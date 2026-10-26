<script lang="ts">
  import type { Snippet } from "svelte";
  import Icon from "./Icon.svelte";

  let { selected = false, onclick, children, icon }: { selected?: boolean; onclick?: () => void; children: Snippet; icon?: string } =
    $props();
</script>

<button class="chip" class:selected {onclick}>
  {#if selected}<Icon name="check" size={18} />{:else if icon}<Icon name={icon} size={18} />{/if}
  {@render children()}
</button>

<style>
  .chip {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    height: 32px;
    padding: 0 16px;
    border-radius: 8px;
    border: 1px solid var(--md-outline-variant);
    background: transparent;
    color: var(--md-on-surface-variant);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    position: relative;
  }
  .chip:hover {
    background: color-mix(in srgb, var(--md-on-surface) 8%, transparent);
  }
  .selected {
    background: var(--md-secondary-container);
    color: var(--md-on-secondary-container);
    border-color: transparent;
    padding-left: 8px;
  }
  .selected:hover {
    background: color-mix(in srgb, var(--md-on-secondary-container) 8%, var(--md-secondary-container));
  }
</style>
