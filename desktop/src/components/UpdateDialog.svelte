<script lang="ts">
  import { t } from "../lib/i18n.ts";
  import { appVersion } from "../lib/net.ts";
  import { updates } from "../lib/update.svelte.ts";
  import Button from "./Button.svelte";
  import Dialog from "./Dialog.svelte";

  const release = $derived(updates.state.kind === "available" ? updates.state.release : null);
</script>

{#if release && updates.dismissed !== release.version}
  <Dialog title={t("update_available")} icon="system_update" onclose={() => (updates.dismissed = release.version)}>
    <p>{t("update_versions", release.version, appVersion)}</p>
    {#snippet actions()}
      <Button variant="text" onclick={() => (updates.dismissed = release.version)}>{t("update_later")}</Button>
      <Button
        onclick={() => {
          updates.open(release);
          updates.dismissed = release.version;
        }}>{t("update_download")}</Button
      >
    {/snippet}
  </Dialog>
{/if}
