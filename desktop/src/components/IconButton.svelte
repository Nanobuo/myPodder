<script lang="ts">
  import Icon from "./Icon.svelte";

  let {
    icon,
    label,
    onclick,
    variant = "standard",
    fill = false,
    size = 40,
    iconSize = 24,
    disabled = false,
    selected = false,
  }: {
    icon: string;
    label: string;
    onclick?: (e: MouseEvent) => void;
    variant?: "standard" | "filled" | "tonal" | "outlined";
    fill?: boolean;
    size?: number;
    iconSize?: number;
    disabled?: boolean;
    selected?: boolean;
  } = $props();
</script>

<button
  class="icon-button {variant}"
  class:selected
  style:width="{size}px"
  style:height="{size}px"
  title={label}
  aria-label={label}
  {disabled}
  {onclick}
>
  <Icon name={icon} {fill} size={iconSize} />
</button>

<style>
  .icon-button {
    flex: none;
    display: inline-grid;
    place-items: center;
    border: none;
    border-radius: 50%;
    background: transparent;
    color: var(--md-on-surface-variant);
    cursor: pointer;
    position: relative;
    transition: background-color 0.15s, border-radius 0.2s;
  }
  .icon-button::after {
    content: "";
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: currentColor;
    opacity: 0;
    transition: opacity 0.15s;
  }
  .icon-button:hover:not(:disabled)::after {
    opacity: 0.08;
  }
  .icon-button:active:not(:disabled)::after {
    opacity: 0.12;
  }
  .icon-button:disabled {
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
    border: 1px solid var(--md-outline-variant);
  }
  .selected {
    color: var(--md-primary);
  }
  .filled:active,
  .tonal:active {
    border-radius: 30%;
  }
</style>
