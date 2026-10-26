import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { episodesDb, kv, podcastsDb, queueDb } from "./db.ts";
import { httpFetch, isTauri } from "./net.ts";
import * as repo from "./repo.ts";
import { changed } from "./store.svelte.ts";
import { player } from "./player.svelte.ts";

// Sincronizzazione con Supabase, identica a quella dell'app Android: stesse tabelle e stesse
// regole (fusione a tre vie delle iscrizioni, vince la modifica più recente per episodi e coda).

const SUPABASE_URL: string = import.meta.env.VITE_SUPABASE_URL || "https://voepashkpflqclttkrfn.supabase.co";
// Chiave pubblicabile: pensata per stare nelle app, i dati sono protetti dalla Row Level Security.
const SUPABASE_KEY: string = import.meta.env.VITE_SUPABASE_KEY || "sb_publishable_8bhwRGoOycMjGFSvt_D9IA_ZM9ImuDk";

export interface SyncSession {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  userId: string;
  email: string;
}

export type SyncStatus = { kind: "idle" } | { kind: "running" } | { kind: "error"; message: string };

export type LoginState =
  | { kind: "idle" }
  | { kind: "sending" }
  | { kind: "sent"; email: string }
  | { kind: "verifying"; email: string }
  /** [email] presente se l'errore è nella verifica del codice (si resta su quel passo). */
  | { kind: "error"; message: string; email?: string };

interface RemoteEpisodeState {
  user_id?: string;
  feed_url: string;
  guid: string;
  position_ms: number;
  completed: boolean;
  last_played_at: number;
  updated_at: number;
  synced_at?: number;
}

class SupabaseError extends Error {
  constructor(public code: number, message: string) {
    super(message);
  }
}

const PAGE = 500;
/** Ripassa l'ultimo minuto: salvataggi concorrenti sul server possono arrivare fuori ordine. */
const OVERLAP_MS = 60_000;
const HOLD_BACK_MS = 2 * 24 * 60 * 60 * 1000;

const K = {
  session: "sync.session",
  lastSyncAt: "sync.lastSyncAt",
  pushedUntil: "sync.pushedUntil",
  pulledUntil: "sync.pulledUntil",
  queueUpdatedAt: "sync.queueUpdatedAt",
  syncedFeeds: "sync.syncedFeeds",
};

async function num(key: string) {
  return (await kv.get<number>(key)) ?? 0;
}

class SyncManager {
  session: SyncSession | null = $state(null);
  lastSyncAt = $state(0);
  status: SyncStatus = $state({ kind: "idle" });
  login: LoginState = $state({ kind: "idle" });

  private running: Promise<boolean> | null = null;
  private pending: ReturnType<typeof setTimeout> | null = null;

  async init() {
    this.session = await kv.get<SyncSession>(K.session);
    this.lastSyncAt = await num(K.lastSyncAt);
    if (isTauri) {
      await listen<string>("login-callback", (e) => void this.handleLoginCallback(e.payload));
    }
  }

  /** Sincronizza a breve: le richieste ravvicinate diventano una sola. */
  requestSync(delayMs = 3000) {
    if (!this.session) return;
    if (this.pending) clearTimeout(this.pending);
    this.pending = setTimeout(() => {
      this.pending = null;
      void this.syncNow();
    }, delayMs);
  }

  async syncNow(): Promise<boolean> {
    if (this.running) {
      // Una sincronizzazione è già in corso: ne parte un'altra subito dopo.
      await this.running;
    }
    if (!this.session) return false;
    this.running = this.doSync().finally(() => (this.running = null));
    return this.running;
  }

  private async doSync(): Promise<boolean> {
    this.status = { kind: "running" };
    try {
      await this.authorized(async (token) => {
        const newFeeds = await this.syncSubscriptions(token);
        await this.pushStates(token);
        await this.pullStates(token, newFeeds);
        await this.syncQueue(token);
      });
      this.lastSyncAt = Date.now();
      await kv.set(K.lastSyncAt, this.lastSyncAt);
      this.status = { kind: "idle" };
      return true;
    } catch (e) {
      this.status = { kind: "error", message: e instanceof Error ? e.message : String(e) };
      return false;
    }
  }

  // --- HTTP ---

