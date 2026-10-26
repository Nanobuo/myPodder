import { kv } from "./db.ts";

export type ThemeMode = "system" | "light" | "dark";
export type NewBadgeStyle = "count" | "dot" | "none";

export interface AppSettings {
  theme: ThemeMode;
  coverColors: boolean;
  skipBackSec: number;
  skipForwardSec: number;
  speed: number;
  volume: number;
  deleteAfterPlayed: boolean;
  autoDownloadCount: number;
  refreshHours: number;
  addNewToQueue: boolean;
  sleepMinutes: number;
  libraryGrid: boolean;
  showRemaining: boolean;
  newBadge: NewBadgeStyle;
  autoCheckUpdates: boolean;
  lastUpdateCheck: number;
  /** Paese per ricerca e classifiche (codice ISO); vuoto = automatico. */
  searchCountry: string;
  showTopCharts: boolean;
  onboardingDone: boolean;
  interests: string[];
  lastFullRefresh: number;
  /** Ultima apertura dell'app: base per contare gli episodi usciti nel frattempo. */
  lastVisit: number;
  /** Cartella scelta per i download; vuoto = cartella dell'app. */
  downloadDir: string;
  lastEpisodeId: number | null;
  libraryTab: number;
}

const defaults: AppSettings = {
  theme: "system",
  coverColors: false,
  skipBackSec: 10,
  skipForwardSec: 30,
  speed: 1,
  volume: 1,
  deleteAfterPlayed: false,
  autoDownloadCount: 3,
  refreshHours: 6,
  addNewToQueue: true,
  sleepMinutes: 30,
  libraryGrid: true,
  showRemaining: true,
  newBadge: "dot",
  autoCheckUpdates: true,
  lastUpdateCheck: 0,
  searchCountry: "",
  showTopCharts: true,
  onboardingDone: false,
  interests: [],
  lastFullRefresh: 0,
  lastVisit: 0,
  downloadDir: "",
  lastEpisodeId: null,
  libraryTab: 0,
};

const KEY = "settings";

/** Impostazioni reattive, salvate nel database a ogni modifica. */
export const settings: AppSettings = $state({ ...defaults });

export async function loadSettings() {
  const saved = await kv.get<Partial<AppSettings>>(KEY);
  Object.assign(settings, defaults, saved ?? {});
}

let saveTimer: ReturnType<typeof setTimeout> | null = null;

export function updateSettings(patch: Partial<AppSettings>) {
  Object.assign(settings, patch);
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    saveTimer = null;
    void kv.set(KEY, $state.snapshot(settings));
  }, 200);
}

/** Salva subito (es. alla chiusura della finestra). */
export async function flushSettings() {
  if (saveTimer) {
    clearTimeout(saveTimer);
    saveTimer = null;
  }
  await kv.set(KEY, $state.snapshot(settings));
}
