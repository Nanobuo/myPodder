<script lang="ts">
  let { checked, onchange, label }: { checked: boolean; onchange: (v: boolean) => void; label?: string } = $props();
</script>

<button
  class="switch"
  class:on={checked}
  role="switch"
  aria-checked={checked}
  aria-label={label}
  onclick={(e) => {
    e.stopPropagation();
    onchange(!checked);
  }}
>
  <span class="thumb">
    {#if checked}<span class="ms" style="font-size:16px">check</span>{/if}
  </span>
</button>

<style>
  .switch {
    flex: none;
    width: 52px;
    height: 32px;
    border-radius: 16px;
    border: 2px solid var(--md-outline);
    background: var(--md-surface-container-highest);
    padding: 0;
    position: relative;
    cursor: pointer;
    transition: background-color 0.2s, border-color 0.2s;
  }
  .thumb {
    position: absolute;
    top: 50%;
    left: 6px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: var(--md-outline);
    transform: translateY(-50%);
    transition: all 0.2s;
    display: grid;
    place-items: center;
  }
  .on {
    background: var(--md-primary);
    border-color: var(--md-primary);
  }
  .on .thumb {
    left: 22px;
    width: 24px;
    height: 24px;
    background: var(--md-on-primary);
    color: var(--md-on-primary-container);
  }
</style>
