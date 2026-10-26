<script lang="ts">
  import Icon from "./Icon.svelte";

  let { src, size = 56, radius = 12, alt = "" }: { src: string | null | undefined; size?: number | "fill"; radius?: number; alt?: string } = $props();
  let failed = $state(false);
  $effect(() => {
    void src;
    failed = false;
  });
  const dim = $derived(size === "fill" ? "100%" : `${size}px`);
</script>

<div class="cover" style:width={dim} style:height={size === "fill" ? "auto" : dim} style:border-radius="{radius}px">
  {#if src && !failed}
    <img {src} {alt} loading="lazy" decoding="async" draggable="false" onerror={() => (failed = true)} />
  {:else}
    <Icon name="podcasts" size={typeof size === "number" ? Math.max(20, size * 0.45) : 48} />
  {/if}
</div>

<style>
  .cover {
    flex: none;
    aspect-ratio: 1;
    overflow: hidden;
    background: var(--md-surface-container-highest);
    color: var(--md-on-surface-variant);
    display: grid;
    place-items: center;
  }
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
</style>
