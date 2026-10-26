<script lang="ts">
  import { podcastsDb } from "../lib/db.ts";
  import { fetchFeed, type ParsedFeed } from "../lib/feed.ts";
  import { formatDate, formatDuration, stripHtml } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import * as repo from "../lib/repo.ts";
  import { nav, versions } from "../lib/store.svelte.ts";
  import { sync } from "../lib/sync.svelte.ts";
  import Button from "../components/Button.svelte";
  import Cover from "../components/Cover.svelte";
  import HtmlDescription from "../components/HtmlDescription.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";
  import Spinner from "../components/Spinner.svelte";

  let { feedUrl, title, image }: { feedUrl: string; title?: string; image?: string | null } = $props();
  let feed = $state<ParsedFeed | null>(null);
  let error = $state(false);
  let subscribedId = $state<number | null>(null);
  let subscribing = $state(false);

  function load() {
    error = false;
    feed = null;
    fetchFeed(feedUrl).then((f) => (feed = f), () => (error = true));
  }
  $effect(load);
  $effect(() => {
    void versions.podcasts;
    void podcastsDb.byUrl(feedUrl).then((p) => (subscribedId = p?.id ?? null));
  });

  async function subscribe() {
    if (subscribing) return;
    subscribing = true;
    try {
      const id = await repo.subscribe(feedUrl, feed ?? undefined);
      sync.requestSync();
      // Dopo l'iscrizione si apre il podcast; "indietro" riporta alla ricerca.
      nav.replace({ name: "podcast", id });
    } catch {
      error = true;
    } finally {
      subscribing = false;
    }
  }
</script>

<div class="screen">
  <div class="screen-inner">
    <header class="top-bar">
      <IconButton icon="arrow_back" label={t("back")} onclick={() => nav.back()} />
      <h1>{feed?.title ?? title ?? t("preview")}</h1>
    </header>

    <section class="hero">
      <Cover src={feed?.imageUrl ?? image} size={200} radius={24} />
      <div class="info">
        <h2 class="headline-medium selectable">{feed?.title ?? title ?? ""}</h2>
        {#if feed?.author}<p class="title-medium muted">{feed.author}</p>{/if}
        <div class="row">
          {#if subscribedId != null}
            <Button variant="tonal" icon="check" onclick={() => nav.replace({ name: "podcast", id: subscribedId! })}>{t("subscribed_open")}</Button>
          {:else}
            <Button icon="add" onclick={subscribe} disabled={subscribing || (!feed && !error)}>{t("subscribe")}</Button>
            {#if subscribing}<Spinner size={28} />{/if}
          {/if}
        </div>
        {#if feed?.description}
          <div class="desc"><HtmlDescription html={feed.description} /></div>
        {/if}
      </div>
    </section>

    {#if error}
      <div class="empty">
        <Icon name="error" />
        <p>{t("feed_error")}</p>
        <Button variant="tonal" onclick={load}>{t("retry")}</Button>
      </div>
    {:else if !feed}
      <div class="empty"><Spinner /></div>
    {:else}
      <h3 class="section-title">{t("episodes_count", feed.episodes.length)}</h3>
      <div class="episodes">
        {#each feed.episodes.slice(0, 100) as e (e.guid)}
          <div class="episode">
            <div class="title-small clamp-2">{e.title}</div>
            {#if e.description}<div class="body-small muted clamp-2">{stripHtml(e.description)}</div>{/if}
            <div class="body-small muted">
              {[formatDate(e.pubDate), e.durationMs > 0 ? formatDuration(e.durationMs) : ""].filter(Boolean).join(" · ")}
            </div>
          </div>
        {/each}
      </div>
    {/if}
  </div>
</div>

<style>
  .hero {
    display: flex;
    gap: 32px;
    align-items: flex-start;
    margin: 8px 0 16px;
  }
  .info {
    flex: 1;
    min-width: 0;
  }
  h2 {
    margin: 0 0 4px;
  }
  .info > p {
    margin: 0 0 16px;
  }
  .desc {
    margin-top: 16px;
    max-height: 160px;
    overflow: hidden;
    mask-image: linear-gradient(to bottom, black 60%, transparent);
    color: var(--md-on-surface-variant);
  }
  .episodes {
    display: flex;
    flex-direction: column;
  }
  .episode {
    padding: 12px;
    border-bottom: 1px solid var(--md-outline-variant);
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
</style>
