<script lang="ts">
  import { t } from "../lib/i18n.ts";
  import HtmlDescription from "./HtmlDescription.svelte";

  let { html, maxHeight = 80 }: { html: string; maxHeight?: number } = $props();
  let expanded = $state(false);
  let el = $state<HTMLElement>();
  let overflowing = $state(false);

  // "Espandi" solo se la descrizione non sta tutta nello spazio ridotto.
  $effect(() => {
    const node = el;
    if (!node) return;
    const check = () => {
      if (!expanded) overflowing = node.scrollHeight > node.clientHeight + 4;
    };
    const ro = new ResizeObserver(check);
    ro.observe(node);
    check();
    return () => ro.disconnect();
  });
</script>

<div
  class="desc"
  class:fade={!expanded && overflowing}
  style:max-height={expanded ? "none" : `${maxHeight}px`}
  bind:this={el}
>
  <HtmlDescription {html} />
</div>
{#if overflowing}
  <button class="more label-large" onclick={() => (expanded = !expanded)}>{expanded ? t("collapse") : t("expand")}</button>
{/if}

<style>
  .desc {
    overflow: hidden;
    color: var(--md-on-surface-variant);
  }
  .desc.fade {
    mask-image: linear-gradient(to bottom, black 50%, transparent);
  }
  .more {
    border: none;
    background: none;
    color: var(--md-primary);
    padding: 4px 0;
    cursor: pointer;
  }
</style>
