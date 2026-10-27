<script lang="ts">
  import { queueDb, type EpisodeItem } from "../lib/db.ts";
  import { t } from "../lib/i18n.ts";
  import { player } from "../lib/player.svelte.ts";
  import * as repo from "../lib/repo.ts";
  import { versions } from "../lib/store.svelte.ts";
  import Button from "../components/Button.svelte";
  import Dialog from "../components/Dialog.svelte";
  import EpisodeRow from "../components/EpisodeRow.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";

  let items = $state<EpisodeItem[]>([]);
  let confirmClear = $state(false);
  let dragIndex = $state<number | null>(null);
  let overIndex = $state<number | null>(null);

  // L'episodio caricato nel player resta in coda finché non parte: qui compare solo sopra.
  $effect(() => {
    void versions.queue;
    void versions.episodes;
    const current = player.current?.id;
    void queueDb.items().then((l) => (items = l.filter((e) => e.id !== current)));
  });

  function drop(to: number) {
    const from = dragIndex;
    dragIndex = null;
    overIndex = null;
    if (from == null || from === to) return;
    const list = [...items];
    list.splice(to, 0, list.splice(from, 1)[0]);
    items = list;
    void repo.setQueue(list.map((e) => e.id));
  }
</script>

<div class="screen">
  <div class="screen-inner">
    <header class="top-bar">
      <h1>{t("nav_queue")}</h1>
      {#if items.length}<IconButton icon="clear_all" label={t("clear_queue")} onclick={() => (confirmClear = true)} />{/if}
    </header>

    {#if player.current}
      <h3 class="section-title">{t("now_playing")}</h3>
      <EpisodeRow item={player.current} />
      <h3 class="section-title">{t("up_next")}</h3>
    {/if}

    {#if items.length === 0}
      <div class="empty"><Icon name="queue_music" /><p>{t("queue_empty")}</p></div>
    {:else}
      <div class="list">
        {#each items as e, i (e.id)}
          <div
            class="item"
            class:dragging={dragIndex === i}
            class:over={overIndex === i && dragIndex !== i}
            draggable="true"
            role="listitem"
            ondragstart={(ev) => {
              dragIndex = i;
              ev.dataTransfer?.setData("text/plain", String(e.id));
              if (ev.dataTransfer) ev.dataTransfer.effectAllowed = "move";
            }}
            ondragover={(ev) => {
              ev.preventDefault();
              overIndex = i;
            }}
            ondrop={(ev) => {
              ev.preventDefault();
              drop(i);
            }}
            ondragend={() => {
              dragIndex = null;
              overIndex = null;
            }}
          >
            <EpisodeRow item={e} draggable />
            <IconButton icon="close" label={t("remove_from_queue")} onclick={() => repo.removeFromQueue(e.id)} />
          </div>
        {/each}
      </div>
      <p class="body-small muted hint">{t("reorder")}</p>
    {/if}
  </div>
</div>

{#if confirmClear}
  <Dialog title={t("clear_queue")} onclose={() => (confirmClear = false)}>
    <p>{t("clear_queue_confirm")}</p>
    {#snippet actions()}
      <Button variant="text" onclick={() => (confirmClear = false)}>{t("cancel")}</Button>
      <Button
        onclick={() => {
          confirmClear = false;
          void repo.clearQueue();
        }}>{t("clear")}</Button
      >
    {/snippet}
  </Dialog>
{/if}

<style>
  .list {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .item {
    display: flex;
    align-items: center;
    gap: 4px;
    border-radius: 16px;
    border-top: 2px solid transparent;
  }
  .item > :global(:first-child) {
    flex: 1;
    min-width: 0;
  }
  .item.dragging {
    opacity: 0.4;
  }
  .item.over {
    border-top-color: var(--md-primary);
  }
  .hint {
    text-align: center;
    margin-top: 16px;
  }
</style>
