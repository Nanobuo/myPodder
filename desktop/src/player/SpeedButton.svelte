<script lang="ts">
  import { t } from "../lib/i18n.ts";
  import { player } from "../lib/player.svelte.ts";

  let { large = false }: { large?: boolean } = $props();
  const speeds = [0.75, 0.9, 1, 1.1, 1.2, 1.25, 1.3, 1.5, 1.75, 2, 2.5, 3];
  let open = $state(false);
  let anchor: HTMLButtonElement;
  let pos = $state({ left: 0, top: 0 });

  const label = (s: number) => `${s.toLocaleString(undefined, { maximumFractionDigits: 2 })}×`;

  function toggle(e: MouseEvent) {
    e.stopPropagation();
    const r = anchor.getBoundingClientRect();
    pos = { left: r.left + r.width / 2, top: r.top };
    open = !open;
  }
</script>

<svelte:window onclick={() => (open = false)} onkeydown={(e) => e.key === "Escape" && (open = false)} />

<button class="speed" class:large bind:this={anchor} onclick={toggle} title={t("playback_speed")} aria-label={t("playback_speed")}>
  {label(player.speed)}
</button>

{#if open}
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
  <div class="popup" style:left="{pos.left}px" style:top="{pos.top}px" onclick={(e) => e.stopPropagation()}>
    <div class="title-small">{t("playback_speed")}</div>
    <div class="current headline-small">{label(player.speed)}</div>
    <input
      type="range"
      min="0.5"
      max="3"
      step="0.05"
      value={player.speed}
      oninput={(e) => player.setSpeed(Number(e.currentTarget.value))}
      aria-label={t("playback_speed")}
    />
    <div class="grid">
      {#each speeds as s (s)}
        <button class:selected={Math.abs(player.speed - s) < 0.001} onclick={() => player.setSpeed(s)}>{label(s)}</button>
      {/each}
    </div>
  </div>
{/if}

<style>
  .speed {
    min-width: 48px;
    height: 32px;
    padding: 0 10px;
    border-radius: 16px;
    border: 1px solid var(--md-outline-variant);
    background: none;
    cursor: pointer;
    font-weight: 600;
    font-size: 13px;
    color: var(--md-on-surface-variant);
    font-variant-numeric: tabular-nums;
  }
  .speed.large {
    height: 40px;
    min-width: 64px;
    border-radius: 20px;
    font-size: 15px;
  }
  .speed:hover {
    background: color-mix(in srgb, var(--md-on-surface) 8%, transparent);
  }
  .popup {
    position: fixed;
    transform: translate(-50%, calc(-100% - 12px));
    width: 280px;
    padding: 16px;
    border-radius: 16px;
    background: var(--md-surface-container-high);
    box-shadow: 0 6px 20px rgb(0 0 0 / 0.25);
    z-index: 95;
    text-align: center;
  }
  .current {
    margin: 4px 0;
    color: var(--md-primary);
    font-variant-numeric: tabular-nums;
  }
  input {
    width: 100%;
    accent-color: var(--md-primary);
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 6px;
    margin-top: 8px;
  }
  .grid button {
    height: 32px;
    border-radius: 8px;
    border: 1px solid var(--md-outline-variant);
    background: none;
    cursor: pointer;
    font-size: 13px;
    color: var(--md-on-surface);
  }
  .grid button:hover {
    background: color-mix(in srgb, var(--md-on-surface) 8%, transparent);
  }
  .grid button.selected {
    background: var(--md-secondary-container);
    color: var(--md-on-secondary-container);
    border-color: transparent;
  }
</style>
