<script lang="ts">
  import type { SearchResult } from "../lib/search.ts";
  import { nav } from "../lib/store.svelte.ts";
  import Cover from "./Cover.svelte";

  let { result, rank, width = 150 }: { result: SearchResult; rank?: number; width?: number } = $props();
</script>

<button
  class="card"
  style:width={width ? `${width}px` : undefined}
  title={result.title}
  onclick={() => nav.navigate({ name: "preview", feedUrl: result.feedUrl, title: result.title, image: result.imageUrl })}
>
  <div class="art">
    <Cover src={result.imageUrl} size="fill" radius={16} />
    {#if rank}<span class="rank">{rank}</span>{/if}
  </div>
  <span class="title-small clamp-2">{result.title}</span>
  {#if result.author}<span class="body-small muted clamp-1">{result.author}</span>{/if}
</button>

<style>
  .card {
    flex: none;
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 0;
    border: none;
    background: none;
    text-align: left;
    cursor: pointer;
    color: var(--md-on-surface);
  }
  .art {
    position: relative;
    transition: transform 0.15s;
  }
  .card:hover .art {
    transform: translateY(-2px);
  }
  .rank {
    position: absolute;
    left: 8px;
    bottom: 8px;
    min-width: 28px;
    height: 28px;
    padding: 0 8px;
    border-radius: 14px;
    background: var(--md-primary);
    color: var(--md-on-primary);
    font-weight: 700;
    display: grid;
    place-items: center;
  }
</style>
