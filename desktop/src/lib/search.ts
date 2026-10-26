import { getJson, getText } from "./net.ts";

export interface SearchResult {
  title: string;
  author: string | null;
  feedUrl: string;
  imageUrl: string | null;
  description: string | null;
  /** Lingua dichiarata dal feed (es. "it", "en-US"), se nota. */
  language?: string | null;
}

const PI_BASE = "https://api.podcastindex.org/api/1.0";
const PI_KEY: string = import.meta.env.VITE_PODCASTINDEX_KEY ?? "";
const PI_SECRET: string = import.meta.env.VITE_PODCASTINDEX_SECRET ?? "";

/** Senza credenziali Podcast Index la ricerca usa l'iTunes Search API. */
export const usesPodcastIndex = PI_KEY.length > 0 && PI_SECRET.length > 0;

async function sha1Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Richiesta autenticata a Podcast Index. */
async function piGet<T>(path: string, params: Record<string, string>): Promise<T> {
  const now = Math.floor(Date.now() / 1000).toString();
  const url = `${PI_BASE}${path}?${new URLSearchParams(params)}`;
  return getJson<T>(url, {
    headers: { "X-Auth-Key": PI_KEY, "X-Auth-Date": now, Authorization: await sha1Hex(PI_KEY + PI_SECRET + now) },
  });
}

interface PiFeed {
  title?: string; url?: string; author?: string; image?: string; artwork?: string; description?: string; language?: string;
}

async function piFeeds(path: string, params: Record<string, string>): Promise<SearchResult[]> {
  const r = await piGet<{ feeds?: PiFeed[] }>(path, params);
  return (r.feeds ?? [])
    .filter((f) => f.url)
    .map((f) => ({
      title: f.title ?? "",
      author: f.author ?? null,
      feedUrl: f.url!,
      imageUrl: f.artwork || f.image || null,
      description: f.description ?? null,
      language: f.language ?? null,
    }));
}

const languageFirst = (list: SearchResult[], language: string) =>
  [...list].sort(
    (a, b) => Number(!!b.language?.toLowerCase().startsWith(language)) - Number(!!a.language?.toLowerCase().startsWith(language)),
  );

/**
 * Cerca podcast. Podcast Index non filtra per paese, quindi i risultati nella lingua del paese
 * scelto vengono mostrati per primi; con iTunes si usa direttamente il paese.
 */
export async function search(term: string, country: string, language: string): Promise<SearchResult[]> {
  if (!usesPodcastIndex) return itunesSearch(term, country, 40);
  return languageFirst(await piFeeds("/search/byterm", { q: term, max: "40" }), language);
}

/**
 * Podcast che citano [term] senza averlo nel titolo: persone (conduttori, ospiti), titoli e
 * descrizioni degli episodi, autore del feed (Podcast Index).
 */
export async function searchDescriptions(term: string, language: string): Promise<SearchResult[]> {
  if (!usesPodcastIndex) return [];
  const r = await piGet<{ items?: { feedId?: number; feedUrl?: string; feedTitle?: string; feedAuthor?: string; feedImage?: string; feedLanguage?: string }[] }>(
    "/search/byperson",
    { q: term, max: "60" },
  );
  const seen = new Set<string>();
  const list: SearchResult[] = [];
  for (const it of r.items ?? []) {
    if (!it.feedUrl) continue;
    const key = String(it.feedId ?? it.feedUrl);
    if (seen.has(key)) continue;
    seen.add(key);
    list.push({
      title: it.feedTitle ?? "",
      author: it.feedAuthor ?? null,
      feedUrl: it.feedUrl,
      imageUrl: it.feedImage || null,
      description: null,
      language: it.feedLanguage ?? null,
    });
  }
  return languageFirst(list, language);
}

/** Podcast Index usa le categorie di Apple divise: "Society & Culture" diventa "Society,Culture". */
const piCategories = (categories: string[]) =>
  [...new Set(categories.flatMap((c) => c.split("&")).map((c) => c.trim()).filter(Boolean))].join(",");

