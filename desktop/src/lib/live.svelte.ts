import { queueDb } from "./db.ts";

/** Dati letti spesso da molte schermate, ricaricati quando cambiano. */
export const live = $state({ queueIds: [] as number[] });

export async function reloadQueueIds() {
  live.queueIds = await queueDb.ids();
}
