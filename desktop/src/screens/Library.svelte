<script lang="ts">
  import { app } from "../lib/app.svelte.ts";
  import { episodesDb, podcastsDb, type EpisodeItem, type PodcastWithNew } from "../lib/db.ts";
  import { t } from "../lib/i18n.ts";
  import { exportOpml, importOpml } from "../lib/opml.ts";
  import { settings, updateSettings } from "../lib/settings.svelte.ts";
  import { nav, snackbar, versions } from "../lib/store.svelte.ts";
  import Button from "../components/Button.svelte";
  import Cover from "../components/Cover.svelte";
  import EpisodeRow from "../components/EpisodeRow.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";
  import Menu from "../components/Menu.svelte";
  import Spinner from "../components/Spinner.svelte";

  let podcasts = $state<PodcastWithNew[] | null>(null);
  let latest = $state<EpisodeItem[] | null>(null);
  let scroller = $state<HTMLElement>();

  $effect(() => {
    void versions.podcasts;
    void versions.episodes;
    void podcastsDb.allWithNew().then((l) => (podcasts = l));
    void episodesDb.latest().then((l) => (latest = l));
  });

  $effect(() => {
    if (nav.reselect) scroller?.scrollTo({ top: 0, behavior: "smooth" });
  });

  export async function runImport() {
    try {
      const r = await importOpml((done, total) => snackbar.show(t("opml_importing", done, total), undefined, 60_000));
      if (!r) return snackbar.dismiss();
      snackbar.show([t("opml_imported", r.imported), r.failed ? t("opml_failed", r.failed) : ""].join(" ").trim());
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

  const badge = (n: number) => (settings.newBadge === "none" || n === 0 ? null : settings.newBadge === "count" ? String(n) : "");
</script>

<div class="screen" bind:this={scroller}>
  <div class="screen-inner">
    <header class="top-bar">
      <h1>{t("nav_library")}</h1>
      {#if app.refreshing}
        <div class="spin"><Spinner size={24} /></div>
      {:else}
        <IconButton icon="refresh" label={t("refresh")} onclick={() => app.refresh(true)} />
      {/if}
      <IconButton
        icon={settings.libraryGrid ? "view_list" : "grid_view"}
        label={settings.libraryGrid ? t("show_as_list") : t("show_as_grid")}
        onclick={() => updateSettings({ libraryGrid: !settings.libraryGrid })}
      />
      <Menu
        items={[
          { label: t("import_opml"), icon: "file_download", run: runImport },
          { label: t("export_opml"), icon: "file_upload", run: runExport },
        ]}
      />
    </header>

    <div class="tabs" role="tablist">
      {#each [t("tab_podcasts"), t("tab_episodes")] as label, i (i)}
        <button role="tab" class:active={settings.libraryTab === i} aria-selected={settings.libraryTab === i} onclick={() => updateSettings({ libraryTab: i })}>
          {label}
        </button>
      {/each}
    </div>

    {#if settings.libraryTab === 0}
      {#if podcasts == null}
        <div class="empty"><Spinner /></div>
      {:else if podcasts.length === 0}
        <div class="empty">
          <Icon name="podcasts" />
          <p>{t("library_empty")}</p>
          <div class="row">
            <Button icon="search" onclick={() => nav.navigate({ name: "search" })}>{t("find_podcasts")}</Button>
            <Button variant="outlined" icon="file_download" onclick={runImport}>{t("import_opml")}</Button>
          </div>
        </div>
      {:else if settings.libraryGrid}
        <div class="grid">
          {#each podcasts as p (p.id)}
            {@const b = badge(p.newCount)}
            <button class="tile" onclick={() => nav.navigate({ name: "podcast", id: p.id })} title={p.title}>
              <div class="art">
                <Cover src={p.imageUrl} size="fill" radius={16} />
                {#if b != null}<span class="badge" class:dot={b === ""}>{b}</span>{/if}
              </div>
              <span class="title-small clamp-2">{p.title}</span>
            </button>
          {/each}
        </div>
      {:else}
        <div class="list">
          {#each podcasts as p (p.id)}
            {@const b = badge(p.newCount)}
            <button class="list-row" onclick={() => nav.navigate({ name: "podcast", id: p.id })}>
              <Cover src={p.imageUrl} size={64} radius={12} />
              <span class="text">
                <span class="title-medium clamp-1">{p.title}</span>
                {#if p.author}<span class="body-medium muted clamp-1">{p.author}</span>{/if}
              </span>
              {#if b != null}<span class="badge inline" class:dot={b === ""}>{b}</span>{/if}
            </button>
          {/each}
        </div>
      {/if}
    {:else if latest == null}
      <div class="empty"><Spinner /></div>
    {:else if latest.length === 0}
      <div class="empty"><Icon name="inbox" /><p>{t("no_new_episodes")}</p></div>
    {:else}
      <div class="episodes">
        {#each latest as e (e.id)}<EpisodeRow item={e} />{/each}
      </div>
    {/if}
  </div>
</div>

<style>
  .spin {
    width: 40px;
    display: grid;
    place-items: center;
  }
  .tabs {
    display: flex;
    border-bottom: 1px solid var(--md-outline-variant);
    margin-bottom: 16px;
    position: sticky;
    top: 64px;
    z-index: 4;
    background: var(--md-surface);
  }
  .tabs button {
    flex: 0 0 auto;
    padding: 14px 24px;
    border: none;
    background: none;
    font-weight: 500;
    font-size: 14px;
    color: var(--md-on-surface-variant);
    cursor: pointer;
    border-bottom: 3px solid transparent;
    margin-bottom: -1px;
  }
  .tabs button:hover {
    color: var(--md-on-surface);
  }
  .tabs button.active {
    color: var(--md-primary);
    border-bottom-color: var(--md-primary);
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 20px 16px;
  }
  .tile {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 0;
    border: none;
    background: none;
    text-align: left;
    cursor: pointer;
    color: var(--md-on-surface);
  }
  .art {
    position: relative;
    transition: transform 0.15s;
  }
  .tile:hover .art {
    transform: translateY(-2px);
  }
  .tile:hover :global(.cover) {
    box-shadow: 0 6px 16px rgb(0 0 0 / 0.18);
  }
  .badge {
    position: absolute;
    top: 8px;
    right: 8px;
    min-width: 22px;
    height: 22px;
    padding: 0 7px;
    border-radius: 11px;
    background: var(--md-error);
    color: var(--md-on-error);
    font-size: 12px;
    font-weight: 600;
    display: grid;
    place-items: center;
    box-shadow: 0 0 0 2px var(--md-surface);
  }
  .badge.dot {
    min-width: 12px;
    width: 12px;
    height: 12px;
    padding: 0;
  }
  .badge.inline {
    position: static;
    box-shadow: none;
  }
  .list,
  .episodes {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .list-row {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 8px 12px;
    border: none;
    background: none;
    text-align: left;
    border-radius: 16px;
    cursor: pointer;
    color: var(--md-on-surface);
  }
  .list-row:hover {
    background: var(--md-surface-container-low);
  }
  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
</style>
