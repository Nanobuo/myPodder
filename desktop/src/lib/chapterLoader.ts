import { appDataDir, join } from "@tauri-apps/api/path";
import { writeFile, mkdir } from "@tauri-apps/plugin-fs";
import { convertFileSrc } from "@tauri-apps/api/core";
import { chaptersDb, episodesDb, type Chapter, type Episode } from "./db.ts";
import { id3TagSize, parseDescriptionChapters, parseId3Chapters, parseJsonChapters } from "./chapters.ts";
import { getText, httpFetch } from "./net.ts";

const MAX_TAG = 8 * 1024 * 1024;
const loading = new Map<number, Promise<void>>();

/**
 * Recupera i capitoli di un episodio nell'ordine: JSON Podcasting 2.0, capitoli Podlove del feed,
 * tag ID3 (CHAP) del file audio, timestamp nella descrizione. Il risultato resta nel database.
 */
export function ensureChapters(episodeId: number): Promise<void> {
  let p = loading.get(episodeId);
  if (!p) {
    p = load(episodeId).finally(() => loading.delete(episodeId));
    loading.set(episodeId, p);
  }
  return p;
}

async function load(episodeId: number) {
  const episode = await episodesDb.item(episodeId);
  if (!episode || episode.chaptersLoaded) return;
  let chapters: Chapter[] = [];
  if (episode.chaptersUrl) {
    chapters = await getText(episode.chaptersUrl)
      .then((text) => parseJsonChapters(text).map((c) => ({ episodeId, ...c })))
      .catch(() => []);
  }
  if (!chapters.length) chapters = await chaptersDb.get(episodeId); // capitoli Podlove letti dal feed
  if (!chapters.length) chapters = await loadId3(episode).catch(() => []);
  if (!chapters.length) chapters = parseDescriptionChapters(episode.description).map((c) => ({ episodeId, ...c }));
  const unique = [...new Map(chapters.map((c) => [c.startMs, c])).values()];
  await chaptersDb.replace(episodeId, unique);
  await episodesDb.setChaptersLoaded(episodeId);
}

/** Legge solo l'inizio del file audio, fino alla fine del tag ID3. */
async function readTag(url: string): Promise<Uint8Array | null> {
  const response = await httpFetch(url, { headers: { Range: "bytes=0-" } });
  if (!response.ok || !response.body) return null;
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;
  let size: number | null = null;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done || !value) break;
      chunks.push(value);
      received += value.length;
      if (size == null && received >= 10) {
        const head = concat(chunks, 10);
        size = id3TagSize(head);
        if (size == null || size > MAX_TAG) return null;
      }
      if (size != null && received >= size) break;
    }
  } finally {
    void reader.cancel().catch(() => undefined);
  }
  return size == null ? null : concat(chunks, size);
}

function concat(chunks: Uint8Array[], max: number): Uint8Array {
  const out = new Uint8Array(Math.min(max, chunks.reduce((n, c) => n + c.length, 0)));
  let o = 0;
  for (const c of chunks) {
    if (o >= out.length) break;
    const part = c.subarray(0, out.length - o);
    out.set(part, o);
    o += part.length;
  }
  return out;
}

async function loadId3(episode: Episode): Promise<Chapter[]> {
  const tag = await readTag(episode.audioUrl);
  if (!tag) return [];
  const parsed = parseId3Chapters(tag);
  if (!parsed.length) return [];
  const dir = await join(await appDataDir(), "chapter_images");
  await mkdir(dir, { recursive: true }).catch(() => undefined);
  const result: Chapter[] = [];
  for (const [i, c] of parsed.entries()) {
    let imageUrl: string | null = null;
    if (c.image) {
      const ext = c.imageMime?.includes("png") ? "png" : "jpg";
      const path = await join(dir, `${episode.id}_${i}.${ext}`);
      await writeFile(path, c.image);
      imageUrl = convertFileSrc(path);
    }
    result.push({ episodeId: episode.id, startMs: c.startMs, title: c.title ?? `#${i + 1}`, imageUrl, url: c.url });
  }
  return result;
}
