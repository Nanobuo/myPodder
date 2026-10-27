<script lang="ts" module>
  /** Chiude il menu aperto: ne resta visibile uno solo alla volta. */
  let closeOpen: (() => void) | null = null;

  export interface MenuItem {
    label: string;
    icon?: string;
    run: () => void;
    danger?: boolean;
  }
</script>

<script lang="ts">
  import IconButton from "./IconButton.svelte";
  import Icon from "./Icon.svelte";
  import { t } from "../lib/i18n.ts";

  let { items, icon = "more_vert", label = t("more_options") }: { items: MenuItem[]; icon?: string; label?: string } = $props();
  let open = $state(false);
  let anchor: HTMLDivElement;
  let pos = $state({ top: 0, left: 0, up: false });

  function toggle(e: MouseEvent) {
    e.stopPropagation();
    if (!open) {
      closeOpen?.();
      closeOpen = () => (open = false);
      const r = anchor.getBoundingClientRect();
      const up = r.bottom + 56 * items.length + 16 > window.innerHeight;
      pos = { top: up ? r.top : r.bottom, left: Math.min(r.right, window.innerWidth - 8), up };
    }
    open = !open;
  }
</script>

<svelte:window onclick={() => (open = false)} onkeydown={(e) => e.key === "Escape" && (open = false)} onblur={() => (open = false)} />

<div class="anchor" bind:this={anchor}>
  <IconButton {icon} {label} onclick={toggle} />
</div>

{#if open}
  <div class="menu" class:up={pos.up} style:top="{pos.top}px" style:left="{pos.left}px" role="menu">
    {#each items as item (item.label)}
      <button
        class="item"
        class:danger={item.danger}
        role="menuitem"
        onclick={(e) => {
          e.stopPropagation();
          open = false;
          item.run();
        }}
      >
        {#if item.icon}<Icon name={item.icon} size={20} />{/if}
        <span>{item.label}</span>
      </button>
    {/each}
  </div>
{/if}

<style>
  .anchor {
    display: inline-flex;
  }
  .menu {
    position: fixed;
    transform: translateX(-100%);
    min-width: 220px;
    max-width: 300px;
    padding: 8px 0;
    border-radius: 12px;
    background: var(--md-surface-container);
    box-shadow: 0 4px 16px rgb(0 0 0 / 0.2);
    z-index: 90;
    animation: appear 0.12s ease-out;
  }
  .menu.up {
    transform: translate(-100%, -100%);
  }
  .item {
    display: flex;
    align-items: center;
    gap: 14px;
    width: 100%;
    min-height: 48px;
    padding: 12px 20px 12px 16px;
    border: none;
    background: none;
    text-align: left;
    cursor: pointer;
    color: var(--md-on-surface);
    font-size: 14px;
    line-height: 20px;
  }
  .item + .item {
    margin-top: 2px;
  }
  .item span {
    flex: 1;
    min-width: 0;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    overflow: hidden;
  }
  .item :global(.ms) {
    flex: none;
    color: var(--md-on-surface-variant);
  }
  .item:hover {
    background: color-mix(in srgb, var(--md-on-surface) 8%, transparent);
  }
  .danger,
  .danger :global(.ms) {
    color: var(--md-error);
  }
  @keyframes appear {
    from { opacity: 0; }
  }
</style>
