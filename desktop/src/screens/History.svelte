<script lang="ts">
  import { episodesDb, type EpisodeItem } from "../lib/db.ts";
  import { formatDay } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import * as repo from "../lib/repo.ts";
  import { versions } from "../lib/store.svelte.ts";
  import Button from "../components/Button.svelte";
  import Dialog from "../components/Dialog.svelte";
  import EpisodeRow from "../components/EpisodeRow.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";

  let items = $state<EpisodeItem[]>([]);
  let confirmClear = $state(false);

  $effect(() => {
    void versions.episodes;
    void episodesDb.history().then((l) => (items = l));
  });

  const groups = $derived.by(() => {
    const out: { day: string; items: EpisodeItem[] }[] = [];
    for (const e of items) {
      const day = formatDay(e.lastPlayedAt);
      if (out.at(-1)?.day === day) out.at(-1)!.items.push(e);
      else out.push({ day, items: [e] });
    }
    return out;
  });
</script>

<div class="screen">
  <div class="screen-inner">
    <header class="top-bar">
      <h1>{t("history")}</h1>
      {#if items.length}<IconButton icon="delete_history" label={t("history_clear")} onclick={() => (confirmClear = true)} />{/if}
    </header>

    {#if items.length === 0}
      <div class="empty"><Icon name="history" /><p>{t("history_empty")}</p></div>
    {:else}
      {#each groups as g (g.day)}
        <h3 class="section-title day">{g.day}</h3>
        {#each g.items as e (e.id)}<EpisodeRow item={e} />{/each}
      {/each}
    {/if}
  </div>
</div>

{#if confirmClear}
  <Dialog title={t("history_clear")} onclose={() => (confirmClear = false)}>
    <p>{t("history_clear_confirm")}</p>
    {#snippet actions()}
      <Button variant="text" onclick={() => (confirmClear = false)}>{t("cancel")}</Button>
      <Button
        onclick={() => {
          confirmClear = false;
          void repo.clearHistory();
        }}>{t("clear")}</Button
      >
    {/snippet}
  </Dialog>
{/if}

<style>
  .day::first-letter {
    text-transform: uppercase;
  }
</style>
