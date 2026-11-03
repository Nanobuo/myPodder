<script lang="ts">
  import { queueDb, type EpisodeItem } from "../lib/db.ts";
  import { formatDuration } from "../lib/format.ts";
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
  let listEl = $state<HTMLElement>();

  // L'episodio caricato nel player resta in coda finché non parte: qui compare solo sopra.
  $effect(() => {
    void versions.queue;
    void versions.episodes;
    const current = player.current?.id;
    void queueDb.items().then((l) => {
      if (drag == null) items = l.filter((e) => e.id !== current);
    });
  });

  /** Tempo che resta da ascoltare in coda. */
  const totalMs = $derived(items.reduce((sum, e) => sum + Math.max(0, e.durationMs - e.positionMs), 0));

  async function save(list: EpisodeItem[]) {
    items = list;
    // L'episodio nel player, se non è ancora partito, resta in testa alla coda.
    const current = player.current?.id;
    const keepCurrent = current != null && (await queueDb.ids()).includes(current);
    await repo.setQueue([...(keepCurrent ? [current] : []), ...list.map((e) => e.id)]);
  }

  function move(from: number, to: number) {
    if (to < 0 || to >= items.length || from === to) return;
    const list = [...items];
    list.splice(to, 0, list.splice(from, 1)[0]);
    void save(list);
  }

  // Trascinamento con il puntatore (il drag & drop HTML non funziona nelle finestre dell'app).
  let drag = $state<{ index: number; target: number; offset: number; height: number } | null>(null);
  let startY = 0;
  let mids: number[] = [];

  function startDrag(index: number, e: PointerEvent) {
    if (e.button !== 0 || !listEl) return;
    const rows = [...listEl.querySelectorAll<HTMLElement>(":scope > .item")];
    const rects = rows.map((r) => r.getBoundingClientRect());
    mids = rects.map((r) => r.top + r.height / 2);
    startY = e.clientY;
    drag = { index, target: index, offset: 0, height: rects[index].height + 2 };
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", endDrag);
    window.addEventListener("pointercancel", cancelDrag);
  }

  function onMove(e: PointerEvent) {
    if (!drag) return;
    const offset = e.clientY - startY;
    const center = mids[drag.index] + offset;
    let target = drag.index;
    while (target < mids.length - 1 && center > mids[target + 1]) target++;
    while (target > 0 && center < mids[target - 1]) target--;
    drag = { ...drag, offset, target };
  }

  function stopListening() {
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", endDrag);
    window.removeEventListener("pointercancel", cancelDrag);
  }

  function endDrag() {
    stopListening();
    const d = drag;
    drag = null;
    if (d) move(d.index, d.target);
  }

  function cancelDrag() {
    stopListening();
    drag = null;
  }

  /** Spostamento delle altre righe per fare posto a quella trascinata. */
  function shift(i: number): number {
    if (!drag || i === drag.index) return 0;
    if (drag.index < i && i <= drag.target) return -drag.height;
    if (drag.target <= i && i < drag.index) return drag.height;
    return 0;
  }

  function onkeydown(e: KeyboardEvent, i: number) {
    if (!e.altKey || (e.key !== "ArrowUp" && e.key !== "ArrowDown")) return;
    e.preventDefault();
    const to = e.key === "ArrowUp" ? i - 1 : i + 1;
    move(i, to);
    // Il focus segue la riga spostata.
    requestAnimationFrame(() => listEl?.querySelectorAll<HTMLElement>(":scope > .item")[to]?.focus());
  }
</script>

<div class="screen">
  <div class="screen-inner">
    <header class="top-bar">
      <div class="title">
        <h1>{t("nav_queue")}</h1>
        {#if items.length}
          <div class="body-small muted">
            {t("episodes_count", items.length)}{totalMs > 0 ? ` · ${formatDuration(totalMs)}` : ""}
          </div>
        {/if}
      </div>
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
      <div class="list" class:dragging={drag != null} bind:this={listEl}>
        {#each items as e, i (e.id)}
          <!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
          <div
            class="item"
            class:lifted={drag?.index === i}
            style:transform={drag?.index === i ? `translateY(${drag.offset}px)` : shift(i) ? `translateY(${shift(i)}px)` : undefined}
            role="listitem"
            tabindex="0"
            onkeydown={(ev) => onkeydown(ev, i)}
          >
            <EpisodeRow item={e} ondragstart={(ev) => startDrag(i, ev)} />
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
  .title {
    flex: 1;
    min-width: 0;
  }
  .item {
    display: flex;
    align-items: center;
    gap: 4px;
    border-radius: 16px;
    background: var(--md-surface);
    position: relative;
  }
  .item:focus-visible {
    outline: 2px solid var(--md-primary);
    outline-offset: -2px;
  }
  .list.dragging .item {
    transition: transform 0.2s cubic-bezier(0.2, 0, 0, 1);
  }
  .list.dragging .item.lifted {
    transition: box-shadow 0.2s;
    z-index: 2;
    background: var(--md-surface-container);
    box-shadow: 0 6px 20px rgb(0 0 0 / 0.25);
  }
  .item > :global(:first-child) {
    flex: 1;
    min-width: 0;
  }
  .hint {
    text-align: center;
    margin-top: 16px;
  }
</style>
