<script lang="ts">
  import { formatClock } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import { player } from "../lib/player.svelte.ts";
  import { settings, updateSettings } from "../lib/settings.svelte.ts";
  import Slider from "../components/Slider.svelte";

  let { compact = false }: { compact?: boolean } = $props();
  let preview = $state<number | null>(null);
  const shown = $derived(preview ?? player.position);
  const total = $derived(player.duration);
  const right = $derived(
    total <= 0 ? "--:--" : settings.showRemaining ? `-${formatClock(Math.max(0, total - shown))}` : formatClock(total),
  );
</script>

<div class="seek" class:compact>
  {#if compact}<span class="time">{formatClock(shown)}</span>{/if}
  <div class="slider">
    <Slider
      value={player.position}
      max={total}
      step={1000}
      label={t("now_playing")}
      disabled={!player.current || total <= 0}
      oninput={(v) => (preview = v)}
      onchange={(v) => player.seekTo(v)}
    />
  </div>
  {#if compact}
    <button class="time toggle" onclick={() => updateSettings({ showRemaining: !settings.showRemaining })}>{right}</button>
  {:else}
    <div class="times">
      <span class="time">{formatClock(shown)}</span>
      <button class="time toggle" onclick={() => updateSettings({ showRemaining: !settings.showRemaining })}>{right}</button>
    </div>
  {/if}
</div>

<style>
  .seek {
    width: 100%;
  }
  .seek.compact {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .slider {
    flex: 1;
    min-width: 0;
  }
  .times {
    display: flex;
    justify-content: space-between;
    margin-top: 4px;
  }
  .time {
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    color: var(--md-on-surface-variant);
    min-width: 48px;
  }
  .toggle {
    border: none;
    background: none;
    padding: 0;
    cursor: pointer;
    text-align: right;
  }
  .toggle:hover {
    color: var(--md-on-surface);
  }
</style>
