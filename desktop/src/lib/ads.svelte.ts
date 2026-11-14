import { invoke } from "@tauri-apps/api/core";
import { appDataDir, join } from "@tauri-apps/api/path";
import { download as tauriDownload } from "@tauri-apps/plugin-upload";
import { adsDb, episodesDb, type AdSegment, type Chapter } from "./db.ts";
import { extension, onDownloaded } from "./downloads.svelte.ts";
import { appVersion } from "./net.ts";
import { settings } from "./settings.svelte.ts";
import { changed } from "./store.svelte.ts";

/**
 * Riconoscimento delle pubblicità negli episodi scaricati:
 * - tratti che si ripetono in più episodi dello stesso podcast (spot, sigle, messaggi);
 * - tratti che cambiano scaricando di nuovo l'episodio (pubblicità inserite al download), se attivo;
 * - capitoli con un titolo da pubblicità ("Sponsor", "Pubblicità", ...).
 * Il player propone il pulsante "Salta pubblicità"; niente viene saltato da solo.
 */

/** Impronte tenute per podcast: bastano gli ultimi episodi per trovare gli spot che girano. */
const KEEP_FINGERPRINTS = 8;
/** Episodi con cui si confronta un nuovo episodio. */
const COMPARE_WITH = 5;

const AD_CHAPTER = /sponsor|pubblicit|annunc|promo\b|promozion|\bspot\b|\bads?\b|ad break|advert|commercial|werbung|anuncio|publicit/i;

/** Capitoli che sembrano pubblicità, come tratti da saltare. */
export function chapterAds(chapters: Chapter[], durationMs: number): AdSegment[] {
  const out: AdSegment[] = [];
  chapters.forEach((c, i) => {
    if (!AD_CHAPTER.test(c.title)) return;
    const end = chapters[i + 1]?.startMs ?? durationMs;
    if (end > c.startMs) out.push({ startMs: c.startMs, endMs: end, source: "chapter" });
  });
  return out;
}

async function fingerprintPath(episodeId: number): Promise<string> {
  return join(await appDataDir(), "fingerprints", `${episodeId}.fp`);
}

const waiting: number[] = [];
let running = false;

/** Mette in fila l'analisi di un episodio scaricato (una alla volta, in background). */
export function scheduleAnalysis(episodeId: number) {
  if (!settings.adDetection || waiting.includes(episodeId)) return;
  waiting.push(episodeId);
  void pump();
}

async function pump() {
  if (running) return;
  running = true;
  while (waiting.length) {
    const id = waiting.shift()!;
    await analyze(id).catch((e) => console.warn("ad analysis", id, e));
  }
  running = false;
}

/** All'avvio: episodi già scaricati e non ancora analizzati. */
export async function analyzePending() {
  if (!settings.adDetection) return;
  for (const { id } of await adsDb.pending(30)) scheduleAnalysis(id);
}

onDownloaded.push(scheduleAnalysis);

async function analyze(episodeId: number) {
  const item = await episodesDb.item(episodeId);
  if (!item?.localPath || !(await invoke<boolean>("file_exists", { path: item.localPath }))) return;
  const previous = await adsDb.analysis(episodeId);
  const fp = await fingerprintPath(episodeId);
  await invoke("ensure_dir", { path: await join(await appDataDir(), "fingerprints") });
  await invoke<number>("ad_fingerprint", { audio: item.localPath, out: fp });

  // 1. Tratti ripetuti negli altri episodi dello stesso podcast.
  const others = (await adsDb.withFingerprint(item.podcastId, episodeId)).slice(0, COMPARE_WITH);
  const hits = await invoke<{ other: number; startMs: number; endMs: number; otherStartMs: number; otherEndMs: number }[]>("ad_repeated", {
    fp,
    others: others.map((o) => o.fingerprint),
  });
  const mine = hits.map((h) => ({ startMs: h.startMs, endMs: h.endMs }));
  await adsDb.replace(episodeId, "repeat", mergeRanges(mine));
  // Lo stesso tratto vale anche per l'altro episodio: si aggiunge ai suoi.
  for (const [k, other] of others.entries()) {
    const found = hits.filter((h) => h.other === k).map((h) => ({ startMs: h.otherStartMs, endMs: h.otherEndMs }));
    if (!found.length) continue;
    const existing = (await adsDb.segments(other.episodeId)).filter((s) => s.source === "repeat");
    await adsDb.replace(other.episodeId, "repeat", mergeRanges([...existing, ...found]));
  }

  // 2. Pubblicità inserite al download: si riscarica l'episodio e si confrontano le due copie.
  let insertedChecked = previous?.insertedChecked ?? false;
  if (settings.adCompareDownload && !insertedChecked) {
    const tmpDir = await join(await appDataDir(), "tmp");
    await invoke("ensure_dir", { path: tmpDir });
    const tmp = await join(tmpDir, `${episodeId}.${extension(item)}`);
    try {
      await tauriDownload(item.audioUrl, tmp, undefined, new Map([["User-Agent", `myPodder/${appVersion} (Desktop)`]]));
      const ranges = await invoke<[number, number][]>("ad_inserted", { fp, audio: tmp });
      await adsDb.replace(episodeId, "inserted", ranges.map(([startMs, endMs]) => ({ startMs, endMs })));
      insertedChecked = true;
    } catch (e) {
      console.warn("second download", episodeId, e);
    } finally {
      await invoke("delete_file", { path: tmp }).catch(() => undefined);
    }
  }

  await adsDb.save({ episodeId, podcastId: item.podcastId, analyzedAt: Date.now(), fingerprint: fp, insertedChecked });
  // Le impronte più vecchie non servono più.
  for (const old of (await adsDb.withFingerprint(item.podcastId, episodeId)).slice(KEEP_FINGERPRINTS - 1)) {
    if (old.fingerprint) await invoke("delete_file", { path: old.fingerprint }).catch(() => undefined);
    await adsDb.dropFingerprint(old.episodeId);
  }
  changed("ads");
}

function mergeRanges(list: { startMs: number; endMs: number }[]): { startMs: number; endMs: number }[] {
  const sorted = [...list].sort((a, b) => a.startMs - b.startMs);
  const out: { startMs: number; endMs: number }[] = [];
  for (const r of sorted) {
    const last = out[out.length - 1];
    if (last && r.startMs <= last.endMs + 1000) last.endMs = Math.max(last.endMs, r.endMs);
    else out.push({ ...r });
  }
  return out;
}

/** Tutti i tratti da proporre di saltare in un episodio, uniti e ordinati. */
export async function adSegments(episodeId: number, chapters: Chapter[], durationMs: number): Promise<AdSegment[]> {
  if (!settings.adDetection) return [];
  const stored = await adsDb.segments(episodeId);
  const all = [...stored, ...chapterAds(chapters, durationMs)].sort((a, b) => a.startMs - b.startMs);
  const out: AdSegment[] = [];
  for (const s of all) {
    const last = out[out.length - 1];
    if (last && s.startMs <= last.endMs + 1000) last.endMs = Math.max(last.endMs, s.endMs);
    else out.push({ ...s });
  }
  return out;
}
