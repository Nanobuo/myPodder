import { artworkOf, chaptersDb, episodesDb, listeningDb, queueDb, type Chapter, type EpisodeItem } from "./db.ts";
import { ensureChapters } from "./chapterLoader.ts";
import { localSource } from "./downloads.svelte.ts";
import { epochDay } from "./format.ts";
import * as repo from "./repo.ts";
import { settings, updateSettings } from "./settings.svelte.ts";
import { changed, snackbar } from "./store.svelte.ts";
import { sync } from "./sync.svelte.ts";
import { t } from "./i18n.ts";

const FADE_MS = 10_000;

export type SleepState = { kind: "off" } | { kind: "timer"; endsAt: number } | { kind: "endOfEpisode" };

/**
 * Riproduzione con un elemento <audio>: episodio corrente, avanzamento salvato, statistiche di
 * ascolto, coda, timer di spegnimento e integrazione con i tasti multimediali del sistema.
 */
class Player {
  readonly audio = new Audio();

  current: EpisodeItem | null = $state(null);
  isPlaying = $state(false);
  isBuffering = $state(false);
  position = $state(0);
  duration = $state(0);
  speed = $state(1);
  volume = $state(1);
  chapters: Chapter[] = $state([]);
  sleep: SleepState = $state({ kind: "off" });
  error = $state(false);

  chapterIndex = $derived(this.chapters.findLastIndex((c) => c.startMs <= this.position));
  currentChapter = $derived(this.chapterIndex >= 0 ? this.chapters[this.chapterIndex] : null);
  /** Immagine del capitolo corrente, se presente, altrimenti la copertina dell'episodio. */
  artwork = $derived(this.currentChapter?.imageUrl ?? (this.current ? artworkOf(this.current) : null));

  private pendingSeek: number | null = null;
  private tick: ReturnType<typeof setInterval> | null = null;
  private sleepTimer: ReturnType<typeof setInterval> | null = null;
  private lastTickWall = 0;
  private lastTickPos = 0;
  private listenedMs = 0;
  private contentMs = 0;
  private lastFlush = 0;
  private loadSeq = 0;
  /**
   * true se la posizione è cambiata per un ascolto o un salto fatto qui: solo allora va salvata.
   * Salvare una posizione non toccata la renderebbe "più recente" di quella arrivata da un altro
   * dispositivo e la sincronizzazione la riporterebbe indietro.
   */
  private positionDirty = false;

  constructor() {
    const a = this.audio;
    a.preload = "auto";
    a.addEventListener("loadedmetadata", () => {
      if (this.pendingSeek != null) {
        a.currentTime = this.pendingSeek / 1000;
        this.pendingSeek = null;
      }
      this.updateDuration();
    });
    a.addEventListener("durationchange", () => this.updateDuration());
    a.addEventListener("timeupdate", () => {
      if (this.pendingSeek == null) this.position = a.currentTime * 1000;
      // WebKit non sempre segnala la fine del caricamento dopo un salto.
      if (this.isBuffering && a.readyState >= 3) this.isBuffering = false;
      this.updatePositionState();
    });
    a.addEventListener("play", () => this.onPlayingChanged(true));
    a.addEventListener("pause", () => this.onPlayingChanged(false));
    a.addEventListener("waiting", () => (this.isBuffering = a.readyState < 3));
    a.addEventListener("seeked", () => (this.isBuffering = a.readyState < 3));
    a.addEventListener("playing", () => {
      this.isBuffering = false;
      this.error = false;
    });
    a.addEventListener("canplay", () => (this.isBuffering = false));
    a.addEventListener("ended", () => void this.onEnded());
    a.addEventListener("error", () => {
      if (!a.src || !this.current) return;
      this.error = true;
      this.isBuffering = false;
      snackbar.show(t("feed_error"));
    });
    a.addEventListener("ratechange", () => (this.speed = a.playbackRate));
    a.addEventListener("volumechange", () => {
      if (this.sleep.kind !== "timer") this.volume = a.volume;
    });
    this.setupMediaSession();
  }

  get hasMedia() {
    return this.current != null;
  }

