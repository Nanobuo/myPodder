<script lang="ts">
  import { open } from "@tauri-apps/plugin-dialog";
  import { countryOf, defaultCountry } from "../lib/countries.ts";
  import { prepareFolder } from "../lib/downloads.svelte.ts";
  import { formatRelative } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import { appVersion } from "../lib/net.ts";
  import { exportOpml, importOpml } from "../lib/opml.ts";
  import { settings, updateSettings, type NewBadgeStyle, type ThemeMode } from "../lib/settings.svelte.ts";
  import { snackbar } from "../lib/store.svelte.ts";
  import { sync } from "../lib/sync.svelte.ts";
  import { updates } from "../lib/update.svelte.ts";
  import Chip from "../components/Chip.svelte";
  import CountryPicker from "../components/CountryPicker.svelte";
  import Icon from "../components/Icon.svelte";
  import ListItem from "../components/ListItem.svelte";
  import Segmented from "../components/Segmented.svelte";
  import Switch from "../components/Switch.svelte";
  import SyncSettings from "./SyncSettings.svelte";
  import Wordmark from "../components/Wordmark.svelte";

  let pickerOpen = $state(false);

  async function chooseFolder() {
    const dir = await open({ directory: true, multiple: false });
    if (typeof dir !== "string") return;
    updateSettings({ downloadDir: dir });
    await prepareFolder().catch(() => undefined);
  }

  async function runImport() {
    try {
      const r = await importOpml((done, total) => snackbar.show(t("opml_importing", done, total), undefined, 60_000));
      if (!r) return snackbar.dismiss();
      snackbar.show([t("opml_imported", r.imported), r.failed ? t("opml_failed", r.failed) : ""].join(" ").trim());
      sync.requestSync();
    } catch {
      snackbar.show(t("opml_error"));
    }
  }

  async function runExport() {
    try {
      const n = await exportOpml();
      if (n != null) snackbar.show(t("opml_exported", n));
    } catch {
      snackbar.show(t("opml_error"));
    }
  }

  const refreshOptions = [0, 1, 3, 6, 12, 24];
</script>

