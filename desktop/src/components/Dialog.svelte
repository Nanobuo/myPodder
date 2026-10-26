<script lang="ts">
  import type { Snippet } from "svelte";
  import Icon from "./Icon.svelte";

  let {
    title,
    icon,
    onclose,
    children,
    actions,
    width = 420,
  }: { title?: string; icon?: string; onclose: () => void; children: Snippet; actions?: Snippet; width?: number } = $props();

  function onkeydown(e: KeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      onclose();
    }
  }
</script>

<svelte:window {onkeydown} />

<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
<div class="scrim" onclick={onclose}>
  <div class="dialog" role="dialog" aria-modal="true" tabindex="-1" style:max-width="{width}px" onclick={(e) => e.stopPropagation()}>
    {#if icon}<div class="icon"><Icon name={icon} /></div>{/if}
    {#if title}<h2 class:centered={!!icon}>{title}</h2>{/if}
    <div class="content">{@render children()}</div>
    {#if actions}<div class="actions">{@render actions()}</div>{/if}
  </div>
</div>

<style>
  .scrim {
    position: fixed;
    inset: 0;
    background: rgb(0 0 0 / 0.32);
    display: grid;
    place-items: center;
    z-index: 100;
    animation: fade 0.15s ease-out;
  }
  .dialog {
    width: calc(100% - 48px);
    max-height: calc(100% - 48px);
    display: flex;
    flex-direction: column;
    background: var(--md-surface-container-high);
    color: var(--md-on-surface);
    border-radius: 28px;
    padding: 24px;
    box-shadow: 0 8px 24px rgb(0 0 0 / 0.25);
    animation: pop 0.18s ease-out;
  }
  .icon {
    text-align: center;
    color: var(--md-secondary);
    margin-bottom: 16px;
  }
  h2 {
    margin: 0 0 16px;
    font-size: 24px;
    line-height: 32px;
    font-weight: 400;
  }
  h2.centered {
    text-align: center;
  }
  .content {
    color: var(--md-on-surface-variant);
    overflow-y: auto;
    min-height: 0;
  }
  .actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 24px;
    flex-wrap: wrap;
  }
  @keyframes fade {
    from { opacity: 0; }
  }
  @keyframes pop {
    from { opacity: 0; transform: scale(0.95); }
  }
</style>
