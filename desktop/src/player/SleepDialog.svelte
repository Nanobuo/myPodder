<script lang="ts">
  import { formatClock } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import { player } from "../lib/player.svelte.ts";
  import { settings } from "../lib/settings.svelte.ts";
  import Button from "../components/Button.svelte";
  import Chip from "../components/Chip.svelte";
  import Dialog from "../components/Dialog.svelte";

  let { onclose }: { onclose: () => void } = $props();
  const presets = [5, 10, 15, 30, 45, 60, 90];
  let custom = $state(settings.sleepMinutes);
  let now = $state(Date.now());
  $effect(() => {
    const i = setInterval(() => (now = Date.now()), 1000);
    return () => clearInterval(i);
  });
</script>

<Dialog title={t("sleep_timer")} icon="bedtime" {onclose}>
  {#if player.sleep.kind === "timer"}
    <p class="remaining title-large">{t("sleep_remaining", formatClock(player.sleep.endsAt - now))}</p>
    <p class="label-large">{t("sleep_extend_by")}</p>
    <div class="chips">
      {#each [5, 10, 15, 30] as m (m)}
        <Chip icon="add" onclick={() => player.extendSleep(m)}>{t("minutes_short", m)}</Chip>
      {/each}
    </div>
  {:else if player.sleep.kind === "endOfEpisode"}
    <p>{t("sleep_end_active")}</p>
  {:else}
    <p class="label-large">{t("sleep_durations")}</p>
    <div class="chips">
      {#each presets as m (m)}
        <Chip
          onclick={() => {
            player.startSleep(m);
            onclose();
          }}>{t("minutes_short", m)}</Chip
        >
      {/each}
      <Chip
        icon="last_page"
        onclick={() => {
          player.sleepAtEndOfEpisode();
          onclose();
        }}>{t("sleep_end_of_episode")}</Chip
      >
    </div>
    <p class="label-large">{t("sleep_custom")}</p>
    <div class="custom">
      <input type="range" min="1" max="180" bind:value={custom} aria-label={t("sleep_custom")} />
      <span class="title-medium value">{t("minutes_short", custom)}</span>
    </div>
  {/if}
  {#snippet actions()}
    {#if player.sleep.kind !== "off"}
      <Button
        variant="text"
        onclick={() => {
          player.cancelSleep();
          onclose();
        }}>{t("sleep_cancel")}</Button
      >
      <Button variant="tonal" onclick={onclose}>{t("close")}</Button>
    {:else}
      <Button variant="text" onclick={onclose}>{t("cancel")}</Button>
      <Button
        onclick={() => {
          player.startSleep(custom);
          onclose();
        }}>{t("sleep_start")}</Button
      >
    {/if}
  {/snippet}
</Dialog>

<style>
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 16px;
  }
  .remaining {
    color: var(--md-on-surface);
    font-variant-numeric: tabular-nums;
  }
  .custom {
    display: flex;
    align-items: center;
    gap: 16px;
  }
  .custom input {
    flex: 1;
    accent-color: var(--md-primary);
  }
  .value {
    min-width: 70px;
    text-align: right;
    color: var(--md-on-surface);
  }
</style>
