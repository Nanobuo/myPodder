import Database from "@tauri-apps/plugin-sql";

// Database locale, con le stesse tabelle dell'app Android.

export interface Podcast {
  id: number;
  feedUrl: string;
  title: string;
  author: string | null;
  description: string | null;
  imageUrl: string | null;
  link: string | null;
  categories: string | null;
  autoDownload: boolean;
  subscribedAt: number;
  lastRefreshed: number;
}

export interface PodcastWithNew extends Podcast {
  newCount: number;
}

export const DownloadState = { NONE: 0, QUEUED: 1, DOWNLOADING: 2, DONE: 3, FAILED: 4 } as const;

export interface Episode {
  id: number;
  podcastId: number;
  guid: string;
  title: string;
  description: string | null;
  audioUrl: string;
  mimeType: string | null;
  imageUrl: string | null;
  link: string | null;
  pubDate: number;
  durationMs: number;
  sizeBytes: number;
  chaptersUrl: string | null;
  chaptersLoaded: boolean;
  isNew: boolean;
  positionMs: number;
  completed: boolean;
  completedAt: number | null;
  lastPlayedAt: number;
  downloadState: number;
  downloadProgress: number;
  localPath: string | null;
  downloadedBytes: number;
  stateUpdatedAt: number;
}

/** Episodio con i dati del podcast necessari per mostrarlo in una lista. */
export interface EpisodeItem extends Episode {
  podcastTitle: string;
  podcastImage: string | null;
  feedUrl: string;
}

export const artworkOf = (e: EpisodeItem) => e.imageUrl ?? e.podcastImage;

export interface Chapter {
  episodeId: number;
  startMs: number;
  title: string;
  imageUrl: string | null;
  url: string | null;
}

export interface ListeningEntry {
  day: number;
  episodeId: number;
  podcastId: number;
  listenedMs: number;
  contentMs: number;
  completed: boolean;
}

