<script lang="ts">
  import { t } from "../lib/i18n.ts";
  import { player } from "../lib/player.svelte.ts";
  import { settings } from "../lib/settings.svelte.ts";
  import { nav } from "../lib/store.svelte.ts";
  import Cover from "../components/Cover.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";
  import SeekBar from "./SeekBar.svelte";
  import SleepDialog from "./SleepDialog.svelte";
  import SpeedButton from "./SpeedButton.svelte";
  import Spinner from "../components/Spinner.svelte";
  import CountBadge from "../components/CountBadge.svelte";
  import { queueCount } from "../lib/live.svelte.ts";

  let sleepOpen = $state(false);
  const item = $derived(player.current);
</script>

<footer class="bar" class:empty={!item}>
  {#if item}
    <button class="info" onclick={() => (nav.playerOpen = !nav.playerOpen)} title={t("open_player")}>
      <Cover src={player.artwork} size={56} radius={10} />
      <div class="text">
        <div class="title-small clamp-1">{item.title}</div>
        <div class="body-small muted clamp-1">
          {[player.currentChapter?.title, item.podcastTitle].filter(Boolean).join(" · ")}
        </div>
      </div>
    </button>

    <div class="center">
      <div class="controls">
        <IconButton icon="replay_{[5, 10, 30].includes(settings.skipBackSec) ? settings.skipBackSec : 10}" label={t("skip_back_seconds", settings.skipBackSec)} onclick={() => player.skipBack()} />
        <button class="play" onclick={() => player.togglePlayPause()} aria-label={player.isPlaying ? t("pause") : t("play")} title={player.isPlaying ? t("pause") : t("play")}>
          {#if player.isBuffering && player.isPlaying}
            <Spinner size={28} color="var(--md-on-primary)" />
          {:else}
            <Icon name={player.isPlaying ? "pause" : "play_arrow"} fill size={30} />
          {/if}
        </button>
        <IconButton icon="forward_{[5, 10, 30].includes(settings.skipForwardSec) ? settings.skipForwardSec : 30}" label={t("skip_forward_seconds", settings.skipForwardSec)} onclick={() => player.skipForward()} />
      </div>
      <SeekBar compact />
    </div>

    <div class="right">
      <SpeedButton />
      <IconButton
        icon="bedtime"
        label={t("sleep_timer")}
        fill={player.sleep.kind !== "off"}
        selected={player.sleep.kind !== "off"}
        onclick={() => (sleepOpen = true)}
      />
      <span class="badged">
        <IconButton icon="queue_music" label={t("nav_queue")} onclick={() => nav.navigate({ name: "queue" })} />
        <span class="badge-pos"><CountBadge count={queueCount(player.current?.id)} /></span>
      </span>
      <div class="volume">
        <IconButton
          icon={player.volume === 0 ? "volume_off" : player.volume < 0.5 ? "volume_down" : "volume_up"}
          label={t("volume")}
          onclick={() => player.setVolume(player.volume > 0 ? 0 : 1)}
        />
        <input type="range" min="0" max="1" step="0.01" value={player.volume} oninput={(e) => player.setVolume(Number(e.currentTarget.value))} aria-label={t("volume")} />
      </div>
      <IconButton
        icon={nav.playerOpen ? "keyboard_arrow_down" : "keyboard_arrow_up"}
        label={t("open_player")}
        onclick={() => (nav.playerOpen = !nav.playerOpen)}
      />
    </div>
  {:else}
    <div class="placeholder body-medium muted"><Icon name="podcasts" /> {t("nothing_playing")}</div>
  {/if}
</footer>

{#if sleepOpen}<SleepDialog onclose={() => (sleepOpen = false)} />{/if}

<style>
  .bar {
    grid-area: player;
    display: grid;
    grid-template-columns: minmax(200px, 1fr) minmax(300px, 1.4fr) minmax(200px, 1fr);
    align-items: center;
    gap: 16px;
    height: 88px;
    padding: 0 16px;
    background: var(--md-surface-container);
  }
  .bar.empty {
    display: flex;
    height: 56px;
  }
  .placeholder {
    display: flex;
    align-items: center;
    gap: 12px;
    padding-left: 24px;
  }
  .info {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
    padding: 6px;
    border: none;
    background: none;
    text-align: left;
    border-radius: 14px;
    cursor: pointer;
  }
  .info:hover {
    background: color-mix(in srgb, var(--md-on-surface) 6%, transparent);
  }
  .text {
    min-width: 0;
  }
  .center {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
  }
  .controls {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .play {
    width: 48px;
    height: 48px;
    border-radius: 16px;
    border: none;
    display: grid;
    place-items: center;
    background: var(--md-primary);
    color: var(--md-on-primary);
    cursor: pointer;
    transition: border-radius 0.2s;
  }
  .play:hover {
    border-radius: 24px;
  }
  .right {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 4px;
  }
  .badged {
    position: relative;
    display: inline-flex;
  }
  .badge-pos {
    position: absolute;
    top: 8px;
    right: 8px;
  }
  .volume {
    display: flex;
    align-items: center;
  }
  .volume input {
    width: 80px;
    accent-color: var(--md-primary);
  }
  @media (max-width: 1000px) {
    .volume input {
      display: none;
    }
  }
</style>
