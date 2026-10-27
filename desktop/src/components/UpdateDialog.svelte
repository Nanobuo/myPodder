<script lang="ts">
  import { t } from "../lib/i18n.ts";
  import { appVersion } from "../lib/net.ts";
  import { updates } from "../lib/update.svelte.ts";
  import Button from "./Button.svelte";
  import Dialog from "./Dialog.svelte";
  import Spinner from "./Spinner.svelte";

  const s = $derived(updates.state);
  const release = $derived("release" in s ? s.release : null);
  const busy = $derived(s.kind === "downloading" || s.kind === "installing");
  const close = () => {
    if (!busy && release) updates.dismissed = release.version;
  };
</script>

{#if release && updates.dismissed !== release.version}
  <Dialog title={t("update_available")} icon="system_update" onclose={close}>
    <p>{t("update_versions", release.version, appVersion)}</p>
    {#if s.kind === "downloading"}
      <p>{t("update_downloading")}</p>
      <div class="bar"><div style:width="{(s.progress ?? 0) * 100}%" class:indeterminate={s.progress == null}></div></div>
    {:else if s.kind === "installing"}
      <div class="row"><Spinner size={24} /> <span>{t("update_installing")}</span></div>
    {:else if s.kind === "failed"}
      <p class="error">{t("update_failed")}</p>
    {/if}
    {#snippet actions()}
      {#if s.kind === "failed"}
        <Button variant="text" onclick={close}>{t("close")}</Button>
        <Button onclick={() => updates.open(release)}>{t("update_open_page")}</Button>
      {:else if !busy}
        <Button variant="text" onclick={close}>{t("update_later")}</Button>
        {#if updates.canInstall}
          <Button icon="system_update" onclick={() => updates.install(release)}>{t("update_now")}</Button>
        {:else}
          <Button onclick={() => updates.open(release)}>{t("update_download")}</Button>
        {/if}
      {/if}
    {/snippet}
  </Dialog>
{/if}

<style>
  .bar {
    height: 6px;
    border-radius: 3px;
    background: var(--md-secondary-container);
    overflow: hidden;
  }
  .bar div {
    height: 100%;
    background: var(--md-primary);
    border-radius: 3px;
    transition: width 0.2s;
  }
  .bar div.indeterminate {
    width: 30% !important;
    animation: slide 1.2s ease-in-out infinite;
  }
  @keyframes slide {
    from { transform: translateX(-100%); }
    to { transform: translateX(340%); }
  }
</style>