export interface NewEpisode {
  guid: string;
  title: string;
  description: string | null;
  audioUrl: string;
  mimeType: string | null;
  imageUrl: string | null;
  link: string | null;
  pubDate: number;
  durationMs: number;
  sizeBytes: number;
  chaptersUrl: string | null;
  chaptersLoaded: boolean;
  isNew: boolean;
}

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS podcasts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    feedUrl TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    author TEXT, description TEXT, imageUrl TEXT, link TEXT, categories TEXT,
    autoDownload INTEGER NOT NULL DEFAULT 0,
    subscribedAt INTEGER NOT NULL DEFAULT 0,
    lastRefreshed INTEGER NOT NULL DEFAULT 0)`,
  `CREATE TABLE IF NOT EXISTS episodes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    podcastId INTEGER NOT NULL REFERENCES podcasts(id) ON DELETE CASCADE,
    guid TEXT NOT NULL, title TEXT NOT NULL, description TEXT,
    audioUrl TEXT NOT NULL, mimeType TEXT, imageUrl TEXT, link TEXT,
    pubDate INTEGER NOT NULL DEFAULT 0, durationMs INTEGER NOT NULL DEFAULT 0, sizeBytes INTEGER NOT NULL DEFAULT 0,
    chaptersUrl TEXT, chaptersLoaded INTEGER NOT NULL DEFAULT 0,
    isNew INTEGER NOT NULL DEFAULT 0,
    positionMs INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, completedAt INTEGER,
    lastPlayedAt INTEGER NOT NULL DEFAULT 0,
    downloadState INTEGER NOT NULL DEFAULT 0, downloadProgress INTEGER NOT NULL DEFAULT 0,
    localPath TEXT, downloadedBytes INTEGER NOT NULL DEFAULT 0,
    stateUpdatedAt INTEGER NOT NULL DEFAULT 0,
    UNIQUE (podcastId, guid))`,
  `CREATE INDEX IF NOT EXISTS episodes_pubDate ON episodes(pubDate)`,
  `CREATE INDEX IF NOT EXISTS episodes_state ON episodes(stateUpdatedAt)`,
  `CREATE TABLE IF NOT EXISTS queue (
    episodeId INTEGER PRIMARY KEY REFERENCES episodes(id) ON DELETE CASCADE,
    position INTEGER NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS chapters (
    episodeId INTEGER NOT NULL REFERENCES episodes(id) ON DELETE CASCADE,
    startMs INTEGER NOT NULL, title TEXT NOT NULL, imageUrl TEXT, url TEXT,
    PRIMARY KEY (episodeId, startMs))`,
  `CREATE TABLE IF NOT EXISTS listening (
    day INTEGER NOT NULL, episodeId INTEGER NOT NULL, podcastId INTEGER NOT NULL,
    listenedMs INTEGER NOT NULL DEFAULT 0, contentMs INTEGER NOT NULL DEFAULT 0,
    completed INTEGER NOT NULL DEFAULT 0,
    updatedAt INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (day, episodeId))`,
  `CREATE TABLE IF NOT EXISTS remote_listening (
    deviceId TEXT NOT NULL, day INTEGER NOT NULL, feedUrl TEXT NOT NULL, guid TEXT NOT NULL,
    listenedMs INTEGER NOT NULL, contentMs INTEGER NOT NULL, completed INTEGER NOT NULL,
    PRIMARY KEY (deviceId, day, feedUrl, guid))`,
  `CREATE INDEX IF NOT EXISTS listening_podcast ON listening(podcastId)`,
  `CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)`,
];

let dbPromise: Promise<Database> | null = null;

async function open(): Promise<Database> {
  const db = await Database.load("sqlite:mypodder.db");
  await db.execute("PRAGMA foreign_keys = ON");
  await db.execute("PRAGMA journal_mode = WAL");
  // Database creati dalla prima versione: manca la data di modifica delle statistiche.
  const cols = await db.select<{ name: string }[]>("PRAGMA table_info(listening)");
  if (cols.length && !cols.some((c) => c.name === "updatedAt")) {
    await db.execute("ALTER TABLE listening ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 1");
  }
  for (const sql of SCHEMA) await db.execute(sql);
  return db;
}

export function database(): Promise<Database> {
  dbPromise ??= open();
  return dbPromise;
}

type Row = Record<string, unknown>;

// Le operazioni passano in fila una alla volta: il pool di connessioni del plugin non garantisce
// che più istruzioni consecutive usino la stessa connessione.
let chain: Promise<unknown> = Promise.resolve();
function serial<T>(fn: () => Promise<T>): Promise<T> {
  const next = chain.then(fn, fn);
  chain = next.catch(() => undefined);
  return next;
}

export async function select<T = Row>(sql: string, params: unknown[] = []): Promise<T[]> {
  const db = await database();
  return serial(() => db.select<T[]>(sql, params));
}

export async function execute(sql: string, params: unknown[] = []): Promise<{ rowsAffected: number; lastInsertId?: number }> {
  const db = await database();
  return serial(() => db.execute(sql, params));
}

/** Esegue più istruzioni di fila senza che altre si inseriscano in mezzo. */
export async function batch(statements: [string, unknown[]][]): Promise<void> {
  const db = await database();
  await serial(async () => {
    for (const [sql, params] of statements) await db.execute(sql, params);
  });
}

const bool = (v: unknown) => v === 1 || v === true || v === "1";

const toPodcast = (r: Row): Podcast => ({ ...(r as unknown as Podcast), autoDownload: bool(r.autoDownload) });

const toEpisode = <T extends Episode>(r: Row): T => ({
  ...(r as unknown as T),
  chaptersLoaded: bool(r.chaptersLoaded),
  isNew: bool(r.isNew),
  completed: bool(r.completed),
});

const EPISODE_ITEM = `SELECT e.*, p.title AS podcastTitle, p.imageUrl AS podcastImage, p.feedUrl AS feedUrl
  FROM episodes e JOIN podcasts p ON p.id = e.podcastId`;

// --- Podcast ---

export const podcastsDb = {
  async all(): Promise<Podcast[]> {
    return (await select("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")).map(toPodcast);
  },
  async allWithNew(): Promise<PodcastWithNew[]> {
    const rows = await select(
      `SELECT p.*, (SELECT COUNT(*) FROM episodes e WHERE e.podcastId = p.id AND e.isNew = 1) AS newCount
       FROM podcasts p ORDER BY p.title COLLATE NOCASE`,
    );
    return rows.map((r) => ({ ...toPodcast(r), newCount: Number(r.newCount) }));
  },
  async get(id: number): Promise<Podcast | null> {
    return (await select("SELECT * FROM podcasts WHERE id = ?", [id])).map(toPodcast)[0] ?? null;
  },
  async byUrl(url: string): Promise<Podcast | null> {
    return (await select("SELECT * FROM podcasts WHERE feedUrl = ?", [url])).map(toPodcast)[0] ?? null;
  },
  async insert(p: Omit<Podcast, "id">): Promise<number> {
    await execute(
      `INSERT OR IGNORE INTO podcasts (feedUrl, title, author, description, imageUrl, link, categories, autoDownload, subscribedAt, lastRefreshed)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [p.feedUrl, p.title, p.author, p.description, p.imageUrl, p.link, p.categories, p.autoDownload ? 1 : 0, p.subscribedAt, p.lastRefreshed],
    );
    return (await podcastsDb.byUrl(p.feedUrl))!.id;
  },
  async update(p: Podcast) {
    await execute(
      `UPDATE podcasts SET title = ?, author = ?, description = ?, imageUrl = ?, link = ?, categories = ?, lastRefreshed = ? WHERE id = ?`,
      [p.title, p.author, p.description, p.imageUrl, p.link, p.categories, p.lastRefreshed, p.id],
    );
  },
  setCategories: (id: number, categories: string) => execute("UPDATE podcasts SET categories = ? WHERE id = ?", [categories, id]),
  setAutoDownload: (id: number, v: boolean) => execute("UPDATE podcasts SET autoDownload = ? WHERE id = ?", [v ? 1 : 0, id]),
  delete: (id: number) => execute("DELETE FROM podcasts WHERE id = ?", [id]),
};

