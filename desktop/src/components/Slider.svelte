<script lang="ts">
  /** Cursore Material: il valore cambia solo al rilascio, mentre si trascina mostra l'anteprima. */
  let {
    value,
    max,
    onchange,
    oninput,
    label,
    step = 1,
    disabled = false,
  }: {
    value: number;
    max: number;
    onchange: (v: number) => void;
    oninput?: (v: number | null) => void;
    label: string;
    step?: number;
    disabled?: boolean;
  } = $props();

  let dragging = $state<number | null>(null);
  const shown = $derived(dragging ?? value);
  const pct = $derived(max > 0 ? Math.min(100, Math.max(0, (shown / max) * 100)) : 0);
</script>

<input
  type="range"
  class="slider"
  min="0"
  max={Math.max(max, 1)}
  {step}
  value={shown}
  aria-label={label}
  {disabled}
  style:--pct="{pct}%"
  oninput={(e) => {
    dragging = Number(e.currentTarget.value);
    oninput?.(dragging);
  }}
  onchange={(e) => {
    const v = Number(e.currentTarget.value);
    dragging = null;
    oninput?.(null);
    onchange(v);
  }}
/>

<style>
  .slider {
    -webkit-appearance: none;
    appearance: none;
    width: 100%;
    height: 20px;
    background: transparent;
    cursor: pointer;
    margin: 0;
  }
  .slider::-webkit-slider-runnable-track {
    height: 6px;
    border-radius: 3px;
    background: linear-gradient(
      to right,
      var(--md-primary) 0,
      var(--md-primary) var(--pct),
      var(--md-secondary-container) var(--pct),
      var(--md-secondary-container) 100%
    );
  }
  .slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    width: 4px;
    height: 20px;
    margin-top: -7px;
    border-radius: 2px;
    background: var(--md-primary);
    box-shadow: 0 0 0 3px var(--md-surface-container);
  }
  .slider::-moz-range-track {
    height: 6px;
    border-radius: 3px;
    background: var(--md-secondary-container);
  }
  .slider::-moz-range-progress {
    height: 6px;
    border-radius: 3px;
    background: var(--md-primary);
  }
  .slider::-moz-range-thumb {
    width: 4px;
    height: 20px;
    border: none;
    border-radius: 2px;
    background: var(--md-primary);
  }
  .slider:disabled {
    opacity: 0.38;
    cursor: default;
  }
</style>
