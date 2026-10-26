<script lang="ts">
  import type { Snippet } from "svelte";
  import Icon from "./Icon.svelte";

  let {
    variant = "filled",
    icon,
    onclick,
    disabled = false,
    children,
    title,
  }: {
    variant?: "filled" | "tonal" | "outlined" | "text" | "elevated";
    icon?: string;
    onclick?: (e: MouseEvent) => void;
    disabled?: boolean;
    children: Snippet;
    title?: string;
  } = $props();
</script>

<button class="btn {variant}" class:with-icon={!!icon} {disabled} {onclick} {title}>
  {#if icon}<Icon name={icon} size={18} />{/if}
  <span>{@render children()}</span>
</button>

<style>
  .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    height: 40px;
    padding: 0 24px;
    border-radius: 20px;
    border: none;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    position: relative;
    white-space: nowrap;
    flex: none;
    transition: border-radius 0.2s;
  }
  .btn.with-icon {
    padding-left: 16px;
  }
  .btn::after {
    content: "";
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: currentColor;
    opacity: 0;
    transition: opacity 0.15s;
  }
  .btn:hover:not(:disabled)::after {
    opacity: 0.08;
  }
  .btn:active:not(:disabled) {
    border-radius: 12px;
  }
  .btn:disabled {
    opacity: 0.38;
    cursor: default;
  }
  .filled {
    background: var(--md-primary);
    color: var(--md-on-primary);
  }
  .tonal {
    background: var(--md-secondary-container);
    color: var(--md-on-secondary-container);
  }
  .outlined {
    background: transparent;
    border: 1px solid var(--md-outline-variant);
    color: var(--md-on-surface-variant);
  }
  .text {
    background: transparent;
    color: var(--md-primary);
    padding: 0 12px;
  }
  .elevated {
    background: var(--md-surface-container-low);
    color: var(--md-primary);
    box-shadow: 0 1px 2px rgb(0 0 0 / 0.3);
  }
</style>
