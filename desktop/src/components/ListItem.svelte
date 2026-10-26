<script lang="ts">
  import type { Snippet } from "svelte";
  import Icon from "./Icon.svelte";

  let {
    headline,
    supporting,
    icon,
    onclick,
    trailing,
    danger = false,
    disabled = false,
  }: {
    headline: string;
    supporting?: string | null;
    icon?: string;
    onclick?: () => void;
    trailing?: Snippet;
    danger?: boolean;
    disabled?: boolean;
  } = $props();
</script>

{#if onclick}
  <button class="list-item clickable" class:danger {disabled} {onclick}>
    {@render body()}
  </button>
{:else}
  <div class="list-item" class:danger>{@render body()}</div>
{/if}

{#snippet body()}
  {#if icon}<Icon name={icon} />{/if}
  <div class="text">
    <div class="headline">{headline}</div>
    {#if supporting}<div class="supporting">{supporting}</div>{/if}
  </div>
  {#if trailing}{@render trailing()}{/if}
{/snippet}

<style>
  .list-item {
    display: flex;
    align-items: center;
    gap: 16px;
    width: 100%;
    min-height: 56px;
    padding: 8px 16px;
    border: none;
    border-radius: 12px;
    background: none;
    text-align: left;
    color: var(--md-on-surface);
  }
  .list-item > :global(.ms) {
    color: var(--md-on-surface-variant);
  }
  .clickable {
    cursor: pointer;
  }
  .clickable:hover:not(:disabled) {
    background: color-mix(in srgb, var(--md-on-surface) 6%, transparent);
  }
  .clickable:disabled {
    opacity: 0.5;
    cursor: default;
  }
  .text {
    flex: 1;
    min-width: 0;
  }
  .headline {
    font-size: 16px;
    line-height: 24px;
  }
  .supporting {
    font-size: 14px;
    line-height: 20px;
    color: var(--md-on-surface-variant);
  }
  .danger .headline,
  .danger > :global(.ms) {
    color: var(--md-error);
  }
</style>
