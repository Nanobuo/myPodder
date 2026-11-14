<script lang="ts">
  import { t } from "../lib/i18n.ts";
  import { rise, ripple } from "../lib/motion.ts";
  import { player } from "../lib/player.svelte.ts";
  import Icon from "./Icon.svelte";

  let { floating = false }: { floating?: boolean } = $props();
</script>

{#if player.currentAd}
  <button use:ripple class="skip-ad" class:floating transition:rise={{ distance: 16, duration: 220 }} onclick={() => player.skipAd()}>
    <span class="label-large">{t("skip_ad")}</span>
    <Icon name="skip_next" size={20} />
  </button>
{/if}

<style>
  .skip-ad {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    height: 40px;
    padding: 0 16px 0 20px;
    border: none;
    border-radius: 20px;
    background: var(--md-tertiary-container);
    color: var(--md-on-tertiary-container);
    cursor: pointer;
    box-shadow: 0 2px 8px rgb(0 0 0 / 0.2);
    transition: border-radius 0.2s;
  }
  .skip-ad:active {
    border-radius: 12px;
  }
  .floating {
    position: absolute;
    right: 24px;
    bottom: calc(100% + 12px);
    z-index: 30;
  }
</style>