// --- Episodi ---

export const episodesDb = {
  async forPodcast(podcastId: number): Promise<EpisodeItem[]> {
    return (await select(`${EPISODE_ITEM} WHERE e.podcastId = ? ORDER BY e.pubDate DESC`, [podcastId])).map(toEpisode<EpisodeItem>);
  },
  async latest(limit = 150): Promise<EpisodeItem[]> {
    return (await select(`${EPISODE_ITEM} WHERE e.completed = 0 ORDER BY e.pubDate DESC LIMIT ?`, [limit])).map(toEpisode<EpisodeItem>);
  },
  async downloads(): Promise<EpisodeItem[]> {
    return (await select(`${EPISODE_ITEM} WHERE e.downloadState != 0 ORDER BY e.downloadState = 3, e.pubDate DESC`)).map(toEpisode<EpisodeItem>);
  },
  async history(limit = 300): Promise<EpisodeItem[]> {
    return (await select(`${EPISODE_ITEM} WHERE e.lastPlayedAt > 0 ORDER BY e.lastPlayedAt DESC LIMIT ?`, [limit])).map(toEpisode<EpisodeItem>);
  },
  clearHistory: () => execute("UPDATE episodes SET lastPlayedAt = 0"),
  /** Ultimo episodio non finito ascoltato, qui o su un altro dispositivo. */
  async lastPlayed(): Promise<EpisodeItem | null> {
    return (await select(`${EPISODE_ITEM} WHERE e.completed = 0 AND e.lastPlayedAt > 0 ORDER BY e.lastPlayedAt DESC LIMIT 1`))
      .map(toEpisode<EpisodeItem>)[0] ?? null;
  },
  async item(id: number): Promise<EpisodeItem | null> {
    return (await select(`${EPISODE_ITEM} WHERE e.id = ?`, [id])).map(toEpisode<EpisodeItem>)[0] ?? null;
  },
  async countPublishedSince(since: number): Promise<number> {
    const r = await select<{ n: number }>("SELECT COUNT(*) AS n FROM episodes WHERE pubDate > ? AND completed = 0", [since]);
    return Number(r[0]?.n ?? 0);
  },
  async guids(podcastId: number): Promise<Set<string>> {
    return new Set((await select<{ guid: string }>("SELECT guid FROM episodes WHERE podcastId = ?", [podcastId])).map((r) => r.guid));
  },
  async downloadedForPodcast(podcastId: number): Promise<Episode[]> {
    return (await select("SELECT * FROM episodes WHERE podcastId = ? AND downloadState != 0", [podcastId])).map(toEpisode<Episode>);
  },
  async downloadedAndCompleted(): Promise<Episode[]> {
    return (await select("SELECT * FROM episodes WHERE downloadState = 3 AND completed = 1")).map(toEpisode<Episode>);
  },
  async downloadedBytes(): Promise<number> {
    const r = await select<{ n: number }>("SELECT COALESCE(SUM(downloadedBytes), 0) AS n FROM episodes WHERE downloadState = 3");
    return Number(r[0]?.n ?? 0);
  },
  /** Inserisce gli episodi nuovi e restituisce i loro id, nell'ordine ricevuto. */
  async insertAll(podcastId: number, episodes: NewEpisode[]): Promise<number[]> {
    if (episodes.length === 0) return [];
    const known = await episodesDb.guids(podcastId);
    const fresh = episodes.filter((e) => !known.has(e.guid));
    const cols = 14;
    const chunk = Math.floor(900 / cols);
    const statements: [string, unknown[]][] = [];
    for (let i = 0; i < fresh.length; i += chunk) {
      const part = fresh.slice(i, i + chunk);
      statements.push([
        `INSERT OR IGNORE INTO episodes (podcastId, guid, title, description, audioUrl, mimeType, imageUrl, link, pubDate, durationMs, sizeBytes, chaptersUrl, chaptersLoaded, isNew)
         VALUES ${part.map(() => "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").join(", ")}`,
        part.flatMap((e) => [
          podcastId, e.guid, e.title, e.description, e.audioUrl, e.mimeType, e.imageUrl, e.link,
          e.pubDate, e.durationMs, e.sizeBytes, e.chaptersUrl, e.chaptersLoaded ? 1 : 0, e.isNew ? 1 : 0,
        ]),
      ]);
    }
    await batch(statements);
    if (fresh.length === 0) return [];
    const rows = await select<{ id: number; guid: string }>("SELECT id, guid FROM episodes WHERE podcastId = ?", [podcastId]);
    const byGuid = new Map(rows.map((r) => [r.guid, r.id]));
    return fresh.map((e) => byGuid.get(e.guid)).filter((id): id is number => id != null);
  },
  async updateMetadata(podcastId: number, list: ParsedMeta[]) {
    await batch(
      list.map((e) => [
        `UPDATE episodes SET title = ?, description = ?, audioUrl = ?, imageUrl = ?,
         durationMs = CASE WHEN ? > 0 THEN ? ELSE durationMs END, chaptersUrl = ?
         WHERE podcastId = ? AND guid = ?`,
        [e.title, e.description, e.audioUrl, e.imageUrl, e.durationMs, e.durationMs, e.chaptersUrl, podcastId, e.guid],
      ]),
    );
  },
  savePosition: (id: number, positionMs: number, now = Date.now()) =>
    execute(
      "UPDATE episodes SET positionMs = ?, lastPlayedAt = ?, stateUpdatedAt = MAX(?, stateUpdatedAt + 1), isNew = 0 WHERE id = ?",
      [Math.round(positionMs), now, now, id],
    ),
  setDuration: (id: number, durationMs: number) =>
    execute("UPDATE episodes SET durationMs = ? WHERE id = ? AND durationMs != ?", [Math.round(durationMs), id, Math.round(durationMs)]),
  setCompleted: (id: number, completed: boolean, at: number | null, now = Date.now()) =>
    execute(
      "UPDATE episodes SET completed = ?, completedAt = ?, positionMs = 0, isNew = 0, stateUpdatedAt = MAX(?, stateUpdatedAt + 1) WHERE id = ?",
      [completed ? 1 : 0, at, now, id],
    ),
  clearNew: (podcastId: number) => execute("UPDATE episodes SET isNew = 0 WHERE podcastId = ?", [podcastId]),
  setDownloadState: (id: number, state: number, progress = 0) =>
    execute("UPDATE episodes SET downloadState = ?, downloadProgress = ? WHERE id = ?", [state, progress, id]),
  setDownloaded: (id: number, path: string, bytes: number) =>
    execute("UPDATE episodes SET downloadState = 3, downloadProgress = 100, localPath = ?, downloadedBytes = ? WHERE id = ?", [path, bytes, id]),
  clearDownload: (id: number) =>
    execute("UPDATE episodes SET downloadState = 0, downloadProgress = 0, localPath = NULL, downloadedBytes = 0 WHERE id = ?", [id]),
  setChaptersLoaded: (id: number) => execute("UPDATE episodes SET chaptersLoaded = 1 WHERE id = ?", [id]),

  // Sincronizzazione
  async changedSince(since: number, limit: number) {
    return (await select<SyncRow>(
      `SELECT e.id, p.feedUrl, e.guid, e.positionMs, e.completed, e.lastPlayedAt, e.stateUpdatedAt
       FROM episodes e JOIN podcasts p ON p.id = e.podcastId
       WHERE e.stateUpdatedAt > ? ORDER BY e.stateUpdatedAt LIMIT ?`,
      [since, limit],
    )).map((r) => ({ ...r, completed: bool(r.completed) }));
  },
  async syncState(feedUrl: string, guid: string) {
    const r = (await select<SyncRow>(
      `SELECT e.id, p.feedUrl, e.guid, e.positionMs, e.completed, e.lastPlayedAt, e.stateUpdatedAt
       FROM episodes e JOIN podcasts p ON p.id = e.podcastId WHERE p.feedUrl = ? AND e.guid = ?`,
      [feedUrl, guid],
    ))[0];
    return r ? { ...r, completed: bool(r.completed) } : null;
  },
  applyRemoteState: (id: number, positionMs: number, completed: boolean, lastPlayedAt: number, updatedAt: number) =>
    execute(
      `UPDATE episodes SET positionMs = ?, completed = ?,
       completedAt = CASE WHEN ? THEN COALESCE(completedAt, ?) ELSE NULL END,
       lastPlayedAt = ?, stateUpdatedAt = ?,
       isNew = CASE WHEN ? OR ? > 0 THEN 0 ELSE isNew END
       WHERE id = ?`,
      [positionMs, completed ? 1 : 0, completed ? 1 : 0, updatedAt, lastPlayedAt, updatedAt, completed ? 1 : 0, positionMs, id],
    ),
  async idFor(feedUrl: string, guid: string): Promise<number | null> {
    const r = await select<{ id: number }>(
      "SELECT e.id FROM episodes e JOIN podcasts p ON p.id = e.podcastId WHERE p.feedUrl = ? AND e.guid = ?",
      [feedUrl, guid],
    );
    return r[0]?.id ?? null;
  },
  async queueKeys(): Promise<{ id: number; feedUrl: string; guid: string }[]> {
    return select(
      `SELECT e.id, p.feedUrl, e.guid FROM queue q JOIN episodes e ON e.id = q.episodeId
       JOIN podcasts p ON p.id = e.podcastId ORDER BY q.position`,
    );
  },
};

