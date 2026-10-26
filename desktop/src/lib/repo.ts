import { chaptersDb, episodesDb, kv, listeningDb, podcastsDb, queueDb, type NewEpisode, type Podcast } from "./db.ts";
import { epochDay } from "./format.ts";
import { fetchFeed, type ParsedEpisode, type ParsedFeed } from "./feed.ts";
import { settings, updateSettings } from "./settings.svelte.ts";
import { changed } from "./store.svelte.ts";
import * as downloads from "./downloads.svelte.ts";

// Operazioni su podcast, episodi e coda: l'equivalente dei repository dell'app Android.

/** Esegue [tasks] con al massimo [limit] in parallelo. */
export async function parallel<T, R>(items: T[], limit: number, task: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = new Array(items.length);
  let next = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const i = next++;
      results[i] = await task(items[i]);
    }
  });
  await Promise.all(workers);
  return results;
}

function toNewEpisodes(list: ParsedEpisode[], markNew: boolean): NewEpisode[] {
  return [...list]
    .sort((a, b) => b.pubDate - a.pubDate)
    .map((e) => ({
      guid: e.guid,
      title: e.title,
      description: e.description,
      audioUrl: e.audioUrl,
      mimeType: e.mimeType,
      imageUrl: e.imageUrl,
      link: e.link,
      pubDate: e.pubDate,
      durationMs: e.durationMs,
      sizeBytes: e.sizeBytes,
      chaptersUrl: e.chaptersUrl,
      // I capitoli Podlove inline sono già disponibili: non serve cercarli altrove.
      chaptersLoaded: e.inlineChapters.length > 0 && !e.chaptersUrl,
      isNew: markNew,
    }));
}

/** URL del feed per id del podcast, per ritrovare gli episodi appena inseriti. */
const feedUrlOf = new Map<number, string>();

async function insertEpisodes(podcastId: number, list: ParsedEpisode[], markNew: boolean): Promise<number[]> {
  const ids = await episodesDb.insertAll(podcastId, toNewEpisodes(list, markNew));
  // Capitoli Podlove inline: salvati subito, solo per gli episodi appena inseriti che li hanno.
  if (ids.length) {
    const fresh = new Set(ids);
    for (const parsed of list) {
      if (!parsed.inlineChapters.length) continue;
      const id = await episodesDb.idFor(feedUrlOf.get(podcastId) ?? "", parsed.guid);
      if (id != null && fresh.has(id)) {
        await chaptersDb.replace(id, parsed.inlineChapters.map((c) => ({ episodeId: id, ...c })));
      }
    }
  }
  return ids;
}

const categoriesOf = (feed: ParsedFeed) => feed.categories.join("|") || null;

/** Si iscrive al feed (se non già presente) e restituisce l'id del podcast. */
export async function subscribe(feedUrl: string, preloaded?: ParsedFeed): Promise<number> {
  const existing = await podcastsDb.byUrl(feedUrl);
  if (existing) return existing.id;
  const feed = preloaded ?? (await fetchFeed(feedUrl));
  const id = await podcastsDb.insert({
    feedUrl,
    title: feed.title,
    author: feed.author,
    description: feed.description,
    imageUrl: feed.imageUrl,
    link: feed.link,
    categories: categoriesOf(feed),
    autoDownload: false,
    subscribedAt: Date.now(),
    lastRefreshed: Date.now(),
  });
  feedUrlOf.set(id, feedUrl);
  await insertEpisodes(id, feed.episodes, false);
  changed("podcasts", "episodes");
  return id;
}

export async function unsubscribe(podcastId: number) {
  await downloads.deleteForPodcast(podcastId);
  await podcastsDb.delete(podcastId);
  changed("podcasts", "episodes", "queue", "downloads");
}

export async function setAutoDownload(podcastId: number, enabled: boolean) {
  await podcastsDb.setAutoDownload(podcastId, enabled);
  changed("podcasts");
}

