import { queueDb } from "./db.ts";

/** Dati letti spesso da molte schermate, ricaricati quando cambiano. */
export const live = $state({ queueIds: [] as number[] });

/** Episodi in coda esclusi [currentId], già nel player: è il numero mostrato sul badge. */
export function queueCount(currentId: number | undefined): number {
  return live.queueIds.filter((id) => id !== currentId).length;
}

export async function reloadQueueIds() {
  live.queueIds = await queueDb.ids();
}