export interface ParsedMeta {
  guid: string;
  title: string;
  description: string | null;
  audioUrl: string;
  imageUrl: string | null;
  durationMs: number;
  chaptersUrl: string | null;
}

export interface SyncRow {
  id: number;
  feedUrl: string;
  guid: string;
  positionMs: number;
  completed: boolean;
  lastPlayedAt: number;
  stateUpdatedAt: number;
}

// --- Coda ---

export const queueDb = {
  async items(): Promise<EpisodeItem[]> {
    return (await select(
      `SELECT e.*, p.title AS podcastTitle, p.imageUrl AS podcastImage, p.feedUrl AS feedUrl FROM queue q
       JOIN episodes e ON e.id = q.episodeId JOIN podcasts p ON p.id = e.podcastId ORDER BY q.position`,
    )).map(toEpisode<EpisodeItem>);
  },
  async ids(): Promise<number[]> {
    return (await select<{ episodeId: number }>("SELECT episodeId FROM queue ORDER BY position")).map((r) => r.episodeId);
  },
  async first(): Promise<number | null> {
    return (await queueDb.ids())[0] ?? null;
  },
  async rewrite(ids: number[]) {
    const unique = [...new Set(ids)];
    await batch([
      ["DELETE FROM queue", []],
      ...unique.map((id, i): [string, unknown[]] => ["INSERT OR REPLACE INTO queue (episodeId, position) VALUES (?, ?)", [id, i]]),
    ]);
  },
  async addLast(episodeIds: number[]) {
    const current = await queueDb.ids();
    const toAdd = episodeIds.filter((id) => !current.includes(id));
    if (toAdd.length) await queueDb.rewrite([...current, ...toAdd]);
  },
  async addFirst(episodeId: number) {
    await queueDb.rewrite([episodeId, ...(await queueDb.ids()).filter((id) => id !== episodeId)]);
  },
  remove: (episodeId: number) => execute("DELETE FROM queue WHERE episodeId = ?", [episodeId]),
  clear: () => execute("DELETE FROM queue"),
};