  private async call(path: string, init: RequestInit & { token?: string } = {}): Promise<string> {
    const headers = new Headers(init.headers);
    headers.set("apikey", SUPABASE_KEY);
    headers.set("Cache-Control", "no-store");
    if (init.body) headers.set("Content-Type", "application/json");
    if (init.token) headers.set("Authorization", `Bearer ${init.token}`);
    const r = await httpFetch(`${SUPABASE_URL}${path}`, { ...init, headers });
    const body = await r.text();
    if (!r.ok) {
      let message = `HTTP ${r.status}`;
      try {
        const obj = JSON.parse(body) as Record<string, unknown>;
        message = String(obj.msg ?? obj.message ?? obj.error_description ?? obj.error ?? message);
      } catch {
        /* risposta non JSON */
      }
      throw new SupabaseError(r.status, message);
    }
    return body;
  }

  private rest<T>(path: string, token: string): Promise<T> {
    return this.call(`/rest/v1/${path}`, { token }).then((b) => JSON.parse(b) as T);
  }

  private async upsert(token: string, table: string, conflict: string, rows: unknown[]) {
    if (!rows.length) return;
    await this.call(`/rest/v1/${table}?on_conflict=${conflict}`, {
      method: "POST",
      token,
      headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify(rows),
    });
  }

  // --- Accesso ---

  /** Invia l'email con il link (e, se il modello lo prevede, il codice) di accesso. */
  async sendLink(email: string) {
    const address = email.trim();
    this.login = { kind: "sending" };
    try {
      // Il link dell'email torna a un piccolo server locale aperto dall'app.
      const redirect = isTauri ? await invoke<string>("start_login_server") : location.origin;
      await this.call(`/auth/v1/otp?redirect_to=${encodeURIComponent(redirect + "/")}`, {
        method: "POST",
        body: JSON.stringify({ email: address, create_user: true }),
      });
      this.login = { kind: "sent", email: address };
    } catch (e) {
      this.login = { kind: "error", message: e instanceof Error ? e.message : String(e) };
    }
  }

  async verifyCode(email: string, code: string) {
    this.login = { kind: "verifying", email };
    try {
      const body = await this.call("/auth/v1/verify", {
        method: "POST",
        body: JSON.stringify({ type: "email", email, token: code.replace(/\W/g, "") }),
      });
      await this.signedIn(this.sessionFrom(body, email));
    } catch (e) {
      this.login = { kind: "error", message: e instanceof Error ? e.message : String(e), email };
    }
  }

  private async handleLoginCallback(query: string) {
    const params = new URLSearchParams(query);
    const access = params.get("access_token");
    const refresh = params.get("refresh_token");
    if (!access || !refresh) {
      this.login = { kind: "error", message: params.get("error_description") ?? "Link non valido" };
      return;
    }
    const email = this.login.kind === "sent" ? this.login.email : "";
    this.login = { kind: "verifying", email };
    try {
      const user = JSON.parse(await this.call("/auth/v1/user", { token: access })) as { id: string; email?: string };
      const expiresIn = Number(params.get("expires_in") ?? 3600);
      await this.signedIn({
        accessToken: access,
        refreshToken: refresh,
        expiresAt: Date.now() + expiresIn * 1000,
        userId: user.id,
        email: user.email ?? email,
      });
    } catch (e) {
      this.login = { kind: "error", message: e instanceof Error ? e.message : String(e) };
    }
  }

  resetLogin() {
    this.login = { kind: "idle" };
  }

  private sessionFrom(body: string, email: string): SyncSession {
    const r = JSON.parse(body) as { access_token: string; refresh_token: string; expires_in?: number; user?: { id: string; email?: string } };
    return {
      accessToken: r.access_token,
      refreshToken: r.refresh_token,
      expiresAt: Date.now() + (r.expires_in ?? 3600) * 1000,
      userId: r.user?.id ?? "",
      email: r.user?.email ?? email,
    };
  }

  private async signedIn(session: SyncSession) {
    await this.clearState();
    await this.saveSession(session);
    this.login = { kind: "idle" };
    await this.syncNow();
  }

  private async saveSession(s: SyncSession) {
    this.session = s;
    await kv.set(K.session, s);
  }

