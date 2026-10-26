<script lang="ts">
  let { size = 40, color = "var(--md-primary)" }: { size?: number; color?: string } = $props();
</script>

<svg class="spinner" style:color={color} width={size} height={size} viewBox="0 0 48 48" role="progressbar">
  <circle cx="24" cy="24" r="20" fill="none" stroke-width="4" stroke-linecap="round" />
</svg>

<style>
  .spinner {
    animation: rotate 1.4s linear infinite;
  }
  circle {
    stroke: currentColor;
    stroke-dasharray: 90 150;
    stroke-dashoffset: 0;
    animation: dash 1.4s ease-in-out infinite;
  }
  @keyframes rotate {
    to { transform: rotate(360deg); }
  }
  @keyframes dash {
    0% { stroke-dasharray: 1 150; stroke-dashoffset: 0; }
    50% { stroke-dasharray: 90 150; stroke-dashoffset: -35; }
    100% { stroke-dasharray: 90 150; stroke-dashoffset: -124; }
  }
</style>
