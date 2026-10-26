import { kv, podcastsDb } from "./db.ts";
import type { StringKey } from "./i18n.ts";
import { fillMissingCategories } from "./repo.ts";
import { byCategory, topCharts, trending, type SearchResult } from "./search.ts";
import { defaultCountry, languageOf } from "./countries.ts";
import { settings } from "./settings.svelte.ts";

/** Argomento proposto all'utente; [category] è il nome della categoria di Apple Podcasts. */
export interface Topic {
  category: string;
  label: StringKey;
  genreId: number;
}

export const topics: Topic[] = [
  { category: "News", label: "topic_news", genreId: 1489 },
  { category: "Society & Culture", label: "topic_society", genreId: 1324 },
  { category: "Comedy", label: "topic_comedy", genreId: 1303 },
  { category: "Technology", label: "topic_technology", genreId: 1318 },
  { category: "Science", label: "topic_science", genreId: 1533 },
  { category: "History", label: "topic_history", genreId: 1487 },
  { category: "True Crime", label: "topic_true_crime", genreId: 1488 },
  { category: "Business", label: "topic_business", genreId: 1321 },
  { category: "Education", label: "topic_education", genreId: 1304 },
  { category: "Sports", label: "topic_sports", genreId: 1545 },
  { category: "Health & Fitness", label: "topic_health", genreId: 1512 },
  { category: "Arts", label: "topic_arts", genreId: 1301 },
  { category: "Music", label: "topic_music", genreId: 1310 },
  { category: "TV & Film", label: "topic_tv_film", genreId: 1309 },
  { category: "Leisure", label: "topic_leisure", genreId: 1502 },
  { category: "Kids & Family", label: "topic_kids", genreId: 1305 },
  { category: "Fiction", label: "topic_fiction", genreId: 1483 },
  { category: "Religion & Spirituality", label: "topic_religion", genreId: 1314 },
];

/** Generi Apple Podcasts noti, anche con i nomi delle sottocategorie più comuni. */
const extraGenres: Record<string, number> = { government: 1511, documentary: 1324, politics: 1489, "tech news": 1318 };

export function genreIdOf(category: string): number | undefined {
  const key = category.trim().toLowerCase();
  return topics.find((t) => t.category.toLowerCase() === key)?.genreId ?? extraGenres[key];
}

export const normalizeUrl = (url: string) =>
  url.trim().toLowerCase().replace(/^https?:\/\//, "").replace(/^www\./, "").replace(/\/+$/, "");

/** Categorie più frequenti tra le iscrizioni, eventualmente completate dagli interessi. */
async function favouriteCategories(interests: string[], max = 3): Promise<string[]> {
  // Podcast seguiti prima che le categorie venissero salvate: le recupera dai feed.
  await fillMissingCategories().catch(() => undefined);
  const counts = new Map<string, number>();
  for (const p of await podcastsDb.all()) {
    for (const c of p.categories?.split("|") ?? []) if (c.trim()) counts.set(c, (counts.get(c) ?? 0) + 1);
  }
  const fromSubscriptions = [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([c]) => c);
  return [...new Set([...fromSubscriptions, ...interests])].slice(0, max);
}

/** Podcast locali per uno o più argomenti, alternando gli argomenti per varietà. */
export async function forTopics(categories: string[], country: string, limit: number): Promise<SearchResult[]> {
  const language = languageOf(country);
  const subscribed = new Set((await podcastsDb.all()).map((p) => normalizeUrl(p.feedUrl)));
  const perTopic = await Promise.all(
    categories.map(async (category) => {
      // Prima la classifica del genere nel paese (già locale), poi Podcast Index/iTunes.
      const id = genreIdOf(category);
      const charts = id != null ? await topCharts(country, 25, id).catch(() => []) : [];
      if (charts.length) return charts;
      const list = await byCategory(category, country, language, 20).catch(() => []);
      return list.filter((r) => !r.language || r.language.toLowerCase().startsWith(language));
    }),
  );
  const seen = new Set<string>();
  const merged: SearchResult[] = [];
  for (let i = 0; merged.length < limit && perTopic.some((l) => i < l.length); i++) {
    for (const list of perTopic) {
      const item = list[i];
      if (!item) continue;
      const key = normalizeUrl(item.feedUrl);
      if (!subscribed.has(key) && !seen.has(key)) {
        seen.add(key);
        merged.push(item);
      }
      if (merged.length >= limit) break;
    }
  }
  return merged;
}

export interface DiscoverContent {
  suggestions: SearchResult[];
  topCharts: SearchResult[];
  trending: SearchResult[];
}

interface Snapshot {
  key: string;
  savedAt: number;
  content: DiscoverContent;
}

const MAX_AGE_MS = 30 * 60 * 1000;

/**
 * Contenuti della sezione Cerca quando non si sta cercando nulla: preparati all'avvio, salvati
 * per mostrarli subito alla riapertura e aggiornati al più ogni 30 minuti o quando cambiano
 * paese, interessi, classifica o iscrizioni. Le sezioni compaiono man mano che arrivano.
 */
class DiscoverStore {
  content: DiscoverContent = $state({ suggestions: [], topCharts: [], trending: [] });
  loading = $state(true);
  private currentKey = "";
  private loadId = 0;

  country = $derived(settings.searchCountry || defaultCountry());

  async keyNow(): Promise<string> {
    const count = (await podcastsDb.all()).length;
    return [this.country, settings.showTopCharts, [...settings.interests].sort().join(","), count].join("|");
  }

  /** Avvia o aggiorna i contenuti se la chiave è cambiata o i dati sono vecchi. */
  async ensure(force = false) {
    const key = await this.keyNow();
    if (!force && key === this.currentKey) return;
    if (!this.currentKey) {
      const cached = await kv.get<Snapshot>("discover");
      if (cached) {
        this.content = cached.content;
        this.loading = false;
        if (!force && cached.key === key && Date.now() - cached.savedAt < MAX_AGE_MS) {
          this.currentKey = key;
          return;
        }
        // Se è cambiato il paese, i vecchi contenuti non valgono più.
        if (cached.key.split("|")[0] !== key.split("|")[0]) this.content = { suggestions: [], topCharts: [], trending: [] };
      }
    } else if (this.currentKey.split("|")[0] !== key.split("|")[0]) {
      this.content = { suggestions: [], topCharts: [], trending: [] };
    }
    this.currentKey = key;
    await this.load(key);
  }

  private async load(key: string) {
    const id = ++this.loadId;
    this.loading = true;
    const country = this.country;
    const language = languageOf(country);
    const set = (patch: Partial<DiscoverContent>) => {
      if (id === this.loadId) this.content = { ...this.content, ...patch };
    };
    await Promise.all([
      favouriteCategories(settings.interests)
        .then((cats) => (cats.length ? forTopics(cats, country, 15) : []))
        .then((suggestions) => set({ suggestions }))
        .catch(() => undefined),
      (settings.showTopCharts ? topCharts(country) : Promise.resolve([]))
        .then((list) => set({ topCharts: list }))
        .catch(() => undefined),
      trending(language).then((list) => set({ trending: list })).catch(() => undefined),
    ]);
    if (id !== this.loadId) return;
    this.loading = false;
    await kv.set("discover", { key, savedAt: Date.now(), content: $state.snapshot(this.content) } satisfies Snapshot);
  }
}

export const discover = new DiscoverStore();