/** Aggiorna un podcast e restituisce il numero di nuovi episodi. */
export async function refresh(podcastId: number): Promise<number> {
  const podcast = await podcastsDb.get(podcastId);
  if (!podcast) return 0;
  const feed = await fetchFeed(podcast.feedUrl);
  feedUrlOf.set(podcastId, podcast.feedUrl);
  const known = await episodesDb.guids(podcastId);
  const existing = feed.episodes.filter((e) => known.has(e.guid));
  const fresh = feed.episodes.filter((e) => !known.has(e.guid));
  const updated: Podcast = {
    ...podcast,
    title: feed.title,
    author: feed.author ?? podcast.author,
    description: feed.description ?? podcast.description,
    imageUrl: feed.imageUrl ?? podcast.imageUrl,
    link: feed.link ?? podcast.link,
    categories: categoriesOf(feed) ?? podcast.categories,
    lastRefreshed: Date.now(),
  };
  await podcastsDb.update(updated);
  await episodesDb.updateMetadata(podcastId, existing);
  const newIds = await insertEpisodes(podcastId, fresh, true);
  changed("podcasts", "episodes");
  if (newIds.length === 0) return 0;

  if (settings.addNewToQueue) await queueDb.addLast([...newIds].reverse()); // dal più vecchio al più recente
  if (podcast.autoDownload && settings.autoDownloadCount > 0) {
    for (const id of newIds.slice(0, settings.autoDownloadCount)) await downloads.enqueue(id);
  }
  changed("queue");
  return newIds.length;
}

let refreshing: Promise<number> | null = null;

/** Aggiorna tutti i podcast (max 4 in parallelo); restituisce i nuovi episodi trovati. */
export function refreshAll(): Promise<number> {
  refreshing ??= (async () => {
    try {
      const all = await podcastsDb.all();
      const found = await parallel(all, 4, (p) => refresh(p.id).catch(() => 0));
      updateSettings({ lastFullRefresh: Date.now() });
      return found.reduce((a, b) => a + b, 0);
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

export const isRefreshing = () => refreshing != null;

/** Legge dal feed le categorie dei podcast che non le hanno ancora. */
export async function fillMissingCategories() {
  const missing = (await podcastsDb.all()).filter((p) => p.categories == null);
  await parallel(missing, 4, async (p) => {
    try {
      // "" segna i feed senza categorie, per non rileggerli ogni volta.
      await podcastsDb.setCategories(p.id, categoriesOf(await fetchFeed(p.feedUrl)) ?? "");
    } catch {
      /* feed non raggiungibile: si riprova la prossima volta */
    }
  });
}

// --- Coda ---

/** Le modifiche alla coda fatte qui sono dell'utente: la versione locale diventa la più recente. */
async function queueChange(block: () => Promise<unknown>) {
  await block();
  await kv.set("sync.queueUpdatedAt", Date.now());
  changed("queue");
}

export const addToQueue = (id: number) => queueChange(() => queueDb.addLast([id]));
export const playNext = (id: number) => queueChange(() => queueDb.addFirst(id));
export const removeFromQueue = (id: number) => queueChange(() => queueDb.remove(id));
export const setQueue = (ids: number[]) => queueChange(() => queueDb.rewrite(ids));
export const clearQueue = () => queueChange(() => queueDb.clear());
export const nextInQueue = () => queueDb.first();

// --- Stato degli episodi ---

export async function savePosition(id: number, positionMs: number) {
  await episodesDb.savePosition(id, positionMs);
  changed("episodes");
}

/** Segna un episodio come ascoltato (o no), con le azioni collegate. */
export async function setCompleted(id: number, completed: boolean, fromPlayback = false) {
  const ep = await episodesDb.item(id);
  if (!ep) return;
  await episodesDb.setCompleted(id, completed, completed ? Date.now() : null);
  if (completed) {
    if (await queueDb.ids().then((ids) => ids.includes(id))) await removeFromQueue(id);
    if (fromPlayback) await listeningDb.markCompleted(epochDay(), id, ep.podcastId);
    if (settings.deleteAfterPlayed && ep.localPath) await downloads.deleteDownload(id);
  }
  changed("episodes", "listening");
}

export async function markAllPlayed(podcastId: number) {
  for (const e of await episodesDb.forPodcast(podcastId)) {
    if (!e.completed) await episodesDb.setCompleted(e.id, true, Date.now());
  }
  await episodesDb.clearNew(podcastId);
  changed("episodes", "podcasts");
}

export async function clearNew(podcastId: number) {
  await episodesDb.clearNew(podcastId);
  changed("podcasts");
}

export async function clearHistory() {
  await episodesDb.clearHistory();
  changed("episodes");
}
