import { openUrl } from "@tauri-apps/plugin-opener";
import { relaunch } from "@tauri-apps/plugin-process";
import { check as checkUpdater, type Update } from "@tauri-apps/plugin-updater";
import { appVersion, getJson, isTauri } from "./net.ts";
import { settings, updateSettings } from "./settings.svelte.ts";

const REPO = "Nanobuo/myPodder";

/** Installer per il sistema in uso, per controllare che la release lo contenga. */
const INSTALLER = /windows/i.test(navigator.userAgent) ? /\.exe$/ : /mac os/i.test(navigator.userAgent) ? /\.dmg$/ : /\.(AppImage|deb)$/;

export interface Release {
  version: string;
  url: string;
  notes: string;
}

export type UpdateState =
  | { kind: "idle" }
  | { kind: "checking" }
  | { kind: "upToDate" }
  | { kind: "available"; release: Release }
  | { kind: "downloading"; release: Release; progress: number | null }
  | { kind: "installing"; release: Release }
  | { kind: "failed"; release: Release }
  | { kind: "error" };

function newer(a: string, b: string): boolean {
  const pa = a.split(".").map((n) => parseInt(n, 10) || 0);
  const pb = b.split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    if ((pa[i] ?? 0) !== (pb[i] ?? 0)) return (pa[i] ?? 0) > (pb[i] ?? 0);
  }
  return false;
}

const releaseUrl = (version: string) => `https://github.com/${REPO}/releases/tag/v${version}`;

/**
 * Aggiornamenti dell'app desktop. Con l'updater di Tauri (release con latest.json firmato) si
 * scaricano e installano dall'app; altrimenti (es. installazione da .deb) si apre la release.
 */
class Updates {
  state: UpdateState = $state({ kind: "idle" });
  /** Versione per cui l'utente ha scelto "Più tardi": non viene riproposta in questa sessione. */
  dismissed = $state<string | null>(null);
  /** Aggiornamento installabile dall'app, se l'updater lo ha trovato. */
  private update: Update | null = null;

  get canInstall() {
    return this.update != null;
  }

  async check(manual: boolean) {
    if (!manual) {
      if (!settings.autoCheckUpdates || Date.now() - settings.lastUpdateCheck < 12 * 3600_000) return;
    }
    this.state = { kind: "checking" };
    if (isTauri) {
      try {
        this.update = await checkUpdater();
        updateSettings({ lastUpdateCheck: Date.now() });
        if (!this.update) {
          this.state = { kind: "upToDate" };
          return;
        }
        const v = this.update.version.replace(/^v/, "");
        this.state = { kind: "available", release: { version: v, url: releaseUrl(v), notes: this.update.body ?? "" } };
        return;
      } catch (e) {
        // Release senza latest.json o installazione non aggiornabile dall'app: si cerca su GitHub.
        console.warn("updater", e);
        this.update = null;
      }
    }
    await this.checkGithub();
  }

  private async checkGithub() {
    try {
      const latest = await getJson<{ tag_name: string; html_url: string; body?: string; assets: { name: string }[] }>(
        `https://api.github.com/repos/${REPO}/releases/latest`,
        { headers: { Accept: "application/vnd.github+json" } },
      );
      updateSettings({ lastUpdateCheck: Date.now() });
      const version = latest.tag_name.replace(/^v/, "");
      if (newer(version, appVersion) && latest.assets.some((a) => INSTALLER.test(a.name))) {
        this.state = { kind: "available", release: { version, url: latest.html_url, notes: latest.body ?? "" } };
      } else {
        this.state = { kind: "upToDate" };
      }
    } catch {
      this.state = { kind: "error" };
    }
  }

  /** Scarica e installa l'aggiornamento, poi riavvia l'app; senza updater apre la release. */
  async install(release: Release) {
    const update = this.update;
    if (!update) {
      this.open(release);
      return;
    }
    let total = 0;
    let done = 0;
    this.state = { kind: "downloading", release, progress: null };
    try {
      await update.downloadAndInstall((event) => {
        if (event.event === "Started") total = event.data.contentLength ?? 0;
        else if (event.event === "Progress") {
          done += event.data.chunkLength;
          if (total > 0) this.state = { kind: "downloading", release, progress: Math.min(1, done / total) };
        } else if (event.event === "Finished") this.state = { kind: "installing", release };
      });
      // Su Windows l'installer chiude e riapre l'app da solo; altrove si riavvia qui.
      await relaunch();
    } catch (e) {
      console.warn("update install", e);
      this.state = { kind: "failed", release };
    }
  }

  open(release: Release) {
    void openUrl(release.url);
  }
}

export const updates = new Updates();
