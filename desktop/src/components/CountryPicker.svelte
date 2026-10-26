<script lang="ts">
  import { allCountries, countryOf, defaultCountry } from "../lib/countries.ts";
  import { t } from "../lib/i18n.ts";
  import { settings, updateSettings } from "../lib/settings.svelte.ts";
  import Dialog from "./Dialog.svelte";
  import Icon from "./Icon.svelte";

  let { onclose }: { onclose: () => void } = $props();
  const auto = countryOf(defaultCountry());

  function pick(code: string) {
    updateSettings({ searchCountry: code });
    onclose();
  }
</script>

<Dialog title={t("choose_country")} {onclose}>
  <div class="list">
    <button class="item" onclick={() => pick("")}>
      <span class="flag"><Icon name="my_location" size={20} /></span>
      <span class="name">{t("country_automatic", auto.name)}</span>
      {#if !settings.searchCountry}<Icon name="check" />{/if}
    </button>
    {#each allCountries() as c (c.code)}
      <button class="item" onclick={() => pick(c.code)}>
        <span class="flag">{c.flag}</span>
        <span class="name">{c.name}</span>
        {#if settings.searchCountry === c.code}<Icon name="check" />{/if}
      </button>
    {/each}
  </div>
</Dialog>

<style>
  .list {
    max-height: 60vh;
    overflow-y: auto;
    margin: 0 -12px;
  }
  .item {
    display: flex;
    align-items: center;
    gap: 16px;
    width: 100%;
    height: 48px;
    padding: 0 12px;
    border: none;
    background: none;
    border-radius: 12px;
    cursor: pointer;
    text-align: left;
    color: var(--md-on-surface);
    font-size: 15px;
  }
  .item:hover {
    background: color-mix(in srgb, var(--md-on-surface) 8%, transparent);
  }
  .item :global(.ms) {
    color: var(--md-primary);
  }
  .flag {
    width: 28px;
    font-size: 22px;
    text-align: center;
  }
  .name {
    flex: 1;
  }
</style>
