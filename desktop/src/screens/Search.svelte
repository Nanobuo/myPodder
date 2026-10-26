<script lang="ts">
  import { languageOf, countryOf } from "../lib/countries.ts";
  import { discover, normalizeUrl } from "../lib/discover.svelte.ts";
  import { t } from "../lib/i18n.ts";
  import { looksLikeUrl, search, searchDescriptions, usesPodcastIndex, type SearchResult } from "../lib/search.ts";
  import { settings } from "../lib/settings.svelte.ts";
  import { nav, versions } from "../lib/store.svelte.ts";
  import CountryPicker from "../components/CountryPicker.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";
  import PodcastCard from "../components/PodcastCard.svelte";
  import ResultRow from "../components/ResultRow.svelte";
  import Spinner from "../components/Spinner.svelte";
  import TextField from "../components/TextField.svelte";

  let { focus }: { focus?: number } = $props();

  // La ricerca resta quando si torna indietro dall'anteprima.
  let query = $state(lastQuery);
  let input = $state<HTMLInputElement>();
  let pickerOpen = $state(false);

  type State =
    | { kind: "idle" }
    | { kind: "loading" }
    | { kind: "results"; items: SearchResult[]; mentions: SearchResult[]; loadingMore: boolean }
    | { kind: "error" };
  let status = $state<State>({ kind: "idle" });

  const country = $derived(discover.country);

  $effect(() => {
    void focus;
    input?.focus();
  });

  $effect(() => {
    void versions.podcasts;
    void settings.searchCountry;
    void settings.showTopCharts;
    void discover.ensure();
  });

  // Ricerca con attesa di mezzo secondo dopo l'ultima lettera.
  $effect(() => {
    const q = query.trim();
    const cc = country;
    lastQuery = query;
    if (q.length < 2 || looksLikeUrl(q)) {
      status = { kind: "idle" };
      return;
    }
    let cancelled = false;
    const timer = setTimeout(async () => {
      status = { kind: "loading" };
      const language = languageOf(cc);
      const extra = searchDescriptions(q, language).catch(() => null);
      const main = await search(q, cc, language).catch(() => null);
      if (cancelled) return;
      if (main) status = { kind: "results", items: main, mentions: [], loadingMore: true };
      const more = await extra;
      if (cancelled) return;
      if (!main && !more) {
        status = { kind: "error" };
        return;
      }
      const known = new Set((main ?? []).map((r) => normalizeUrl(r.feedUrl)));
      status = {
        kind: "results",
        items: main ?? [],
        mentions: (more ?? []).filter((r) => !known.has(normalizeUrl(r.feedUrl))),
        loadingMore: false,
      };
    }, 500);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  });
</script>

<script lang="ts" module>
  let lastQuery = "";
</script>

<div class="screen">
  <div class="screen-inner">
    <header class="search-head">
      <div class="field">
        <TextField bind:value={query} bind:inputEl={input} label={t("search_hint")} icon="search">
          {#snippet trailing()}
            {#if query}<IconButton icon="close" label={t("clear")} onclick={() => (query = "")} />{/if}
          {/snippet}
        </TextField>
      </div>
      <button class="country" onclick={() => (pickerOpen = true)} title={t("search_country")}>
        <span class="flag">{countryOf(country).flag}</span>
        <span class="label-large">{countryOf(country).name}</span>
        <Icon name="arrow_drop_down" />
      </button>
    </header>

    {#if looksLikeUrl(query)}
      <button class="open-feed" onclick={() => nav.navigate({ name: "preview", feedUrl: query.trim() })}>
        <Icon name="rss_feed" />
        <span class="text">
          <span class="title-medium">{t("open_feed")}</span>
          <span class="body-small muted clamp-1">{query.trim()}</span>
        </span>
        <Icon name="chevron_right" />
      </button>
    {:else if status.kind === "loading"}
      <div class="empty"><Spinner /></div>
    {:else if status.kind === "error"}
      <div class="empty"><Icon name="cloud_off" /><p>{t("search_error")}</p></div>
    {:else if status.kind === "results"}
      {#if status.items.length === 0 && status.mentions.length === 0 && !status.loadingMore}
        <div class="empty"><Icon name="search_off" /><p>{t("search_no_results")}</p></div>
      {/if}
      <div class="results">
        {#each status.items as r (r.feedUrl)}<ResultRow result={r} />{/each}
      </div>
      {#if status.loadingMore}
        <div class="more"><Spinner size={28} /></div>
      {:else if status.mentions.length}
        <h3 class="section-title">{t("search_in_descriptions")}</h3>
        <p class="body-small muted hint">{t("search_in_descriptions_hint")}</p>
        <div class="results">
          {#each status.mentions as r (r.feedUrl)}<ResultRow result={r} />{/each}
        </div>
      {/if}
    {:else}
      {#if discover.content.suggestions.length}
        <h3 class="section-title">{t("suggested_for_you")}</h3>
        <div class="carousel">
          {#each discover.content.suggestions as r (r.feedUrl)}<PodcastCard result={r} />{/each}
        </div>
      {:else if !discover.loading && settings.interests.length === 0}
        <p class="body-medium muted">{t("no_suggestions")}</p>
      {/if}

      {#if settings.showTopCharts && discover.content.topCharts.length}
        <h3 class="section-title">{t("top_charts", countryOf(country).name)}</h3>
        <div class="carousel">
          {#each discover.content.topCharts as r, i (r.feedUrl)}<PodcastCard result={r} rank={i + 1} />{/each}
        </div>
      {/if}

      {#if discover.content.trending.length}
        <h3 class="section-title">{t("trending")}</h3>
        <div class="grid">
          {#each discover.content.trending as r (r.feedUrl)}<PodcastCard result={r} width={0} />{/each}
        </div>
      {/if}

      {#if discover.loading}
        <div class="more"><Spinner size={28} /></div>
      {/if}
      {#if usesPodcastIndex}<p class="body-small muted credit">{t("powered_by_podcastindex")}</p>{/if}
    {/if}
  </div>
</div>

{#if pickerOpen}<CountryPicker onclose={() => (pickerOpen = false)} />{/if}

<style>
  .search-head {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px 0 8px;
    position: sticky;
    top: 0;
    z-index: 5;
    background: var(--md-surface);
  }
  .field {
    flex: 1;
  }
  .country {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 56px;
    padding: 0 8px 0 16px;
    border-radius: 28px;
    border: 1px solid var(--md-outline-variant);
    background: none;
    cursor: pointer;
    color: var(--md-on-surface);
    max-width: 240px;
  }
  .country:hover {
    background: color-mix(in srgb, var(--md-on-surface) 6%, transparent);
  }
  .country .label-large {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .flag {
    font-size: 20px;
  }
  .open-feed {
    display: flex;
    align-items: center;
    gap: 16px;
    width: 100%;
    margin-top: 16px;
    padding: 16px;
    border: none;
    border-radius: 16px;
    background: var(--md-surface-container-low);
    cursor: pointer;
    text-align: left;
    color: var(--md-on-surface);
  }
  .open-feed .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .results {
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-top: 8px;
  }
  .hint {
    margin: -4px 0 4px;
  }
  .more {
    display: grid;
    place-items: center;
    padding: 24px;
  }
  .carousel {
    display: flex;
    gap: 16px;
    overflow-x: auto;
    padding-bottom: 8px;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 20px 16px;
  }
  .credit {
    margin-top: 32px;
    text-align: center;
  }
</style>
