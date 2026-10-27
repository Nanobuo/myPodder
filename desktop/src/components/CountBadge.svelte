<script lang="ts">
  /** Numero sopra un'icona (es. episodi in coda); va dentro un contenitore con position: relative. */
  let { count }: { count: number } = $props();
</script>

{#if count > 0}
  <span class="badge" aria-label={String(count)}>{count > 99 ? "99+" : count}</span>
{/if}

<style>
  .badge {
    position: absolute;
    top: -2px;
    right: -4px;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border-radius: 8px;
    background: var(--md-error);
    color: var(--md-on-error);
    font-size: 11px;
    font-weight: 600;
    line-height: 16px;
    text-align: center;
    pointer-events: none;
  }
</style>