  private updateDuration() {
    const d = this.audio.duration;
    if (Number.isFinite(d) && d > 0) {
      this.duration = d * 1000;
      const id = this.current?.id;
      if (id != null && Math.abs((this.current?.durationMs ?? 0) - this.duration) > 1000) {
        void episodesDb.setDuration(id, this.duration);
      }
    }
  }

  /** Impostazioni iniziali e ripristino dell'ultimo episodio (senza avviarlo). */
  async init() {
    this.audio.playbackRate = settings.speed;
    this.audio.defaultPlaybackRate = settings.speed;
    this.audio.volume = settings.volume;
    this.speed = settings.speed;
    this.volume = settings.volume;
    const last = settings.lastEpisodeId != null ? await episodesDb.item(settings.lastEpisodeId) : null;
    const id = last && !last.completed ? last.id : await repo.nextInQueue();
    if (id != null) await this.load(id, { autoplay: false });
  }

  private resumePosition(e: EpisodeItem): number {
    if (e.completed) return 0;
    if (e.durationMs > 0 && e.positionMs > e.durationMs - 5000) return 0;
    return e.positionMs;
  }

  private async load(id: number, opts: { startMs?: number; autoplay: boolean }) {
    const seq = ++this.loadSeq;
    const item = await episodesDb.item(id);
    if (!item || seq !== this.loadSeq) return;
    await this.flush();
    this.positionDirty = false;
    this.current = item;
    this.error = false;
    this.position = opts.startMs ?? this.resumePosition(item);
    this.duration = item.durationMs;
    this.pendingSeek = this.position;
    this.chapters = await chaptersDb.get(id);
    const src = (await localSource(item)) ?? item.audioUrl;
    if (seq !== this.loadSeq) return;
    this.audio.src = src;
    this.audio.playbackRate = settings.speed;
    this.audio.load();
    updateSettings({ lastEpisodeId: id });
    this.updateMetadata();
    if (opts.autoplay) await this.audio.play().catch(() => undefined);
    void ensureChapters(id).then(async () => {
      if (this.current?.id === id) this.chapters = await chaptersDb.get(id);
    });
  }

  /**
   * L'episodio esce dalla coda quando parte davvero, non quando viene solo caricato (es.
   * all'avvio): altrimenti la coda di qui risulterebbe "modificata" e sovrascriverebbe quella
   * sincronizzata dagli altri dispositivi.
   */
  private async leaveQueue(id: number) {
    if ((await queueDb.ids()).includes(id)) await repo.removeFromQueue(id);
  }

  /** Riproduce un episodio, opzionalmente da una posizione precisa (es. un capitolo). */
  async play(id: number, startMs?: number) {
    if (this.current?.id === id && !this.audio.ended) {
      if (startMs != null) this.seekTo(startMs);
      await this.audio.play().catch(() => undefined);
      return;
    }
    await this.load(id, { startMs, autoplay: true });
  }

  togglePlayPause() {
    if (!this.current) return;
    if (this.audio.paused) {
      if (this.audio.ended) this.audio.currentTime = 0;
      void this.audio.play().catch(() => undefined);
    } else {
      this.audio.pause();
    }
  }

  pause() {
    this.audio.pause();
  }

  seekTo(ms: number) {
    if (this.current) this.positionDirty = true;
    this.moveTo(ms);
  }

  /** Sposta il player senza considerarlo una modifica dell'utente (allineamento con il database). */
  private moveTo(ms: number) {
    const target = Math.max(0, this.duration > 0 ? Math.min(ms, this.duration - 500) : ms);
    this.position = target;
    if (this.audio.readyState >= 1) this.audio.currentTime = target / 1000;
    else this.pendingSeek = target;
    this.lastTickPos = target;
  }

  skipBack() {
    this.seekTo(this.position - settings.skipBackSec * 1000);
  }

  skipForward() {
    this.seekTo(this.position + settings.skipForwardSec * 1000);
  }

  setSpeed(v: number) {
    this.audio.playbackRate = v;
    this.audio.defaultPlaybackRate = v;
    updateSettings({ speed: v });
  }

