<script lang="ts">
  import { openUrl } from "@tauri-apps/plugin-opener";
  import { actions } from "../lib/actions.ts";
  import { ensureChapters } from "../lib/chapterLoader.ts";
  import { artworkOf, chaptersDb, DownloadState, episodesDb, type Chapter, type EpisodeItem } from "../lib/db.ts";
  import { progress } from "../lib/downloads.svelte.ts";
  import { formatClock, formatDate, formatDuration } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import { live } from "../lib/live.svelte.ts";
  import { player } from "../lib/player.svelte.ts";
  import { nav, versions } from "../lib/store.svelte.ts";
  import Button from "../components/Button.svelte";
  import Cover from "../components/Cover.svelte";
  import HtmlDescription from "../components/HtmlDescription.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";
  import Spinner from "../components/Spinner.svelte";

  let { id }: { id: number } = $props();
  let item = $state<EpisodeItem | null>(null);
  let chapters = $state<Chapter[]>([]);
  let chaptersOpen = $state(true);

  $effect(() => {
    void versions.episodes;
    void episodesDb.item(id).then((e) => (item = e));
  });
  $effect(() => {
    void ensureChapters(id).then(() => chaptersDb.get(id)).then((c) => (chapters = c));
  });

  const isCurrent = $derived(player.current?.id === id);
  const playing = $derived(isCurrent && player.isPlaying);
  const position = $derived(isCurrent ? player.position : (item?.positionMs ?? 0));
  const inQueue = $derived(live.queueIds.includes(id));
  const currentChapter = $derived(isCurrent ? player.chapterIndex : -1);

  const playLabel = $derived.by(() => {
    if (playing) return t("pause");
    if (item && !item.completed && position > 0) return t("resume_from", formatClock(position));
    return t("play");
  });
</script>

<div class="screen">
  <div class="screen-inner narrow">
    <header class="top-bar">
      <IconButton icon="arrow_back" label={t("back")} onclick={() => nav.back()} />
      <h1>{t("episode_details")}</h1>
    </header>

    {#if !item}
      <div class="empty"><Spinner /></div>
    {:else}
      <section class="hero">
        <Cover src={artworkOf(item)} size={160} radius={20} />
        <div class="info">
          <button class="podcast title-medium" onclick={() => nav.navigate({ name: "podcast", id: item!.podcastId })}>{item.podcastTitle}</button>
          <h2 class="headline-small selectable">{item.title}</h2>
          <p class="body-medium muted">
            {[formatDate(item.pubDate), item.durationMs > 0 ? formatDuration(item.durationMs) : "", item.completed ? t("played") : ""]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </div>
      </section>

      <div class="row actions">
        <Button icon={playing ? "pause" : "play_arrow"} onclick={() => actions.togglePlay(id)}>{playLabel}</Button>
        <IconButton
          variant="tonal"
          icon={inQueue ? "playlist_remove" : "playlist_add"}
          label={inQueue ? t("remove_from_queue") : t("add_to_queue")}
          onclick={() => (inQueue ? actions.removeFromQueue(id) : actions.addToQueue(id))}
        />
        <IconButton variant="tonal" icon="queue_play_next" label={t("play_next")} onclick={() => actions.playNext(id)} />
        {#if item.downloadState === DownloadState.DONE}
          <IconButton variant="tonal" icon="delete" label={t("delete_download")} onclick={() => actions.deleteDownload(id)} />
        {:else if item.downloadState === DownloadState.QUEUED || item.downloadState === DownloadState.DOWNLOADING}
          <IconButton variant="tonal" icon="close" label={`${t("downloading")} ${progress[id] ?? 0}%`} onclick={() => actions.deleteDownload(id)} />
          <span class="body-small muted">{progress[id] ?? 0}%</span>
        {:else}
          <IconButton
            variant="tonal"
            icon={item.downloadState === DownloadState.FAILED ? "sync_problem" : "download"}
            label={item.downloadState === DownloadState.FAILED ? t("download_failed") : t("download")}
            onclick={() => actions.download(id)}
          />
        {/if}
        <IconButton
          variant="tonal"
          icon={item.completed ? "remove_done" : "done_all"}
          label={item.completed ? t("mark_unplayed") : t("mark_played")}
          onclick={() => actions.setPlayed(id, !item!.completed)}
        />
        {#if item.link}
          <IconButton variant="tonal" icon="open_in_new" label={t("open_website")} onclick={() => openUrl(item!.link!)} />
        {/if}
      </div>

      {#if chapters.length}
        <section class="card chapters">
          <button class="head" onclick={() => (chaptersOpen = !chaptersOpen)}>
            <span class="title-medium">{t("chapters")}</span>
            <span class="body-small muted">{chapters.length}</span>
            <span class="spacer"></span>
            <Icon name={chaptersOpen ? "expand_less" : "expand_more"} />
          </button>
          {#if chaptersOpen}
            {#each chapters as c, i (c.startMs)}
              <button class="chapter" class:active={i === currentChapter} onclick={() => actions.play(id, c.startMs)}>
                <span class="time">{formatClock(c.startMs)}</span>
                <span>{c.title}</span>
              </button>
            {/each}
          {/if}
        </section>
      {/if}

      <h3 class="section-title">{t("description")}</h3>
      <HtmlDescription html={item.description} onTimestamp={(ms) => actions.play(id, ms)} />
    {/if}
  </div>
</div>

<style>
  .narrow {
    max-width: 860px;
  }
  .hero {
    display: flex;
    gap: 24px;
    align-items: center;
    margin: 8px 0 20px;
  }
  .info {
    min-width: 0;
  }
  .podcast {
    border: none;
    background: none;
    color: var(--md-primary);
    padding: 0;
    cursor: pointer;
    text-align: left;
  }
  .podcast:hover {
    text-decoration: underline;
  }
  h2 {
    margin: 4px 0;
  }
  .info p {
    margin: 0;
  }
  .actions {
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 16px;
  }
  .chapters {
    padding: 8px;
    margin-top: 8px;
  }
  .head {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 8px 12px;
    border: none;
    background: none;
    cursor: pointer;
    color: var(--md-on-surface);
  }
  .chapter {
    display: flex;
    gap: 16px;
    width: 100%;
    padding: 8px 12px;
    border: none;
    background: none;
    border-radius: 12px;
    text-align: left;
    cursor: pointer;
    color: var(--md-on-surface);
  }
  .chapter:hover {
    background: color-mix(in srgb, var(--md-on-surface) 6%, transparent);
  }
  .chapter.active {
    background: var(--md-secondary-container);
    color: var(--md-on-secondary-container);
    font-weight: 500;
  }
  .time {
    width: 64px;
    flex: none;
    color: var(--md-on-surface-variant);
    font-variant-numeric: tabular-nums;
  }
</style>