/** Podcast di tendenza in una lingua, eventualmente limitati ad alcune categorie. */
export async function trending(language: string, categories: string[] = [], max = 30): Promise<SearchResult[]> {
  if (!usesPodcastIndex) return [];
  const params: Record<string, string> = { max: String(max), lang: language };
  if (categories.length) params.cat = piCategories(categories);
  return piFeeds("/podcasts/trending", params);
}

export async function byCategory(category: string, country: string, language: string, max: number): Promise<SearchResult[]> {
  return usesPodcastIndex ? trending(language, [category], max) : itunesSearch(category, country, max);
}

interface ItunesResult {
  collectionId?: number; collectionName?: string; artistName?: string; feedUrl?: string;
  artworkUrl600?: string; artworkUrl100?: string; primaryGenreName?: string;
}

const fromItunes = (r: ItunesResult): SearchResult => ({
  title: r.collectionName ?? "",
  author: r.artistName ?? null,
  feedUrl: r.feedUrl!,
  imageUrl: r.artworkUrl600 ?? r.artworkUrl100 ?? null,
  description: r.primaryGenreName ?? null,
});

async function itunesSearch(term: string, country: string, limit: number): Promise<SearchResult[]> {
  const params = new URLSearchParams({ media: "podcast", term, country: country.toLowerCase(), limit: String(limit) });
  const r = await getJson<{ results?: ItunesResult[] }>(`https://itunes.apple.com/search?${params}`);
  return (r.results ?? []).filter((x) => x.feedUrl).map(fromItunes);
}

/**
 * I podcast più ascoltati in un paese secondo le classifiche di Apple Podcasts, in generale o
 * per un genere ([genreId] di Apple, es. 1318 = Tecnologia).
 */
export async function topCharts(country: string, limit = 10, genreId?: number): Promise<SearchResult[]> {
  const cc = country.toLowerCase();
  let ids: string[] = [];
  if (genreId != null) {
    ids = await chartIdsLegacy(cc, limit, genreId);
  } else {
    ids = await chartIdsV2(cc, limit).catch(() => []);
    if (ids.length === 0) ids = await chartIdsLegacy(cc, limit);
  }
  if (ids.length === 0) return [];
  // La classifica non contiene l'URL del feed: lo ricava la lookup di iTunes.
  const params = new URLSearchParams({ id: ids.join(","), country: cc, entity: "podcast" });
  const r = await getJson<{ results?: ItunesResult[] }>(`https://itunes.apple.com/lookup?${params}`);
  const byId = new Map((r.results ?? []).filter((x) => x.feedUrl).map((x) => [String(x.collectionId), x]));
  return ids.map((id) => byId.get(id)).filter((x): x is ItunesResult => !!x).map(fromItunes);
}

async function chartIdsV2(cc: string, limit: number): Promise<string[]> {
  const r = await getJson<{ feed?: { results?: { id: string }[] } }>(
    `https://rss.applemarketingtools.com/api/v2/${cc}/podcasts/top/${limit}/podcasts.json`,
  );
  return (r.feed?.results ?? []).map((x) => x.id);
}

async function chartIdsLegacy(cc: string, limit: number, genreId?: number): Promise<string[]> {
  const genre = genreId != null ? `genre=${genreId}/` : "";
  const body = await getText(`https://itunes.apple.com/${cc}/rss/toppodcasts/limit=${limit}/${genre}json`);
  const r = JSON.parse(body) as { feed?: { entry?: { id?: { attributes?: { "im:id"?: string } } }[] | { id?: { attributes?: { "im:id"?: string } } } } };
  const entries = r.feed?.entry;
  const list = Array.isArray(entries) ? entries : entries ? [entries] : [];
  return list.map((e) => e.id?.attributes?.["im:id"]).filter((x): x is string => !!x);
}

export const looksLikeUrl = (text: string) => /^https?:\/\//i.test(text.trim());