  /** Esce dall'account e dimentica cursori e stato: il prossimo accesso riparte da una fusione. */
  private async clearState() {
    for (const key of Object.values(K)) await kv.remove(key);
    this.session = null;
    this.lastSyncAt = 0;
  }

  async signOut() {
    const s = this.session;
    if (s) await this.call("/auth/v1/logout", { method: "POST", token: s.accessToken, body: "{}" }).catch(() => undefined);
    await this.clearState();
    this.status = { kind: "idle" };
  }

  /** Elimina account e dati sul server; quelli su questo computer restano. */
  async deleteAccount(): Promise<boolean> {
    try {
      await this.authorized((token) => this.call("/rest/v1/rpc/delete_account", { method: "POST", token, body: "{}" }));
      await this.clearState();
      this.status = { kind: "idle" };
      return true;
    } catch (e) {
      this.status = { kind: "error", message: e instanceof Error ? e.message : String(e) };
      return false;
    }
  }

  /** Esegue [block] con un token valido, rinnovandolo se è scaduto o se il server lo rifiuta. */
  private async authorized<T>(block: (token: string) => Promise<T>): Promise<T> {
    let s = this.session;
    if (!s) throw new Error("Not signed in");
    if (s.expiresAt - Date.now() < 60_000) s = await this.renew(s);
    try {
      return await block(s.accessToken);
    } catch (e) {
      if (!(e instanceof SupabaseError) || e.code !== 401) throw e;
      return block((await this.renew(s)).accessToken);
    }
  }

  private async renew(s: SyncSession): Promise<SyncSession> {
    try {
      const body = await this.call("/auth/v1/token?grant_type=refresh_token", {
        method: "POST",
        body: JSON.stringify({ refresh_token: s.refreshToken }),
      });
      const fresh = this.sessionFrom(body, s.email);
      if (!fresh.userId) fresh.userId = s.userId;
      await this.saveSession(fresh);
      return fresh;
    } catch (e) {
      // Sessione revocata o account eliminato: serve un nuovo accesso.
      if (e instanceof SupabaseError && e.code >= 400 && e.code <= 403) await this.clearState();
      throw e;
    }
  }

  // --- Iscrizioni ---

  /** Restituisce i feed appena aggiunti qui perché presenti sul server. */
  private async syncSubscriptions(token: string): Promise<Set<string>> {
    const local = new Map((await podcastsDb.all()).map((p) => [p.feedUrl, p]));
    const remote = new Set((await this.rest<{ feed_url: string }[]>("subscriptions?select=feed_url", token)).map((r) => r.feed_url));
    const synced = new Set((await kv.get<string[]>(K.syncedFeeds)) ?? []);
    const l = new Set(local.keys());

    const addRemote = [...l].filter((u) => !synced.has(u) && !remote.has(u));
    const removeRemote = [...synced].filter((u) => !l.has(u) && remote.has(u));
    const addLocal = [...remote].filter((u) => !synced.has(u) && !l.has(u));
    const removeLocal = [...synced].filter((u) => l.has(u) && !remote.has(u));

    const now = Date.now();
    const uid = this.session!.userId;
    await this.upsert(token, "subscriptions", "user_id,feed_url", addRemote.map((url) => {
      const p = local.get(url)!;
      return { user_id: uid, feed_url: url, title: p.title, image_url: p.imageUrl, updated_at: now };
    }));
    for (const url of removeRemote) {
      await this.call(`/rest/v1/subscriptions?feed_url=eq.${encodeURIComponent(url)}`, { method: "DELETE", token });
    }

    for (const url of removeLocal) {
      const p = local.get(url);
      if (p) {
        if (player.current?.podcastId === p.id) await player.stop();
        await repo.unsubscribe(p.id);
      }
    }
    const added = new Set<string>();
    await repo.parallel(addLocal, 4, async (url) => {
      try {
        await repo.subscribe(url);
        added.add(url);
      } catch {
        /* feed non raggiungibile: si riprova alla prossima sincronizzazione */
      }
    });

    const localFinal = new Set([...l].filter((u) => !removeLocal.includes(u)).concat([...added]));
    const remoteFinal = new Set([...remote, ...addRemote].filter((u) => !removeRemote.includes(u)));
    await kv.set(K.syncedFeeds, [...localFinal].filter((u) => remoteFinal.has(u)));
    return added;
  }

