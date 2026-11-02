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
    placeholder,
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
    /** Se presente, l'etichetta resta visibile a sinistra e il campo mostra questo suggerimento. */
    placeholder?: string;
  } = $props();

  $effect(() => {
    if (autofocus) inputEl?.focus();
  });
</script>

<label class="field" class:has-icon={!!icon} class:inline={placeholder != null}>
  {#if icon}<Icon name={icon} />{/if}
  {#if placeholder != null}<span class="inline-label">{label}</span>{/if}
  <input
    bind:this={inputEl}
    bind:value
    {type}
    placeholder={placeholder ?? label}
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
    background: var(--field-bg, var(--md-surface-container-high));
    color: var(--md-on-surface-variant);
    cursor: text;
    outline: 2px solid transparent;
    outline-offset: -2px;
    transition: outline-color 0.2s, background-color 0.2s;
  }
  .field:hover {
    background: color-mix(in srgb, var(--md-on-surface) 4%, var(--field-bg, var(--md-surface-container-high)));
  }
  .field:focus-within {
    outline-color: var(--md-primary);
  }
  .field:focus-within > :global(.ms) {
    color: var(--md-primary);
  }
  .inline-label {
    flex: 1;
    font-size: 16px;
    color: var(--md-on-surface);
  }
  .inline input {
    flex: 0 0 96px;
    padding-right: 12px;
    text-align: right;
    font-variant-numeric: tabular-nums;
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
