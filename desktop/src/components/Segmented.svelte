<script lang="ts" generics="T">
  import Icon from "./Icon.svelte";

  let { options, selected, label, onselect }: { options: T[]; selected: T; label: (v: T) => string; onselect: (v: T) => void } =
    $props();
</script>

<div class="segmented" role="radiogroup">
  {#each options as option, i (i)}
    <button class:selected={option === selected} role="radio" aria-checked={option === selected} onclick={() => onselect(option)}>
      {#if option === selected}<Icon name="check" size={18} />{/if}
      {label(option)}
    </button>
  {/each}
</div>

<style>
  .segmented {
    display: inline-flex;
    border: 1px solid var(--md-outline);
    border-radius: 20px;
    overflow: hidden;
  }
  button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    height: 38px;
    padding: 0 16px;
    border: none;
    border-left: 1px solid var(--md-outline);
    background: none;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    color: var(--md-on-surface);
    white-space: nowrap;
  }
  button:first-child {
    border-left: none;
  }
  button:hover {
    background: color-mix(in srgb, var(--md-on-surface) 8%, transparent);
  }
  .selected {
    background: var(--md-secondary-container);
    color: var(--md-on-secondary-container);
  }
  .selected:hover {
    background: color-mix(in srgb, var(--md-on-secondary-container) 8%, var(--md-secondary-container));
  }
</style>
