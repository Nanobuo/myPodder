<script lang="ts">
  import { artworkOf, DownloadState, type EpisodeItem } from "../lib/db.ts";
  import { actions } from "../lib/actions.ts";
  import { progress } from "../lib/downloads.svelte.ts";
  import { formatDate, formatDuration, stripHtml } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import { live } from "../lib/live.svelte.ts";
  import { player } from "../lib/player.svelte.ts";
  import { nav } from "../lib/store.svelte.ts";
  import Cover from "./Cover.svelte";
  import Icon from "./Icon.svelte";
  import Menu, { type MenuItem } from "./Menu.svelte";

  let {
    item,
    showPodcast = true,
    dimPlayed = false,
    showDescription = false,
    draggable = false,
  }: { item: EpisodeItem; showPodcast?: boolean; dimPlayed?: boolean; showDescription?: boolean; draggable?: boolean } = $props();

  const isCurrent = $derived(player.current?.id === item.id);
  const playing = $derived(isCurrent && player.isPlaying);
  const position = $derived(isCurrent ? player.position : item.positionMs);
  const duration = $derived(isCurrent && player.duration ? player.duration : item.durationMs);
  const fraction = $derived(!item.completed && duration > 0 && position > 0 ? Math.min(1, position / duration) : 0);
  const inQueue = $derived(live.queueIds.includes(item.id));
  const dl = $derived(progress[item.id]);

  const meta = $derived.by(() => {
    const parts = [formatDate(item.pubDate)];
    if (item.completed) parts.push(t("played"));
    else if (position > 0 && duration > 0) parts.push(t("remaining", formatDuration(duration - position)));
    else if (duration > 0) parts.push(formatDuration(duration));
    return parts.filter(Boolean).join(" · ");
  });

  const menu = $derived.by((): MenuItem[] => {
    const id = item.id;
    const list: MenuItem[] = [];
    if (inQueue) list.push({ label: t("remove_from_queue"), icon: "playlist_remove", run: () => void actions.removeFromQueue(id) });
    else {
      list.push({ label: t("play_next"), icon: "queue_play_next", run: () => void actions.playNext(id) });
      list.push({ label: t("add_to_queue"), icon: "playlist_add", run: () => void actions.addToQueue(id) });
    }
    if (item.downloadState === DownloadState.NONE || item.downloadState === DownloadState.FAILED) {
      list.push({ label: t("download"), icon: "download", run: () => void actions.download(id) });
    } else {
      list.push({ label: t("delete_download"), icon: "delete", run: () => void actions.deleteDownload(id) });
    }
    list.push(
      item.completed
        ? { label: t("mark_unplayed"), icon: "remove_done", run: () => void actions.setPlayed(id, false) }
        : { label: t("mark_played"), icon: "done_all", run: () => void actions.setPlayed(id, true) },
    );
    if (showPodcast) list.push({ label: item.podcastTitle, icon: "podcasts", run: () => nav.navigate({ name: "podcast", id: item.podcastId }) });
    return list;
  });
</script>

<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
<div
  class="episode-row"
  class:dim={dimPlayed && item.completed}
  class:current={isCurrent}
  onclick={() => nav.navigate({ name: "episode", id: item.id })}
  ondblclick={(e) => {
    e.stopPropagation();
    actions.togglePlay(item.id);
  }}
>
  {#if draggable}<span class="handle" title={t("reorder")}><Icon name="drag_indicator" /></span>{/if}
  <Cover src={artworkOf(item)} size={64} radius={10} />
  <div class="text">
    {#if showPodcast}<div class="podcast label-medium muted clamp-1">{item.podcastTitle}</div>{/if}
    <div class="title title-small clamp-2">
      {#if item.isNew && !item.completed}<span class="new-dot" aria-hidden="true"></span>{/if}{item.title}
    </div>
    {#if showDescription && item.description}
      <div class="desc body-small muted clamp-2">{stripHtml(item.description)}</div>
    {/if}
    <div class="meta body-small muted">
      {#if inQueue}<Icon name="queue_music" size={14} />{/if}
      {#if item.downloadState === DownloadState.DONE}
        <Icon name="download_done" size={14} />
      {:else if item.downloadState === DownloadState.QUEUED || item.downloadState === DownloadState.DOWNLOADING}
        <Icon name="downloading" size={14} />{dl != null ? `${dl}% ·` : ""}
      {:else if item.downloadState === DownloadState.FAILED}
        <span class="error"><Icon name="error" size={14} /></span>
      {/if}
      <span>{meta}</span>
    </div>
    {#if fraction > 0}<div class="bar"><div style:width="{fraction * 100}%"></div></div>{/if}
  </div>
  <button
    class="play"
    class:active={isCurrent}
    title={playing ? t("pause") : t("play")}
    aria-label={playing ? t("pause") : t("play")}
    onclick={(e) => {
      e.stopPropagation();
      actions.togglePlay(item.id);
    }}
  >
    <Icon name={playing ? "pause" : "play_arrow"} fill />
  </button>
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
  <div onclick={(e) => e.stopPropagation()}><Menu items={menu} /></div>
</div>

<style>
  .episode-row {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 10px 12px;
    border-radius: 16px;
    cursor: pointer;
    transition: background-color 0.12s;
  }
  .episode-row:hover {
    background: var(--md-surface-container-low);
  }
  .episode-row.current {
    background: color-mix(in srgb, var(--md-primary) 8%, transparent);
  }
  .dim {
    opacity: 0.55;
  }
  .handle {
    color: var(--md-on-surface-variant);
    cursor: grab;
    display: flex;
  }
  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .title {
    font-size: 15px;
  }
  .new-dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--md-primary);
    margin-right: 6px;
    vertical-align: middle;
  }
  .meta {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .bar {
    height: 4px;
    border-radius: 2px;
    background: var(--md-surface-container-highest);
    overflow: hidden;
    margin-top: 4px;
    max-width: 240px;
  }
  .bar div {
    height: 100%;
    background: var(--md-primary);
  }
  .play {
    flex: none;
    width: 44px;
    height: 44px;
    border-radius: 50%;
    border: none;
    display: grid;
    place-items: center;
    cursor: pointer;
    background: var(--md-secondary-container);
    color: var(--md-on-secondary-container);
    transition: border-radius 0.2s, background-color 0.2s;
  }
  .play.active {
    background: var(--md-primary);
    color: var(--md-on-primary);
    border-radius: 14px;
  }
  .play:hover {
    filter: brightness(1.05);
  }
</style>
