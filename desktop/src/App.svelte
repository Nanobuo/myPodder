<script lang="ts">
  import { onMount } from "svelte";
  import { app } from "./lib/app.svelte.ts";
  import { t, type StringKey } from "./lib/i18n.ts";
  import { reloadQueueIds } from "./lib/live.svelte.ts";
  import { player } from "./lib/player.svelte.ts";
  import { settings } from "./lib/settings.svelte.ts";
  import { nav, versions, type Route } from "./lib/store.svelte.ts";
  import { applyScheme, coverSeed, systemDark } from "./lib/theme.svelte.ts";
  import Icon from "./components/Icon.svelte";
  import Snackbar from "./components/Snackbar.svelte";
  import Spinner from "./components/Spinner.svelte";
  import UpdateDialog from "./components/UpdateDialog.svelte";
  import Wordmark from "./components/Wordmark.svelte";
  import PlayerBar from "./player/PlayerBar.svelte";
  import PlayerView from "./player/PlayerView.svelte";
  import Library from "./screens/Library.svelte";
  import Search from "./screens/Search.svelte";
  import Queue from "./screens/Queue.svelte";
  import Downloads from "./screens/Downloads.svelte";
  import Stats from "./screens/Stats.svelte";
  import History from "./screens/History.svelte";
  import Settings from "./screens/Settings.svelte";
  import Podcast from "./screens/Podcast.svelte";
  import Episode from "./screens/Episode.svelte";
  import Preview from "./screens/Preview.svelte";
  import Onboarding from "./screens/Onboarding.svelte";

  onMount(() => {
    void app.start();
  });

  // Tema: chiaro/scuro e colore d'accento (eventualmente dalla copertina in ascolto).
  const dark = $derived(settings.theme === "dark" || (settings.theme === "system" && systemDark.value));
  let seed = $state<number | null>(null);
  $effect(() => {
    const url = settings.coverColors ? (player.current?.podcastImage ?? player.current?.imageUrl ?? null) : null;
    let cancelled = false;
    void coverSeed(url).then((s) => {
      if (!cancelled) seed = s;
    });
    return () => (cancelled = true);
  });
  $effect(() => applyScheme(seed, dark));

  $effect(() => {
    void versions.queue;
    void versions.podcasts;
    void reloadQueueIds();
  });

  const items: { route: Route["name"]; icon: string; label: StringKey }[] = [
    { route: "library", icon: "video_library", label: "nav_library" },
    { route: "search", icon: "search", label: "nav_search" },
    { route: "queue", icon: "queue_music", label: "nav_queue" },
    { route: "downloads", icon: "download", label: "nav_downloads" },
    { route: "stats", icon: "bar_chart", label: "nav_stats" },
    { route: "history", icon: "history", label: "nav_history" },
  ];

  function isTyping(e: KeyboardEvent) {
    const el = e.target as HTMLElement | null;
    return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
  }

  function onkeydown(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "f") {
      e.preventDefault();
      nav.navigate({ name: "search", focus: Date.now() });
      return;
    }
    if (e.altKey && e.key === "ArrowLeft") {
      nav.back();
      return;
    }
    if (isTyping(e) || e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.key === " ") {
      e.preventDefault();
      player.togglePlayPause();
    } else if (e.key === "ArrowLeft") {
      player.skipBack();
    } else if (e.key === "ArrowRight") {
      player.skipForward();
    } else if (e.key === "Escape") {
      // Menu, finestre e popup si chiudono da soli con Escape: niente "indietro" in quel caso.
      if (!document.querySelector(".menu, .popup, .scrim")) nav.back();
    }
  }

  // Tasto "indietro" del mouse.
  function onmouseup(e: MouseEvent) {
    if (e.button === 3) {
      e.preventDefault();
      nav.back();
    }
  }
</script>

<svelte:window {onkeydown} {onmouseup} />

{#if !app.ready}
  <div class="splash"><Spinner size={48} /></div>
{:else if app.needsOnboarding}
  <Onboarding />
{:else}
  <div class="shell">
    <nav class="rail">
      <div class="logo"><Wordmark height={19} /></div>
      {#each items as item (item.route)}
        {@const active = nav.section === item.route}
        <button class="rail-item" class:active onclick={() => nav.navigate({ name: item.route } as Route)} title={t(item.label)}>
          <span class="indicator"><Icon name={item.icon} fill={active} /></span>
          <span class="label-medium">{t(item.label)}</span>
        </button>
      {/each}
      <div class="spacer"></div>
      <button class="rail-item" class:active={nav.section === "settings"} onclick={() => nav.navigate({ name: "settings" })} title={t("settings")}>
        <span class="indicator"><Icon name="settings" fill={nav.section === "settings"} /></span>
        <span class="label-medium">{t("settings")}</span>
      </button>
    </nav>

    <main class="content">
      {#key nav.stack.length + ":" + JSON.stringify(nav.current)}
        <div class="page">
          {#if nav.current.name === "library"}<Library />
          {:else if nav.current.name === "search"}<Search focus={nav.current.focus} />
          {:else if nav.current.name === "queue"}<Queue />
          {:else if nav.current.name === "downloads"}<Downloads />
          {:else if nav.current.name === "stats"}<Stats />
          {:else if nav.current.name === "history"}<History />
          {:else if nav.current.name === "settings"}<Settings />
          {:else if nav.current.name === "podcast"}<Podcast id={nav.current.id} />
          {:else if nav.current.name === "episode"}<Episode id={nav.current.id} />
          {:else if nav.current.name === "preview"}<Preview feedUrl={nav.current.feedUrl} title={nav.current.title} image={nav.current.image} />
          {/if}
        </div>
      {/key}
      {#if nav.playerOpen && player.current}
        <PlayerView />
      {/if}
    </main>

    <PlayerBar />
  </div>
{/if}

<Snackbar />
<UpdateDialog />

<style>
  .splash {
    height: 100%;
    display: grid;
    place-items: center;
  }
  .shell {
    height: 100%;
    display: grid;
    grid-template-columns: 96px 1fr;
    grid-template-rows: 1fr auto;
    grid-template-areas: "rail content" "player player";
  }
  .rail {
    grid-area: rail;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: 16px 0 16px;
    background: var(--md-surface);
    overflow-y: auto;
  }
  .logo {
    margin-bottom: 20px;
    color: var(--md-on-surface);
  }
  .rail-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    width: 80px;
    padding: 4px 0 8px;
    border: none;
    background: none;
    color: var(--md-on-surface-variant);
    cursor: pointer;
  }
  .indicator {
    display: grid;
    place-items: center;
    width: 56px;
    height: 32px;
    border-radius: 16px;
    transition: background-color 0.2s;
  }
  .rail-item:hover .indicator {
    background: color-mix(in srgb, var(--md-on-surface) 8%, transparent);
  }
  .rail-item.active {
    color: var(--md-on-surface);
  }
  .rail-item.active .indicator {
    background: var(--md-secondary-container);
    color: var(--md-on-secondary-container);
  }
  .spacer {
    flex: 1;
  }
  .content {
    grid-area: content;
    position: relative;
    min-width: 0;
    min-height: 0;
    background: var(--md-surface);
    border-top-left-radius: 24px;
    overflow: hidden;
  }
  .page {
    height: 100%;
    animation: enter 0.18s ease-out;
  }
  @keyframes enter {
    from {
      opacity: 0;
      transform: translateY(6px);
    }
  }
</style>
