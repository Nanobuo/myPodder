<script lang="ts">
  import { episodesDb, podcastsDb, type EpisodeItem, type Podcast } from "../lib/db.ts";
  import { t } from "../lib/i18n.ts";
  import { player } from "../lib/player.svelte.ts";
  import * as repo from "../lib/repo.ts";
  import { nav, snackbar, versions } from "../lib/store.svelte.ts";
  import { sync } from "../lib/sync.svelte.ts";
  import Button from "../components/Button.svelte";
  import Cover from "../components/Cover.svelte";
  import Dialog from "../components/Dialog.svelte";
  import EpisodeRow from "../components/EpisodeRow.svelte";
  import HtmlDescription from "../components/HtmlDescription.svelte";
  import IconButton from "../components/IconButton.svelte";
  import Menu from "../components/Menu.svelte";
  import Spinner from "../components/Spinner.svelte";
  import Switch from "../components/Switch.svelte";

  let { id }: { id: number } = $props();
  let podcast = $state<Podcast | null>(null);
  let episodes = $state<EpisodeItem[] | null>(null);
  let expanded = $state(false);
  let descEl = $state<HTMLElement>();
  let overflowing = $state(false);

  // "Espandi" solo se la descrizione non sta tutta nello spazio ridotto.
  $effect(() => {
    const el = descEl;
    if (!el) return;
    const check = () => {
      if (!expanded) overflowing = el.scrollHeight > el.clientHeight + 4;
    };
    const ro = new ResizeObserver(check);
    ro.observe(el);
    check();
    return () => ro.disconnect();
  });
  let confirmUnsubscribe = $state(false);
  let refreshing = $state(false);

  $effect(() => {
    void versions.podcasts;
    void versions.episodes;
    void podcastsDb.get(id).then((p) => (podcast = p));
    void episodesDb.forPodcast(id).then((l) => (episodes = l));
  });

  // Aprire il podcast toglie il segno dei nuovi episodi.
  $effect(() => {
    void repo.clearNew(id);
  });

  async function refresh() {
    refreshing = true;
    try {
      const n = await repo.refresh(id);
      snackbar.show(n > 0 ? t("new_episodes", n) : t("no_new_episodes"));
    } catch {
      snackbar.show(t("refresh_error"));
    } finally {
      refreshing = false;
    }
  }

  async function unsubscribe() {
    confirmUnsubscribe = false;
    if (player.current?.podcastId === id) await player.stop();
    nav.back();
    await repo.unsubscribe(id);
    sync.requestSync();
  }
</script>

<div class="screen">
  <div class="screen-inner">
    <header class="top-bar">
      <IconButton icon="arrow_back" label={t("back")} onclick={() => nav.back()} />
      <h1>{podcast?.title ?? ""}</h1>
      {#if refreshing}<div class="spin"><Spinner size={24} /></div>{:else}<IconButton icon="refresh" label={t("refresh")} onclick={refresh} />{/if}
      <Menu
        items={[
          { label: t("mark_all_played"), icon: "done_all", run: () => void repo.markAllPlayed(id).then(() => sync.requestSync()) },
          { label: t("unsubscribe"), icon: "remove_circle", danger: true, run: () => (confirmUnsubscribe = true) },
        ]}
      />
    </header>

    {#if podcast}
      <section class="hero">
        <Cover src={podcast.imageUrl} size={200} radius={24} />
        <div class="info">
          <h2 class="headline-medium selectable">{podcast.title}</h2>
          {#if podcast.author}<p class="title-medium muted selectable">{podcast.author}</p>{/if}
          {#if podcast.description}
            <div class="desc" class:clamped={!expanded} class:fade={!expanded && overflowing} bind:this={descEl}>
              <HtmlDescription html={podcast.description} />
            </div>
            {#if overflowing}
              <button class="more label-large" onclick={() => (expanded = !expanded)}>{expanded ? t("collapse") : t("expand")}</button>
            {/if}
          {/if}
          <div class="row actions">
            <Button variant="tonal" icon="check" onclick={() => (confirmUnsubscribe = true)}>{t("subscribed")}</Button>
            <label class="auto row">
              <Switch checked={podcast.autoDownload} onchange={(v) => repo.setAutoDownload(id, v)} label={t("auto_download")} />
              <span>
                <span class="body-large">{t("auto_download")}</span><br />
                <span class="body-small muted">{t("auto_download_summary")}</span>
              </span>
            </label>
          </div>
        </div>
      </section>
    {/if}

    {#if episodes == null}
      <div class="empty"><Spinner /></div>
    {:else if episodes.length === 0}
      <div class="empty"><p>{t("no_episodes")}</p></div>
    {:else}
      <h3 class="section-title">{t("episodes_count", episodes.length)}</h3>
      <div class="episodes">
        {#each episodes as e (e.id)}<EpisodeRow item={e} showPodcast={false} dimPlayed showDescription />{/each}
      </div>
    {/if}
  </div>
</div>

{#if confirmUnsubscribe && podcast}
  <Dialog title={t("unsubscribe")} onclose={() => (confirmUnsubscribe = false)}>
    <p>{t("unsubscribe_confirm", podcast.title)}</p>
    {#snippet actions()}
      <Button variant="text" onclick={() => (confirmUnsubscribe = false)}>{t("cancel")}</Button>
      <Button onclick={unsubscribe}>{t("unsubscribe")}</Button>
    {/snippet}
  </Dialog>
{/if}

<style>
  .spin {
    width: 40px;
    display: grid;
    place-items: center;
  }
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
    margin: 0 0 12px;
  }
  .desc {
    color: var(--md-on-surface-variant);
  }
  .desc.clamped {
    max-height: 80px;
    overflow: hidden;
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
  .actions {
    margin-top: 16px;
    gap: 24px;
    flex-wrap: wrap;
  }
  .auto {
    gap: 12px;
    cursor: pointer;
  }
  .episodes {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
</style>
