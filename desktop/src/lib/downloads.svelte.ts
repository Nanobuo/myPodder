import { invoke } from "@tauri-apps/api/core";
import { appDataDir, join } from "@tauri-apps/api/path";
import { download as tauriDownload } from "@tauri-apps/plugin-upload";
import { DownloadState, episodesDb, type Episode } from "./db.ts";
import { settings } from "./settings.svelte.ts";
import { changed } from "./store.svelte.ts";
import { appVersion } from "./net.ts";

/** Avanzamento dei download in corso (0–100), per episodio. */
export const progress: Record<number, number> = $state({});

const waiting: number[] = [];
const active = new Set<number>();
const MAX_PARALLEL = 2;

/** Cartella base dei download: quella scelta dall'utente oppure quella dell'app. */
async function baseDir(): Promise<string> {
  if (settings.downloadDir) return join(settings.downloadDir, "podcasts");
  return join(await appDataDir(), "podcasts");
}

/** Nome di file o cartella valido su tutti i sistemi. */
function safeName(name: string, max = 80): string {
  const cleaned = name.replace(/[\\/:*?"<>|\u0000-\u001f]/g, " ").replace(/\s+/g, " ").trim().replace(/[. ]+$/, "");
  return (cleaned || "episodio").slice(0, max);
}

function extension(e: Episode): string {
  const fromUrl = /\.([a-z0-9]{2,4})(?:$|[?#])/i.exec(new URL(e.audioUrl, "https://x").pathname)?.[1];
  if (fromUrl) return fromUrl.toLowerCase();
  if (e.mimeType?.includes("mp4") || e.mimeType?.includes("m4a")) return "m4a";
  if (e.mimeType?.includes("ogg")) return "ogg";
  return "mp3";
}

export async function prepareFolder() {
  const dir = await baseDir();
  await invoke("ensure_dir", { path: dir });
  // Il player legge i file scaricati tramite il protocollo "asset": la cartella va autorizzata.
  await invoke("allow_dir", { path: settings.downloadDir || (await appDataDir()) });
}

export async function enqueue(episodeId: number) {
  if (active.has(episodeId) || waiting.includes(episodeId)) return;
  await episodesDb.setDownloadState(episodeId, DownloadState.QUEUED);
  waiting.push(episodeId);
  changed("episodes", "downloads");
  pump();
}

function pump() {
  while (active.size < MAX_PARALLEL && waiting.length) {
    const id = waiting.shift()!;
    active.add(id);
    void run(id).finally(() => {
      active.delete(id);
      delete progress[id];
      pump();
    });
  }
}

async function run(id: number) {
  const item = await episodesDb.item(id);
  if (!item) return;
  try {
    await prepareFolder();
    const dir = await join(await baseDir(), safeName(item.podcastTitle, 60));
    await invoke("ensure_dir", { path: dir });
    const path = await join(dir, `${safeName(item.title)}.${extension(item)}`);
    await episodesDb.setDownloadState(id, DownloadState.DOWNLOADING, 0);
    changed("episodes", "downloads");
    progress[id] = 0;
    let lastShown = 0;
    await tauriDownload(
      item.audioUrl,
      path,
      ({ progressTotal, total }) => {
        if (!total) return;
        const pct = Math.min(99, Math.floor((progressTotal / total) * 100));
        if (pct !== lastShown) {
          lastShown = pct;
          progress[id] = pct;
        }
      },
      new Map([["User-Agent", `myPodder/${appVersion} (Desktop)`]]),
    );
    const size = await invoke<number>("file_size", { path });
    if (!size) throw new Error("empty file");
    await episodesDb.setDownloaded(id, path, size);
  } catch (e) {
    console.warn("download failed", id, e);
    await episodesDb.setDownloadState(id, DownloadState.FAILED);
  }
  changed("episodes", "downloads");
}

export async function deleteDownload(episodeId: number) {
  const i = waiting.indexOf(episodeId);
  if (i >= 0) waiting.splice(i, 1);
  const item = await episodesDb.item(episodeId);
  if (item?.localPath) await invoke("delete_file", { path: item.localPath }).catch(() => undefined);
  await episodesDb.clearDownload(episodeId);
  changed("episodes", "downloads");
}

export async function deleteForPodcast(podcastId: number) {
  for (const e of await episodesDb.downloadedForPodcast(podcastId)) {
    if (e.localPath) await invoke("delete_file", { path: e.localPath }).catch(() => undefined);
  }
}

/** Elimina i download degli episodi già ascoltati; restituisce quanti sono stati rimossi. */
export async function deletePlayed(): Promise<number> {
  const list = await episodesDb.downloadedAndCompleted();
  for (const e of list) await deleteDownload(e.id);
  return list.length;
}

let mediaBase: Promise<string> | null = null;

/**
 * Indirizzo da cui il player legge un episodio scaricato, se il file esiste ancora: lo serve un
 * piccolo server locale (il protocollo "asset" di Tauri non è riproducibile su Linux e macOS).
 */
export async function localSource(e: Episode): Promise<string | null> {
  if (e.downloadState !== DownloadState.DONE || !e.localPath) return null;
  const exists = await invoke<boolean>("file_exists", { path: e.localPath }).catch(() => false);
  if (!exists) return null;
  mediaBase ??= invoke<string>("media_base_url");
  const base = await mediaBase.catch(() => null);
  if (!base) return null;
  return base + encodeURIComponent(e.localPath.replace(/\\/g, "/")).replace(/%2F/g, "/");
}

/** Download interrotti alla chiusura dell'app: ripartono all'avvio. */
export async function resumePending(list: Episode[]) {
  for (const e of list) {
    if (e.downloadState === DownloadState.QUEUED || e.downloadState === DownloadState.DOWNLOADING) await enqueue(e.id);
  }
}
