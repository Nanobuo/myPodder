import { deleteDownload, enqueue } from "./downloads.svelte.ts";
import { player } from "./player.svelte.ts";
import * as repo from "./repo.ts";
import { sync } from "./sync.svelte.ts";

/** Azioni sugli episodi usate da liste, pagina dell'episodio e player. */
export const actions = {
  play: (id: number, startMs?: number) => player.play(id, startMs),
  togglePlay(id: number) {
    if (player.current?.id === id) player.togglePlayPause();
    else void player.play(id);
  },
  download: (id: number) => enqueue(id),
  deleteDownload: (id: number) => deleteDownload(id),
  addToQueue: (id: number) => repo.addToQueue(id),
  playNext: (id: number) => repo.playNext(id),
  removeFromQueue: (id: number) => repo.removeFromQueue(id),
  async setPlayed(id: number, played: boolean) {
    if (played && player.current?.id === id) player.pause();
    await repo.setCompleted(id, played);
    if (player.current?.id === id) await player.refreshCurrent();
    sync.requestSync();
  },
};
