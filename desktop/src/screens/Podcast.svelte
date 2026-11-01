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
  import Icon from "../components/Icon.svelte";
  import TextField from "../components/TextField.svelte";
  import { formatClock } from "../lib/format.ts";
  import { parseClockToMs } from "../lib/time.ts";

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

  // Parti da saltare: secondi ("45") o minuti e secondi ("1:30"); vuoto = niente.
  let editSkip = $state(false);
  let introText = $state("");
  let outroText = $state("");
  const parseSkip = (v: string) => (v.trim() ? parseClockToMs(v.trim()) : 0);

  function openSkip() {
    introText = podcast?.skipIntroMs ? formatClock(podcast.skipIntroMs) : "";
    outroText = podcast?.skipOutroMs ? formatClock(podcast.skipOutroMs) : "";
    editSkip = true;
  }

  async function saveSkip() {
    const intro = parseSkip(introText);
    const outro = parseSkip(outroText);
    if (intro == null || outro == null) return;
    editSkip = false;
    await repo.setSkip(id, intro, outro);
    sync.requestSync();
  }
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
          <button class="skip" onclick={openSkip}>
            <Icon name="content_cut" />
            <span>
              <span class="body-large">{t("skip_parts")}</span><br />
              <span class="body-small muted">
                {podcast.skipIntroMs || podcast.skipOutroMs
                  ? t("skip_parts_summary", formatClock(podcast.skipIntroMs), formatClock(podcast.skipOutroMs))
                  : t("skip_parts_off")}
              </span>
            </span>
          </button>
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

{#if editSkip && podcast}
  <Dialog title={t("skip_parts")} icon="content_cut" onclose={() => (editSkip = false)}>
    <div class="skip-form">
      <p>{t("skip_parts_hint")}</p>
      <TextField bind:value={introText} label={t("skip_intro")} icon="first_page" autofocus onenter={saveSkip} />
      <TextField bind:value={outroText} label={t("skip_outro")} icon="last_page" onenter={saveSkip} />
    </div>
    {#snippet actions()}
      <Button variant="text" onclick={() => (editSkip = false)}>{t("cancel")}</Button>
      <Button disabled={parseSkip(introText) == null || parseSkip(outroText) == null} onclick={saveSkip}>{t("save")}</Button>
    {/snippet}
  </Dialog>
{/if}

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
  .skip {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 12px;
    padding: 8px 12px 8px 8px;
    border: none;
    border-radius: 12px;
    background: none;
    text-align: left;
    cursor: pointer;
    color: var(--md-on-surface);
  }
  .skip > :global(.ms) {
    color: var(--md-on-surface-variant);
  }
  .skip:hover {
    background: color-mix(in srgb, var(--md-on-surface) 6%, transparent);
  }
  .skip-form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .skip-form p {
    margin: 0;
  }
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
