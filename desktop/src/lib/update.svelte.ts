import { openUrl } from "@tauri-apps/plugin-opener";
import { appVersion, getJson } from "./net.ts";
import { settings, updateSettings } from "./settings.svelte.ts";

const REPO = "Nanobuo/myPodder";
/** Le versioni desktop si pubblicano con i tag "desktop-vX.Y.Z", separate da quelle Android. */
const TAG_PREFIX = "desktop-v";

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
  | { kind: "error" };

function newer(a: string, b: string): boolean {
  const pa = a.split(".").map((n) => parseInt(n, 10) || 0);
  const pb = b.split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    if ((pa[i] ?? 0) !== (pb[i] ?? 0)) return (pa[i] ?? 0) > (pb[i] ?? 0);
  }
  return false;
}

class Updates {
  state: UpdateState = $state({ kind: "idle" });
  /** Versione per cui l'utente ha scelto "Più tardi": non viene riproposta in questa sessione. */
  dismissed = $state<string | null>(null);

  async check(manual: boolean) {
    if (!manual) {
      if (!settings.autoCheckUpdates || Date.now() - settings.lastUpdateCheck < 12 * 3600_000) return;
    }
    this.state = { kind: "checking" };
    try {
      const releases = await getJson<{ tag_name: string; html_url: string; body?: string; draft: boolean; prerelease: boolean }[]>(
        `https://api.github.com/repos/${REPO}/releases?per_page=30`,
        { headers: { Accept: "application/vnd.github+json" } },
      );
      updateSettings({ lastUpdateCheck: Date.now() });
      const latest = releases.find((r) => !r.draft && r.tag_name.startsWith(TAG_PREFIX));
      const version = latest?.tag_name.slice(TAG_PREFIX.length);
      if (latest && version && newer(version, appVersion)) {
        this.state = { kind: "available", release: { version, url: latest.html_url, notes: latest.body ?? "" } };
      } else {
        this.state = { kind: "upToDate" };
      }
    } catch {
      this.state = { kind: "error" };
    }
  }

  open(release: Release) {
    void openUrl(release.url);
  }
}

export const updates = new Updates();
