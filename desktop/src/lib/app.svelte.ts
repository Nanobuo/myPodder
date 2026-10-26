import { getCurrentWindow } from "@tauri-apps/api/window";
import { episodesDb, podcastsDb } from "./db.ts";
import { prepareFolder, resumePending } from "./downloads.svelte.ts";
import { t } from "./i18n.ts";
import { isTauri } from "./net.ts";
import { player } from "./player.svelte.ts";
import { isRefreshing, refreshAll } from "./repo.ts";
import { flushSettings, loadSettings, settings, updateSettings } from "./settings.svelte.ts";
import { nav, snackbar } from "./store.svelte.ts";
import { sync } from "./sync.svelte.ts";
import { updates } from "./update.svelte.ts";

const DAY_MS = 24 * 3600_000;
const REFRESH_INTERVAL_MS = 15 * 60_000;

/** Stato generale dell'app: avvio, aggiornamenti dei feed e ritorno in primo piano. */
class AppState {
  ready = $state(false);
  refreshing = $state(false);
  needsOnboarding = $state(false);
  private checkingNew = false;
  private autoRefresh: ReturnType<typeof setInterval> | null = null;

  async start() {
    await loadSettings();
    const podcasts = await podcastsDb.all();
    // Chi ha già delle iscrizioni (es. dalla sincronizzazione) non vede il benvenuto.
    if (!settings.onboardingDone && podcasts.length) updateSettings({ onboardingDone: true });
    this.needsOnboarding = !settings.onboardingDone && podcasts.length === 0;
    await sync.init();
    await player.init().catch((e) => console.warn("player restore", e));
    this.ready = true;

    if (isTauri) {
      await prepareFolder().catch(() => undefined);
      await resumePending(await episodesDb.downloads());
      const win = getCurrentWindow();
      await win.onFocusChanged(({ payload: focused }) => {
        if (focused) this.onForeground();
      });
      await win.onCloseRequested(async () => {
        await player.shutdown();
        await flushSettings();
      });
    }
    this.onForeground();
    this.scheduleAutoRefresh();
    void updates.check(false);
  }

  /** Aggiornamento automatico dei feed ogni N ore (0 = solo manuale), con sincronizzazione. */
  scheduleAutoRefresh() {
    if (this.autoRefresh) clearInterval(this.autoRefresh);
    this.autoRefresh = setInterval(() => {
      if (settings.refreshHours > 0 && Date.now() - settings.lastFullRefresh > settings.refreshHours * 3600_000) {
        void this.refresh(false).then(() => sync.syncNow());
      }
    }, 5 * 60_000);
  }

  /**
   * L'app torna in primo piano: sincronizza, aggiorna i feed se non sono recenti e segnala
   * quanti episodi sono usciti dall'ultima apertura.
   */
  onForeground() {
    sync.requestSync(0);
    if (this.checkingNew || this.needsOnboarding) return;
    this.checkingNew = true;
    void (async () => {
      try {
        const now = Date.now();
        const since = settings.lastVisit > 0 ? settings.lastVisit : now - DAY_MS;
        if (now - settings.lastFullRefresh > REFRESH_INTERVAL_MS) {
          await this.refresh(false);
          // I feed aggiornati possono avere episodi già ascoltati altrove.
          sync.requestSync(0);
        }
        const count = await episodesDb.countPublishedSince(since);
        updateSettings({ lastVisit: now });
        if (count > 0) {
          snackbar.show(t("new_episodes_available", count), {
            label: t("show"),
            run: () => {
              updateSettings({ libraryTab: 1 });
              nav.navigate({ name: "library" });
            },
          }, 8000);
        }
      } finally {
        this.checkingNew = false;
      }
    })();
  }

  /** Aggiorna tutti i feed; [report] mostra l'esito (aggiornamento manuale). */
  async refresh(report: boolean) {
    if (isRefreshing()) return;
    this.refreshing = true;
    try {
      const found = await refreshAll();
      if (report) snackbar.show(found > 0 ? t("new_episodes", found) : t("no_new_episodes"));
    } catch {
      if (report) snackbar.show(t("refresh_error"));
    } finally {
      this.refreshing = false;
    }
  }

  finishOnboarding() {
    updateSettings({ onboardingDone: true });
    this.needsOnboarding = false;
  }
}

export const app = new AppState();
