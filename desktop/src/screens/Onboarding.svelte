<script lang="ts">
  import { app } from "../lib/app.svelte.ts";
  import { defaultCountry } from "../lib/countries.ts";
  import { forTopics, topics } from "../lib/discover.svelte.ts";
  import { t } from "../lib/i18n.ts";
  import { parallel, subscribe } from "../lib/repo.ts";
  import type { SearchResult } from "../lib/search.ts";
  import { settings, updateSettings } from "../lib/settings.svelte.ts";
  import { sync } from "../lib/sync.svelte.ts";
  import Button from "../components/Button.svelte";
  import Chip from "../components/Chip.svelte";
  import Cover from "../components/Cover.svelte";
  import Icon from "../components/Icon.svelte";
  import Spinner from "../components/Spinner.svelte";
  import Wordmark from "../components/Wordmark.svelte";
  import SyncSettings from "./SyncSettings.svelte";

  let step = $state<"topics" | "suggestions">("topics");
  let chosen = $state<string[]>([]);
  let suggestions = $state<SearchResult[] | null>(null);
  let selected = $state<string[]>([]);
  let subscribing = $state(false);

  // Chi accede alla sincronizzazione dal benvenuto ritrova le proprie iscrizioni.
  $effect(() => {
    if (sync.session && sync.lastSyncAt) app.finishOnboarding();
  });

  async function next() {
    updateSettings({ interests: chosen });
    step = "suggestions";
    suggestions = null;
    const country = settings.searchCountry || defaultCountry();
    suggestions = await forTopics(chosen, country, 24).catch(() => []);
  }

  async function finish() {
    subscribing = true;
    await parallel(selected, 4, (url) => subscribe(url).catch(() => 0));
    subscribing = false;
    sync.requestSync();
    app.finishOnboarding();
  }

  function toggle(list: string[], value: string) {
    return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
  }
</script>

<div class="onboarding">
  <div class="inner">
    <Wordmark height={40} />
    {#if step === "topics"}
      <h1 class="headline-medium">{t("onboarding_title")}</h1>
      <p class="body-large muted">{t("onboarding_subtitle")}</p>
      <div class="chips">
        {#each topics as topic (topic.category)}
          <Chip selected={chosen.includes(topic.category)} onclick={() => (chosen = toggle(chosen, topic.category))}>{t(topic.label)}</Chip>
        {/each}
      </div>
      <div class="sync card">
        <SyncSettings />
      </div>
      <div class="actions">
        <Button variant="text" onclick={() => app.finishOnboarding()}>{t("onboarding_skip")}</Button>
        <Button disabled={chosen.length === 0} onclick={next}>{t("onboarding_next")}</Button>
      </div>
    {:else}
      <h1 class="headline-medium">{t("onboarding_suggestions_title")}</h1>
      <p class="body-large muted">{t("onboarding_suggestions_subtitle")}</p>
      {#if suggestions == null}
        <div class="loading"><Spinner /></div>
      {:else if suggestions.length === 0}
        <p class="muted">{t("onboarding_empty")}</p>
      {:else}
        <div class="grid">
          {#each suggestions as s (s.feedUrl)}
            {@const on = selected.includes(s.feedUrl)}
            <button class="pick" class:on onclick={() => (selected = toggle(selected, s.feedUrl))} title={s.title}>
              <div class="art">
                <Cover src={s.imageUrl} size="fill" radius={16} />
                {#if on}<span class="check"><Icon name="check" /></span>{/if}
              </div>
              <span class="title-small clamp-2">{s.title}</span>
            </button>
          {/each}
        </div>
      {/if}
      <div class="actions">
        <Button variant="text" onclick={() => (step = "topics")}>{t("back")}</Button>
        {#if subscribing}<Spinner size={32} />{/if}
        <Button disabled={subscribing} onclick={finish}>
          {selected.length ? t("onboarding_subscribe", selected.length) : t("onboarding_done")}
        </Button>
      </div>
    {/if}
  </div>
</div>

<style>
  .onboarding {
    height: 100%;
    overflow-y: auto;
    background: var(--md-surface);
  }
  .inner {
    max-width: 860px;
    margin: 0 auto;
    padding: 48px 32px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  h1 {
    margin: 24px 0 0;
  }
  p {
    margin: 0;
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin: 8px 0;
  }
  .sync {
    padding: 8px;
  }
  .actions {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 12px;
    margin-top: 16px;
  }
  .loading {
    display: grid;
    place-items: center;
    padding: 48px;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 20px 16px;
  }
  .pick {
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
    border-radius: 16px;
    outline: 3px solid transparent;
    transition: outline-color 0.15s, transform 0.15s;
  }
  .pick.on .art {
    outline-color: var(--md-primary);
    transform: scale(0.96);
  }
  .check {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: var(--md-primary);
    color: var(--md-on-primary);
    display: grid;
    place-items: center;
  }
</style>