// --- Capitoli ---

export const chaptersDb = {
  async get(episodeId: number): Promise<Chapter[]> {
    return select("SELECT * FROM chapters WHERE episodeId = ? ORDER BY startMs", [episodeId]);
  },
  async replace(episodeId: number, chapters: Chapter[]) {
    await batch([
      ["DELETE FROM chapters WHERE episodeId = ?", [episodeId]],
      ...chapters.map((c): [string, unknown[]] => [
        "INSERT OR REPLACE INTO chapters (episodeId, startMs, title, imageUrl, url) VALUES (?, ?, ?, ?, ?)",
        [episodeId, c.startMs, c.title, c.imageUrl, c.url],
      ]),
    ]);
  },
};

// --- Statistiche ---

// Le statistiche sommano gli ascolti di questo computer (listening) e quelli sincronizzati dagli
// altri dispositivi (remote_listening).
const ALL_LISTENING = "(SELECT day, listenedMs, contentMs, completed FROM listening UNION ALL SELECT day, listenedMs, contentMs, completed FROM remote_listening)";

export interface ListeningSyncRow {
  day: number;
  feedUrl: string;
  guid: string;
  listenedMs: number;
  contentMs: number;
  completed: boolean;
  updatedAt: number;
}

export interface RemoteListening {
  deviceId: string;
  day: number;
  feedUrl: string;
  guid: string;
  listenedMs: number;
  contentMs: number;
  completed: boolean;
}

