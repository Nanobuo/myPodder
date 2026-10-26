<script lang="ts">
  import { artworkOf, type EpisodeItem } from "../lib/db.ts";
  import { formatClock } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import { player } from "../lib/player.svelte.ts";
  import { queueDb } from "../lib/db.ts";
  import { settings } from "../lib/settings.svelte.ts";
  import { nav, versions } from "../lib/store.svelte.ts";
  import Cover from "../components/Cover.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";
  import SeekBar from "./SeekBar.svelte";
  import SleepDialog from "./SleepDialog.svelte";
  import SpeedButton from "./SpeedButton.svelte";
  import Spinner from "../components/Spinner.svelte";

  let sleepOpen = $state(false);
  let chaptersOpen = $state(true);
  let upNext = $state<EpisodeItem[]>([]);
  const item = $derived(player.current!);

  $effect(() => {
    void versions.queue;
    void queueDb.items().then((list) => (upNext = list.slice(0, 5)));
  });

  // Il capitolo corrente resta visibile nella lista.
  let chapterList = $state<HTMLElement>();
  $effect(() => {
    const i = player.chapterIndex;
    if (i >= 0 && chaptersOpen) chapterList?.querySelector(`[data-i="${i}"]`)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  });
</script>

<section class="player-view">
  <div class="top">
    <IconButton icon="keyboard_arrow_down" label={t("close")} onclick={() => (nav.playerOpen = false)} />
    <span class="label-large muted">{t("now_playing")}</span>
    <div class="spacer"></div>
    <IconButton icon="info" label={t("episode_details")} onclick={() => nav.navigate({ name: "episode", id: item.id })} />
  </div>

  <div class="body">
    <div class="main">
      <div class="art"><Cover src={player.artwork} size="fill" radius={28} /></div>
      <div class="titles">
        <h2 class="headline-small selectable">{item.title}</h2>
        <button class="podcast title-medium" onclick={() => nav.navigate({ name: "podcast", id: item.podcastId })}>{item.podcastTitle}</button>
      </div>
      <SeekBar />
      <div class="controls">
        <SpeedButton large />
        <IconButton icon="replay_{[5, 10, 30].includes(settings.skipBackSec) ? settings.skipBackSec : 10}" label={t("skip_back_seconds", settings.skipBackSec)} size={56} iconSize={32} onclick={() => player.skipBack()} />
        <button class="play" onclick={() => player.togglePlayPause()} aria-label={player.isPlaying ? t("pause") : t("play")}>
          {#if player.isBuffering && player.isPlaying}<Spinner size={40} color="var(--md-on-primary)" />{:else}<Icon name={player.isPlaying ? "pause" : "play_arrow"} fill size={44} />{/if}
        </button>
        <IconButton icon="forward_{[5, 10, 30].includes(settings.skipForwardSec) ? settings.skipForwardSec : 30}" label={t("skip_forward_seconds", settings.skipForwardSec)} size={56} iconSize={32} onclick={() => player.skipForward()} />
        <IconButton icon="bedtime" label={t("sleep_timer")} size={48} fill={player.sleep.kind !== "off"} selected={player.sleep.kind !== "off"} onclick={() => (sleepOpen = true)} />
      </div>
    </div>

    <div class="side">
      {#if player.chapters.length}
        <div class="panel">
          <button class="panel-head" onclick={() => (chaptersOpen = !chaptersOpen)}>
            <div>
              <div class="title-medium">{t("chapters")}</div>
              {#if player.chapterIndex >= 0}
                <div class="body-small muted">{t("current_chapter", player.chapterIndex + 1, player.chapters.length)}</div>
              {/if}
            </div>
            <Icon name={chaptersOpen ? "expand_less" : "expand_more"} />
          </button>
          {#if chaptersOpen}
            <div class="chapters" bind:this={chapterList}>
              {#each player.chapters as c, i (c.startMs)}
                <button class="chapter" class:active={i === player.chapterIndex} data-i={i} onclick={() => player.seekTo(c.startMs)}>
                  <span class="time">{formatClock(c.startMs)}</span>
                  <span class="clamp-2">{c.title}</span>
                </button>
              {/each}
            </div>
          {/if}
        </div>
      {/if}

      <div class="panel">
        <div class="panel-head static">
          <div class="title-medium">{t("up_next")}</div>
          <button class="link label-large" onclick={() => nav.navigate({ name: "queue" })}>{t("nav_queue")}</button>
        </div>
        {#if upNext.length === 0}
          <p class="body-small muted pad">{t("queue_empty")}</p>
        {:else}
          {#each upNext as e (e.id)}
            <button class="next" onclick={() => player.play(e.id)}>
              <Cover src={artworkOf(e)} size={40} radius={8} />
              <span class="text">
                <span class="body-medium clamp-1">{e.title}</span>
                <span class="body-small muted clamp-1">{e.podcastTitle}</span>
              </span>
              <Icon name="play_arrow" />
            </button>
          {/each}
        {/if}
      </div>
    </div>
  </div>
</section>

{#if sleepOpen}<SleepDialog onclose={() => (sleepOpen = false)} />{/if}

<style>
  .player-view {
    position: absolute;
    inset: 0;
    z-index: 20;
    display: flex;
    flex-direction: column;
    background: linear-gradient(to bottom, var(--md-surface-container-low), var(--md-surface));
    animation: up 0.22s ease-out;
    overflow: hidden;
  }
  @keyframes up {
    from {
      transform: translateY(40px);
      opacity: 0;
    }
  }
  .top {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
  }
  .spacer {
    flex: 1;
  }
  .body {
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: minmax(320px, 520px) minmax(280px, 420px);
    justify-content: center;
    gap: 48px;
    padding: 0 32px 24px;
    overflow-y: auto;
  }
  .main {
    display: flex;
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }
  .art {
    width: min(100%, 42vh);
    align-self: center;
    box-shadow: 0 8px 32px rgb(0 0 0 / 0.2);
    border-radius: 28px;
  }
  .titles {
    text-align: center;
  }
  h2 {
    margin: 0 0 4px;
  }
  .podcast {
    border: none;
    background: none;
    color: var(--md-primary);
    cursor: pointer;
    padding: 0;
  }
  .podcast:hover {
    text-decoration: underline;
  }
  .controls {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
  }
  .play {
    width: 88px;
    height: 88px;
    border-radius: 28px;
    border: none;
    display: grid;
    place-items: center;
    background: var(--md-primary);
    color: var(--md-on-primary);
    cursor: pointer;
    transition: border-radius 0.25s;
  }
  .play:hover {
    border-radius: 44px;
  }
  .side {
    display: flex;
    flex-direction: column;
    gap: 16px;
    min-height: 0;
  }
  .panel {
    background: var(--md-surface-container);
    border-radius: 24px;
    padding: 8px;
  }
  .panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding: 8px 12px;
    border: none;
    background: none;
    text-align: left;
    cursor: pointer;
    color: var(--md-on-surface);
  }
  .panel-head.static {
    cursor: default;
  }
  .link {
    border: none;
    background: none;
    color: var(--md-primary);
    cursor: pointer;
  }
  .pad {
    padding: 0 12px 8px;
    margin: 0;
  }
  .chapters {
    max-height: 320px;
    overflow-y: auto;
  }
  .chapter {
    display: flex;
    gap: 12px;
    width: 100%;
    padding: 8px 12px;
    border: none;
    background: none;
    text-align: left;
    border-radius: 12px;
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
    flex: none;
    width: 56px;
    font-variant-numeric: tabular-nums;
    color: var(--md-on-surface-variant);
  }
  .next {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 6px 12px;
    border: none;
    background: none;
    text-align: left;
    border-radius: 12px;
    cursor: pointer;
    color: var(--md-on-surface-variant);
  }
  .next:hover {
    background: color-mix(in srgb, var(--md-on-surface) 6%, transparent);
  }
  .next .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    color: var(--md-on-surface);
  }
  @media (max-width: 960px) {
    .body {
      grid-template-columns: minmax(300px, 520px);
    }
  }
</style>
