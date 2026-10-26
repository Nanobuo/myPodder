<script lang="ts">
  import { DownloadState, episodesDb, type EpisodeItem } from "../lib/db.ts";
  import { deletePlayed } from "../lib/downloads.svelte.ts";
  import { formatBytes } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import { snackbar, versions } from "../lib/store.svelte.ts";
  import { actions } from "../lib/actions.ts";
  import EpisodeRow from "../components/EpisodeRow.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";

  let items = $state<EpisodeItem[]>([]);
  let bytes = $state(0);

  $effect(() => {
    void versions.downloads;
    void versions.episodes;
    void episodesDb.downloads().then((l) => (items = l));
    void episodesDb.downloadedBytes().then((b) => (bytes = b));
  });

  async function removePlayed() {
    const n = await deletePlayed();
    snackbar.show(t("deleted_downloads", n));
  }
</script>

<div class="screen">
  <div class="screen-inner">
    <header class="top-bar">
      <h1>{t("nav_downloads")}</h1>
      {#if items.some((e) => e.completed && e.downloadState === DownloadState.DONE)}
        <IconButton icon="delete_sweep" label={t("delete_played")} onclick={removePlayed} />
      {/if}
    </header>

    {#if items.length === 0}
      <div class="empty"><Icon name="download" /><p>{t("downloads_empty")}</p></div>
    {:else}
      <div class="card storage">
        <Icon name="hard_drive" />
        <span class="body-medium">{t("storage_used", formatBytes(bytes))}</span>
      </div>
      <div class="list">
        {#each items as e (e.id)}
          <div class="item">
            <EpisodeRow item={e} />
            {#if e.downloadState === DownloadState.FAILED}
              <IconButton icon="refresh" label={t("download_failed")} onclick={() => actions.download(e.id)} />
            {/if}
            <IconButton icon="delete" label={t("delete_download")} onclick={() => actions.deleteDownload(e.id)} />
          </div>
        {/each}
      </div>
    {/if}
  </div>
</div>

<style>
  .storage {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    color: var(--md-on-surface-variant);
  }
  .list {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .item {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .item > :global(:first-child) {
    flex: 1;
    min-width: 0;
  }
</style>
