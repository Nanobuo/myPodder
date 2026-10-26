<script lang="ts">
  import { snackbar } from "../lib/store.svelte.ts";
  import IconButton from "./IconButton.svelte";
  import { t } from "../lib/i18n.ts";
</script>

{#if snackbar.current}
  {@const s = snackbar.current}
  {#key s.id}
    <div class="snackbar" role="status">
      <span class="message">{s.message}</span>
      {#if s.action}
        <button
          class="action"
          onclick={() => {
            s.action?.run();
            snackbar.dismiss();
          }}>{s.action.label}</button
        >
      {/if}
      <IconButton icon="close" label={t("close")} size={36} iconSize={20} onclick={() => snackbar.dismiss()} />
    </div>
  {/key}
{/if}

<style>
  .snackbar {
    position: fixed;
    left: 50%;
    bottom: 112px;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 320px;
    max-width: min(640px, calc(100% - 48px));
    padding: 4px 4px 4px 16px;
    border-radius: 8px;
    background: var(--md-inverse-surface);
    color: var(--md-inverse-on-surface);
    box-shadow: 0 4px 12px rgb(0 0 0 / 0.25);
    z-index: 120;
    animation: slide 0.2s ease-out;
  }
  .snackbar :global(.icon-button) {
    color: var(--md-inverse-on-surface);
  }
  .message {
    flex: 1;
    padding: 10px 0;
  }
  .action {
    border: none;
    background: none;
    color: var(--md-inverse-primary);
    font-weight: 500;
    padding: 10px 12px;
    cursor: pointer;
    border-radius: 20px;
  }
  .action:hover {
    background: color-mix(in srgb, var(--md-inverse-primary) 12%, transparent);
  }
  @keyframes slide {
    from { opacity: 0; transform: translate(-50%, 16px); }
  }
</style>