  setVolume(v: number) {
    this.audio.volume = Math.min(1, Math.max(0, v));
    updateSettings({ volume: this.audio.volume });
  }

  /** Ferma e svuota il player (es. episodio eliminato). */
  async stop() {
    await this.flush();
    this.audio.pause();
    this.audio.removeAttribute("src");
    this.audio.load();
    this.current = null;
    this.chapters = [];
    this.position = 0;
    this.duration = 0;
    if ("mediaSession" in navigator) navigator.mediaSession.metadata = null;
  }

  // --- Avanzamento: statistiche e posizione ---

  private onPlayingChanged(playing: boolean) {
    this.isPlaying = playing;
    updateSettings({ lastPlayerActivity: Date.now() });
    if ("mediaSession" in navigator) navigator.mediaSession.playbackState = playing ? "playing" : "paused";
    if (playing) {
      this.positionDirty = true;
      if (this.current) void this.leaveQueue(this.current.id);
      this.lastTickWall = performance.now();
      this.lastTickPos = this.audio.currentTime * 1000;
      this.tick ??= setInterval(() => {
        this.accumulate();
        if (performance.now() - this.lastFlush > 15_000) void this.flush();
      }, 1000);
    } else {
      if (this.tick) clearInterval(this.tick);
      this.tick = null;
      this.accumulate();
      void this.flush().then(() => {
        // In pausa: la posizione va subito sugli altri dispositivi.
        if (!this.audio.ended) sync.requestSync(1000);
      });
    }
  }

  private accumulate() {
    const now = performance.now();
    const pos = this.audio.currentTime * 1000;
    const wall = now - this.lastTickWall;
    const delta = pos - this.lastTickPos;
    if (this.current && this.lastTickWall > 0 && wall > 0 && wall <= 5000) {
      this.listenedMs += wall;
      // Esclude i salti (seek): conta solo l'avanzamento compatibile con la velocità.
      if (delta >= 0 && delta <= wall * 4 + 500) this.contentMs += delta;
    }
    this.lastTickWall = now;
    this.lastTickPos = pos;
  }

  private async flush(savePosition = true) {
    this.lastFlush = performance.now();
    const item = this.current;
    if (!item) return;
    if (this.listenedMs > 0) {
      const listened = this.listenedMs;
      const content = this.contentMs;
      this.listenedMs = 0;
      this.contentMs = 0;
      await listeningDb.add(epochDay(), item.id, item.podcastId, listened, content);
      changed("listening");
    }
    if (savePosition && this.positionDirty && !this.audio.ended && this.audio.readyState >= 1 && this.pendingSeek == null) {
      await repo.savePosition(item.id, this.audio.currentTime * 1000);
      // Finché si ascolta la posizione continua a cambiare; in pausa resta salvata così.
      this.positionDirty = this.isPlaying;
    }
  }

  private async onEnded() {
    const item = this.current;
    if (!item) return;
    this.accumulate();
    await this.flush(false);
    await repo.setCompleted(item.id, true, true);
    sync.requestSync();
    const stopHere = this.sleep.kind === "endOfEpisode";
    if (stopHere) this.cancelSleep();
    const next = await repo.nextInQueue();
    if (next == null) {
      // Coda finita: niente più da riprodurre.
      await this.stop();
      return;
    }
    // Con il timer "fine episodio" il prossimo resta pronto, ma non parte.
    await this.load(next, { autoplay: !stopHere });
  }

  /**
   * Allinea il player fermo al database dopo una sincronizzazione: carica l'ultimo episodio
   * ascoltato (anche su un altro dispositivo) e si sposta alla posizione salvata, così Play
   * riprende da dove si era arrivati. Durante l'ascolto non fa nulla.
   */
  async reconcile() {
    if (this.isPlaying || this.positionDirty) return;
    const latest = await episodesDb.lastPlayed();
    const current = this.current ? await episodesDb.item(this.current.id) : null;
    if (this.isPlaying || this.positionDirty) return;
    // Si segue un altro dispositivo solo se ci ha ascoltato dopo l'ultima attività di questo
    // computer: altrimenti vale il player di qui, con la sua coda.
    const since = Math.max(current?.lastPlayedAt ?? 0, settings.lastPlayerActivity);
    if (latest && latest.id !== current?.id && latest.lastPlayedAt > since) {
      await this.load(latest.id, { autoplay: false });
      return;
    }
    if (!current) return;
    this.current = current;
    if (!current.completed && Math.abs(current.positionMs - this.position) > 2000) this.moveTo(current.positionMs);
  }

