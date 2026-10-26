<script lang="ts">
  import { stripHtml } from "../lib/format.ts";
  import type { SearchResult } from "../lib/search.ts";
  import { nav } from "../lib/store.svelte.ts";
  import Cover from "./Cover.svelte";
  import Icon from "./Icon.svelte";

  let { result }: { result: SearchResult } = $props();
</script>

<button class="row" onclick={() => nav.navigate({ name: "preview", feedUrl: result.feedUrl, title: result.title, image: result.imageUrl })}>
  <Cover src={result.imageUrl} size={64} radius={12} />
  <span class="text">
    <span class="title-medium clamp-1">{result.title}</span>
    {#if result.author}<span class="body-medium muted clamp-1">{result.author}</span>{/if}
    {#if result.description}<span class="body-small muted clamp-1">{stripHtml(result.description)}</span>{/if}
  </span>
  <Icon name="chevron_right" />
</button>

<style>
  .row {
    display: flex;
    align-items: center;
    gap: 16px;
    width: 100%;
    padding: 8px 12px;
    border: none;
    background: none;
    text-align: left;
    border-radius: 16px;
    cursor: pointer;
    color: var(--md-on-surface-variant);
  }
  .row:hover {
    background: var(--md-surface-container-low);
  }
  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    color: var(--md-on-surface);
  }
</style>