  // --- Stato degli episodi ---

  private async pushStates(token: string) {
    const start = Date.now();
    let since = await num(K.pushedUntil);
    const uid = this.session!.userId;
    while (true) {
      const batch = await episodesDb.changedSince(since, PAGE);
      if (!batch.length) break;
      await this.upsert(token, "episode_states", "user_id,feed_url,guid", batch.map((e): RemoteEpisodeState => ({
        user_id: uid,
        feed_url: e.feedUrl,
        guid: e.guid,
        position_ms: e.positionMs,
        completed: e.completed,
        last_played_at: e.lastPlayedAt,
        updated_at: e.stateUpdatedAt,
      })));
      since = batch[batch.length - 1].stateUpdatedAt;
      // Mai oltre l'ora attuale: un orologio avanti su un altro dispositivo non deve far saltare modifiche.
      await kv.set(K.pushedUntil, Math.min(since, start));
      if (batch.length < PAGE) break;
    }
  }

  private async pullStates(token: string, newFeeds: Set<string>) {
    const changedPositions = new Map<number, number>();
    const pulledUntil = await num(K.pulledUntil);
    let since = Math.max(0, pulledUntil - OVERLAP_MS);
    let newest = pulledUntil;
    // Stati recenti di episodi che qui non ci sono ancora (feed non aggiornato): da riprovare.
    let heldBack: number | null = null;
    const recent = Date.now() - HOLD_BACK_MS;
    while (true) {
      const page = await this.rest<RemoteEpisodeState[]>(
        `episode_states?synced_at=gt.${since}&order=synced_at&limit=${PAGE}`,
        token,
      );
      for (const row of page) {
        if (!(await this.apply(row, changedPositions)) && row.updated_at > recent && heldBack == null) heldBack = row.synced_at ?? null;
        newest = Math.max(newest, row.synced_at ?? 0);
      }
      if (page.length < PAGE) break;
      since = page[page.length - 1].synced_at ?? since;
    }
    await kv.set(K.pulledUntil, heldBack != null ? Math.min(heldBack - 1, newest) : newest);
    for (const feed of newFeeds) {
      const rows = await this.rest<RemoteEpisodeState[]>(`episode_states?feed_url=eq.${encodeURIComponent(feed)}`, token);
      for (const row of rows) await this.apply(row, changedPositions);
    }
    if (changedPositions.size) {
      changed("episodes");
      player.onRemotePositions(changedPositions);
      await player.refreshCurrent();
    }
  }

  /** Applica uno stato remoto se più recente; false se l'episodio non esiste qui. */
  private async apply(row: RemoteEpisodeState, out: Map<number, number>): Promise<boolean> {
    const local = await episodesDb.syncState(row.feed_url, row.guid);
    if (!local) return false;
    if (row.updated_at > local.stateUpdatedAt) {
      await episodesDb.applyRemoteState(local.id, row.position_ms, row.completed, row.last_played_at, row.updated_at);
      out.set(local.id, row.completed ? 0 : row.position_ms);
    }
    return true;
  }

  // --- Coda ---

  private async syncQueue(token: string) {
    const remote = (await this.rest<{ items: { f: string; g: string }[]; updated_at: number }[]>("queue_state?select=items,updated_at", token))[0];
    const localAt = await num(K.queueUpdatedAt);
    if (remote && remote.updated_at > localAt) {
      const ids: number[] = [];
      for (const it of remote.items) {
        const id = await episodesDb.idFor(it.f, it.g);
        if (id != null && id !== player.current?.id && !ids.includes(id)) ids.push(id);
      }
      await queueDb.rewrite(ids);
      await kv.set(K.queueUpdatedAt, remote.updated_at);
      changed("queue");
    } else if (!remote || localAt > remote.updated_at) {
      const at = localAt > 0 ? localAt : Date.now();
      const items = (await episodesDb.queueKeys()).map((k) => ({ f: k.feedUrl, g: k.guid }));
      await this.upsert(token, "queue_state", "user_id", [{ user_id: this.session!.userId, items, updated_at: at }]);
      await kv.set(K.queueUpdatedAt, at);
    }
  }
}

export const sync = new SyncManager();