export const listeningDb = {
  async add(day: number, episodeId: number, podcastId: number, listenedMs: number, contentMs: number) {
    await batch([
      ["INSERT OR IGNORE INTO listening (day, episodeId, podcastId, listenedMs, contentMs) VALUES (?, ?, ?, 0, 0)", [day, episodeId, podcastId]],
      [
        "UPDATE listening SET listenedMs = listenedMs + ?, contentMs = contentMs + ?, updatedAt = ? WHERE day = ? AND episodeId = ?",
        [Math.round(listenedMs), Math.round(contentMs), Date.now(), day, episodeId],
      ],
    ]);
  },
  async markCompleted(day: number, episodeId: number, podcastId: number) {
    await batch([
      ["INSERT OR IGNORE INTO listening (day, episodeId, podcastId, listenedMs, contentMs) VALUES (?, ?, ?, 0, 0)", [day, episodeId, podcastId]],
      ["UPDATE listening SET completed = 1, updatedAt = ? WHERE day = ? AND episodeId = ?", [Date.now(), day, episodeId]],
    ]);
  },
  async all(): Promise<ListeningEntry[]> {
    return (await select("SELECT * FROM listening")).map((r) => ({ ...(r as unknown as ListeningEntry), completed: bool(r.completed) }));
  },
  /** Azzera le statistiche: quelle di questo computer e quelle scaricate dagli altri dispositivi. */
  clear: () => batch([["DELETE FROM listening", []], ["DELETE FROM remote_listening", []]]),
  clearRemote: () => execute("DELETE FROM remote_listening"),
  deleteUpTo: (until: number) => execute("DELETE FROM listening WHERE updatedAt <= ?", [until]),
  /** Ripristina dopo "Annulla": le righe tornano "modificate" per essere ricaricate sul server. */
  async restore(entries: ListeningEntry[]) {
    const now = Date.now();
    await batch(entries.map((e) => [
      "INSERT OR REPLACE INTO listening (day, episodeId, podcastId, listenedMs, contentMs, completed, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
      [e.day, e.episodeId, e.podcastId, e.listenedMs, e.contentMs, e.completed ? 1 : 0, now],
    ]));
  },
  async changedSince(since: number, limit: number): Promise<ListeningSyncRow[]> {
    return (await select<ListeningSyncRow>(
      `SELECT l.day, p.feedUrl, e.guid, l.listenedMs, l.contentMs, l.completed, l.updatedAt
       FROM listening l JOIN episodes e ON e.id = l.episodeId JOIN podcasts p ON p.id = e.podcastId
       WHERE l.updatedAt > ? ORDER BY l.updatedAt LIMIT ?`,
      [since, limit],
    )).map((r) => ({ ...r, completed: bool(r.completed) }));
  },
  async upsertRemote(rows: RemoteListening[]) {
    await batch(rows.map((r) => [
      `INSERT OR REPLACE INTO remote_listening (deviceId, day, feedUrl, guid, listenedMs, contentMs, completed)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [r.deviceId, r.day, r.feedUrl, r.guid, r.listenedMs, r.contentMs, r.completed ? 1 : 0],
    ]));
  },
  async totals(fromDay: number) {
    const r = (await select<{ listenedMs: number; contentMs: number; completed: number }>(
      `SELECT COALESCE(SUM(listenedMs), 0) AS listenedMs, COALESCE(SUM(contentMs), 0) AS contentMs,
       COALESCE(SUM(completed), 0) AS completed FROM ${ALL_LISTENING} WHERE day >= ?`,
      [fromDay],
    ))[0];
    return { listenedMs: Number(r?.listenedMs ?? 0), contentMs: Number(r?.contentMs ?? 0), completed: Number(r?.completed ?? 0) };
  },
  days: (fromDay: number) =>
    select<{ day: number; listenedMs: number }>(
      `SELECT day, SUM(listenedMs) AS listenedMs FROM ${ALL_LISTENING} WHERE day >= ? GROUP BY day ORDER BY day`,
      [fromDay],
    ),
  async activeDays(minMs: number): Promise<number[]> {
    return (await select<{ day: number }>(
      `SELECT day FROM ${ALL_LISTENING} GROUP BY day HAVING SUM(listenedMs) >= ? ORDER BY day DESC`,
      [minMs],
    )).map((r) => r.day);
  },
  topPodcasts: (fromDay: number, limit: number) =>
    select<{ podcastId: number; title: string; imageUrl: string | null; listenedMs: number }>(
      `SELECT t.podcastId AS podcastId, p.title AS title, p.imageUrl AS imageUrl, SUM(t.listenedMs) AS listenedMs
       FROM (SELECT podcastId, day, listenedMs FROM listening
             UNION ALL SELECT p2.id AS podcastId, r.day, r.listenedMs FROM remote_listening r
             JOIN podcasts p2 ON p2.feedUrl = r.feedUrl) t
       JOIN podcasts p ON p.id = t.podcastId WHERE t.day >= ?
       GROUP BY t.podcastId ORDER BY listenedMs DESC LIMIT ?`,
      [fromDay, limit],
    ),
};

// --- Chiave/valore (impostazioni, stato della sincronizzazione, cache) ---

export const kv = {
  async get<T>(key: string): Promise<T | null> {
    const r = await select<{ value: string }>("SELECT value FROM kv WHERE key = ?", [key]);
    if (!r[0]) return null;
    try {
      return JSON.parse(r[0].value) as T;
    } catch {
      return null;
    }
  },
  set: (key: string, value: unknown) =>
    execute("INSERT OR REPLACE INTO kv (key, value) VALUES (?, ?)", [key, JSON.stringify(value)]),
  remove: (key: string) => execute("DELETE FROM kv WHERE key = ?", [key]),
  async removePrefix(prefix: string) {
    await execute("DELETE FROM kv WHERE key LIKE ?", [prefix + "%"]);
  },
};