<div class="screen">
  <div class="screen-inner narrow">
    <header class="top-bar"><h1>{t("settings")}</h1></header>

    <h2 class="section">{t("settings_appearance")}</h2>
    <div class="choice">
      <span class="body-large">{t("theme")}</span>
      <Segmented
        options={["system", "light", "dark"] as ThemeMode[]}
        selected={settings.theme}
        label={(v) => t(v === "system" ? "theme_system" : v === "light" ? "theme_light" : "theme_dark")}
        onselect={(v) => updateSettings({ theme: v })}
      />
    </div>
    <ListItem headline={t("cover_colors")} supporting={t("cover_colors_summary")} onclick={() => updateSettings({ coverColors: !settings.coverColors })}>
      {#snippet trailing()}<Switch checked={settings.coverColors} onchange={(v) => updateSettings({ coverColors: v })} />{/snippet}
    </ListItem>
    <div class="choice">
      <span class="body-large">{t("library_layout")}</span>
      <Segmented
        options={[true, false]}
        selected={settings.libraryGrid}
        label={(v) => (v ? t("layout_grid") : t("layout_list"))}
        onselect={(v) => updateSettings({ libraryGrid: v })}
      />
    </div>
    <div class="choice">
      <span class="body-large">{t("new_badge")}</span>
      <Segmented
        options={["dot", "count", "none"] as NewBadgeStyle[]}
        selected={settings.newBadge}
        label={(v) => t(v === "dot" ? "badge_dot" : v === "count" ? "badge_count" : "badge_none")}
        onselect={(v) => updateSettings({ newBadge: v })}
      />
    </div>

    <h2 class="section">{t("settings_playback")}</h2>
    <div class="choice">
      <span class="body-large">{t("skip_back_interval")}</span>
      <div class="chips">
        {#each [5, 10, 15, 30] as s (s)}
          <Chip selected={settings.skipBackSec === s} onclick={() => updateSettings({ skipBackSec: s })}>{t("seconds_short", s)}</Chip>
        {/each}
      </div>
    </div>
    <div class="choice">
      <span class="body-large">{t("skip_forward_interval")}</span>
      <div class="chips">
        {#each [10, 15, 30, 45, 60] as s (s)}
          <Chip selected={settings.skipForwardSec === s} onclick={() => updateSettings({ skipForwardSec: s })}>{t("seconds_short", s)}</Chip>
        {/each}
      </div>
    </div>
    <ListItem headline={t("show_remaining")} supporting={t("show_remaining_summary")} onclick={() => updateSettings({ showRemaining: !settings.showRemaining })}>
      {#snippet trailing()}<Switch checked={settings.showRemaining} onchange={(v) => updateSettings({ showRemaining: v })} />{/snippet}
    </ListItem>
    <ListItem headline={t("shortcuts")} supporting={t("shortcuts_summary")} icon="keyboard" />

    <h2 class="section">{t("settings_downloads")}</h2>
    <ListItem headline={t("storage_location")} supporting={settings.downloadDir || t("storage_default")} icon="folder" onclick={chooseFolder}>
      {#snippet trailing()}
        {#if settings.downloadDir}
          <button class="link label-large" onclick={(e) => { e.stopPropagation(); updateSettings({ downloadDir: "" }); }}>{t("storage_reset")}</button>
        {:else}
          <span class="link label-large">{t("storage_choose")}</span>
        {/if}
      {/snippet}
    </ListItem>
    <p class="body-small muted note">{t("storage_existing_note")}</p>
    <ListItem headline={t("delete_after_played")} supporting={t("delete_after_played_summary")} onclick={() => updateSettings({ deleteAfterPlayed: !settings.deleteAfterPlayed })}>
      {#snippet trailing()}<Switch checked={settings.deleteAfterPlayed} onchange={(v) => updateSettings({ deleteAfterPlayed: v })} />{/snippet}
    </ListItem>
    <div class="choice column">
      <span class="body-large">{t("auto_download_count")}</span>
      <div class="chips">
        {#each [1, 3, 5, 10] as n (n)}
          <Chip selected={settings.autoDownloadCount === n} onclick={() => updateSettings({ autoDownloadCount: n })}>{n}</Chip>
        {/each}
      </div>
    </div>

    <h2 class="section">{t("settings_feeds")}</h2>
    <div class="choice column">
      <span class="body-large">{t("refresh_interval")}</span>
      <div class="chips">
        {#each refreshOptions as h (h)}
          <Chip selected={settings.refreshHours === h} onclick={() => updateSettings({ refreshHours: h })}>
            {h === 0 ? t("refresh_manual") : t("hours_short", h)}
          </Chip>
        {/each}
      </div>
    </div>
    <ListItem headline={t("add_new_to_queue")} supporting={t("add_new_to_queue_summary")} onclick={() => updateSettings({ addNewToQueue: !settings.addNewToQueue })}>
      {#snippet trailing()}<Switch checked={settings.addNewToQueue} onchange={(v) => updateSettings({ addNewToQueue: v })} />{/snippet}
    </ListItem>
    <ListItem headline={t("import_opml")} supporting={t("import_opml_summary")} icon="file_download" onclick={runImport} />
    <ListItem headline={t("export_opml")} icon="file_upload" onclick={runExport} />

    <h2 class="section">{t("settings_sync")}</h2>
    <SyncSettings />

    <h2 class="section">{t("search_settings")}</h2>
    <ListItem
      headline={t("search_country")}
      supporting={settings.searchCountry ? `${countryOf(settings.searchCountry).flag} ${countryOf(settings.searchCountry).name}` : t("country_automatic", countryOf(defaultCountry()).name)}
      icon="public"
      onclick={() => (pickerOpen = true)}
    />
    <ListItem headline={t("show_top_charts")} supporting={t("show_top_charts_summary")} onclick={() => updateSettings({ showTopCharts: !settings.showTopCharts })}>
      {#snippet trailing()}<Switch checked={settings.showTopCharts} onchange={(v) => updateSettings({ showTopCharts: v })} />{/snippet}
    </ListItem>

    <h2 class="section">{t("settings_about")}</h2>
    <div class="about">
      <Wordmark height={28} />
      <span class="body-medium muted">{t("version", appVersion)}</span>
    </div>
    <ListItem
      headline={t("update_check")}
      supporting={updates.state.kind === "checking"
        ? t("update_checking")
        : updates.state.kind === "upToDate"
          ? t("update_up_to_date")
          : updates.state.kind === "error"
            ? t("update_check_error")
            : updates.state.kind === "available"
              ? t("update_versions", updates.state.release.version, appVersion)
              : t("update_check_summary")}
      icon="system_update"
      onclick={() => {
        // Con un aggiornamento già trovato si riapre la finestra per installarlo.
        if (updates.state.kind === "available") updates.dismissed = null;
        else if (updates.state.kind !== "downloading" && updates.state.kind !== "installing") void updates.check(true);
      }}
    />
    <ListItem headline={t("update_auto")} onclick={() => updateSettings({ autoCheckUpdates: !settings.autoCheckUpdates })}>
      {#snippet trailing()}<Switch checked={settings.autoCheckUpdates} onchange={(v) => updateSettings({ autoCheckUpdates: v })} />{/snippet}
    </ListItem>
    {#if sync.lastSyncAt}<p class="body-small muted note"><Icon name="cloud_done" size={14} /> {t("sync_last", formatRelative(sync.lastSyncAt))}</p>{/if}
  </div>
</div>

{#if pickerOpen}<CountryPicker onclose={() => (pickerOpen = false)} />{/if}

<style>
  .narrow {
    max-width: 820px;
  }
  .section {
    margin: 28px 16px 8px;
    font-size: 14px;
    font-weight: 500;
    color: var(--md-primary);
  }
  .choice {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 10px 16px;
    flex-wrap: wrap;
  }
  .choice.column {
    flex-direction: column;
    align-items: flex-start;
  }
  .chips {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
  .link {
    border: none;
    background: none;
    color: var(--md-primary);
    cursor: pointer;
    white-space: nowrap;
  }
  .note {
    margin: 0 16px 8px 56px;
  }
  .about {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 8px 16px 12px;
  }
</style>
