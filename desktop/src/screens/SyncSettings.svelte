<script lang="ts">
  import { formatRelative } from "../lib/format.ts";
  import { t } from "../lib/i18n.ts";
  import { sync } from "../lib/sync.svelte.ts";
  import Button from "../components/Button.svelte";
  import Dialog from "../components/Dialog.svelte";
  import ListItem from "../components/ListItem.svelte";
  import Spinner from "../components/Spinner.svelte";
  import TextField from "../components/TextField.svelte";

  let showLogin = $state(false);
  let confirmSignOut = $state(false);
  let confirmDelete = $state(false);
  let email = $state("");
  let code = $state("");

  const login = $derived(sync.login);
  // Dopo l'invio si resta sul passo del codice, anche se la verifica fallisce.
  const sentTo = $derived(
    login.kind === "sent" || login.kind === "verifying" ? login.email : login.kind === "error" ? (login.email ?? null) : null,
  );
  const busy = $derived(login.kind === "sending" || login.kind === "verifying");
  const validEmail = $derived(/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim()));

  // Accesso completato dal link: la finestra si chiude da sola.
  $effect(() => {
    if (sync.session) showLogin = false;
  });

  function closeLogin() {
    showLogin = false;
    code = "";
    sync.resetLogin();
  }

  const statusText = $derived.by(() => {
    const s = sync.status;
    if (s.kind === "running") return t("sync_running");
    if (s.kind === "error") return t("sync_error", s.message);
    return sync.lastSyncAt ? t("sync_last", formatRelative(sync.lastSyncAt)) : t("sync_never");
  });
</script>

{#if !sync.session}
  <ListItem headline={t("sync_sign_in")} supporting={t("sync_sign_in_summary")} icon="cloud_sync" onclick={() => (showLogin = true)} />
{:else}
  <ListItem headline={t("sync_account")} supporting={sync.session.email} icon="account_circle" />
  <ListItem headline={t("sync_now")} supporting={statusText} icon="sync" disabled={sync.status.kind === "running"} onclick={() => sync.syncNow()}>
    {#snippet trailing()}{#if sync.status.kind === "running"}<Spinner size={24} />{/if}{/snippet}
  </ListItem>
  <ListItem headline={t("sync_sign_out")} icon="logout" onclick={() => (confirmSignOut = true)} />
  <ListItem headline={t("sync_delete_account")} supporting={t("sync_delete_account_summary")} icon="delete_forever" onclick={() => (confirmDelete = true)} />
{/if}

{#if showLogin && !sync.session}
  <Dialog title={t("sync_sign_in")} icon="cloud_sync" onclose={closeLogin}>
    <div class="form">
      {#if sentTo == null}
        <p>{t("sync_email_hint")}</p>
        <TextField bind:value={email} label={t("sync_email")} type="email" icon="mail" autofocus disabled={busy} onenter={() => validEmail && sync.sendLink(email)} />
      {:else}
        <p>{t("sync_code_hint", sentTo)}</p>
        <TextField bind:value={code} label={t("sync_code")} icon="pin" disabled={busy} onenter={() => code.length >= 6 && sync.verifyCode(sentTo, code)} />
      {/if}
      {#if login.kind === "error"}<p class="error body-small">{t("sync_login_error", login.message)}</p>{/if}
      {#if busy}<div class="busy"><Spinner size={28} /></div>{/if}
    </div>
    {#snippet actions()}
      {#if sentTo == null}
        <Button variant="text" onclick={closeLogin}>{t("cancel")}</Button>
        <Button disabled={!validEmail || busy} onclick={() => sync.sendLink(email)}>{t("sync_send_code")}</Button>
      {:else}
        <Button variant="text" disabled={busy} onclick={() => { code = ""; sync.resetLogin(); }}>{t("sync_change_email")}</Button>
        <Button disabled={code.replace(/\D/g, "").length < 6 || busy} onclick={() => sync.verifyCode(sentTo, code)}>{t("sync_verify")}</Button>
      {/if}
    {/snippet}
  </Dialog>
{/if}

{#if confirmSignOut}
  <Dialog title={t("sync_sign_out")} onclose={() => (confirmSignOut = false)}>
    <p>{t("sync_sign_out_confirm")}</p>
    {#snippet actions()}
      <Button variant="text" onclick={() => (confirmSignOut = false)}>{t("cancel")}</Button>
      <Button onclick={() => { confirmSignOut = false; void sync.signOut(); }}>{t("sync_sign_out")}</Button>
    {/snippet}
  </Dialog>
{/if}

{#if confirmDelete}
  <Dialog title={t("sync_delete_account")} onclose={() => (confirmDelete = false)}>
    <p>{t("sync_delete_account_confirm")}</p>
    {#snippet actions()}
      <Button variant="text" onclick={() => (confirmDelete = false)}>{t("cancel")}</Button>
      <Button onclick={() => { confirmDelete = false; void sync.deleteAccount(); }}>{t("delete")}</Button>
    {/snippet}
  </Dialog>
{/if}

<style>
  .form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .form p {
    margin: 0;
  }
  .busy {
    display: grid;
    place-items: center;
  }
</style>
