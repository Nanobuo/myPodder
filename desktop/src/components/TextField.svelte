<script lang="ts">
  import Icon from "./Icon.svelte";

  let {
    value = $bindable(""),
    label,
    type = "text",
    icon,
    autofocus = false,
    disabled = false,
    onenter,
    inputEl = $bindable(),
    trailing,
  }: {
    value?: string;
    label: string;
    type?: string;
    icon?: string;
    autofocus?: boolean;
    disabled?: boolean;
    onenter?: () => void;
    inputEl?: HTMLInputElement;
    trailing?: import("svelte").Snippet;
  } = $props();

  $effect(() => {
    if (autofocus) inputEl?.focus();
  });
</script>

<label class="field" class:has-icon={!!icon}>
  {#if icon}<Icon name={icon} />{/if}
  <input
    bind:this={inputEl}
    bind:value
    {type}
    placeholder={label}
    aria-label={label}
    {disabled}
    spellcheck="false"
    onkeydown={(e) => {
      if (e.key === "Enter") onenter?.();
    }}
  />
  {#if trailing}{@render trailing()}{/if}
</label>

<style>
  .field {
    display: flex;
    align-items: center;
    gap: 12px;
    height: 56px;
    padding: 0 8px 0 16px;
    border-radius: 28px;
    background: var(--md-surface-container-high);
    color: var(--md-on-surface-variant);
    cursor: text;
  }
  .field:focus-within {
    outline: 2px solid var(--md-primary);
  }
  input {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: none;
    outline: none;
    background: none;
    font-size: 16px;
    color: var(--md-on-surface);
    user-select: text;
  }
  input::placeholder {
    color: var(--md-on-surface-variant);
  }
</style>