  /** Aggiorna i dati dell'episodio corrente dopo una modifica (es. segnato come ascoltato). */
  async refreshCurrent() {
    if (!this.current) return;
    const item = await episodesDb.item(this.current.id);
    if (item) this.current = item;
    else await this.stop();
  }

  // --- Timer di spegnimento ---

  startSleep(minutes: number) {
    this.setSleep({ kind: "timer", endsAt: Date.now() + minutes * 60_000 });
    updateSettings({ sleepMinutes: minutes });
  }

  sleepAtEndOfEpisode() {
    this.setSleep({ kind: "endOfEpisode" });
  }

  extendSleep(minutes = 5) {
    const base = this.sleep.kind === "timer" ? Math.max(this.sleep.endsAt, Date.now()) : Date.now();
    this.setSleep({ kind: "timer", endsAt: base + minutes * 60_000 });
  }

  cancelSleep() {
    this.setSleep({ kind: "off" });
  }

  private setSleep(state: SleepState) {
    this.sleep = state;
    this.audio.volume = settings.volume;
    if (this.sleepTimer) clearInterval(this.sleepTimer);
    this.sleepTimer = null;
    if (state.kind === "timer") {
      this.sleepTimer = setInterval(() => {
        if (this.sleep.kind !== "timer") return;
        const left = this.sleep.endsAt - Date.now();
        if (left <= 0) {
          this.pause();
          this.audio.volume = settings.volume;
          this.cancelSleep();
        } else if (left <= FADE_MS && this.isPlaying) {
          // Dissolvenza negli ultimi secondi, come sull'app Android.
          this.audio.volume = settings.volume * (left / FADE_MS);
        }
      }, 250);
    }
  }

  // --- Tasti multimediali e controlli del sistema ---

  private setupMediaSession() {
    if (!("mediaSession" in navigator)) return;
    const ms = navigator.mediaSession;
    const handlers: [MediaSessionAction, MediaSessionActionHandler][] = [
      ["play", () => void this.audio.play()],
      ["pause", () => this.pause()],
      ["seekbackward", () => this.skipBack()],
      ["seekforward", () => this.skipForward()],
      ["previoustrack", () => this.skipBack()],
      ["nexttrack", () => this.skipForward()],
      ["seekto", (d) => d.seekTime != null && this.seekTo(d.seekTime * 1000)],
      ["stop", () => this.pause()],
    ];
    for (const [action, handler] of handlers) {
      try {
        ms.setActionHandler(action, handler);
      } catch {
        /* azione non supportata da questo sistema */
      }
    }
  }

  private updateMetadata() {
    if (!("mediaSession" in navigator) || !this.current) return;
    const art = this.current.imageUrl ?? this.current.podcastImage;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: this.current.title,
      artist: this.current.podcastTitle,
      album: this.current.podcastTitle,
      artwork: art ? [{ src: art, sizes: "512x512" }] : [],
    });
  }

  private lastPositionState = 0;
  private updatePositionState() {
    if (!("mediaSession" in navigator) || !this.duration) return;
    const now = performance.now();
    if (now - this.lastPositionState < 5000) return;
    this.lastPositionState = now;
    try {
      navigator.mediaSession.setPositionState({
        duration: this.duration / 1000,
        position: Math.min(this.position, this.duration) / 1000,
        playbackRate: this.speed,
      });
    } catch {
      /* valori non validi durante il caricamento */
    }
  }

  /** Salva tutto prima della chiusura dell'app. */
  async shutdown() {
    this.accumulate();
    await this.flush();
  }
}

export const player = new Player();
