<script lang="ts">
  let { height = 28 }: { height?: number } = $props();
</script>

<span class="wordmark" style:font-size="{height}px" aria-label="myPodder">my<span class="accent">Podder</span></span>

<style>
  .wordmark {
    font-family: "Inter Variable", sans-serif;
    font-weight: 750;
    letter-spacing: -0.045em;
    line-height: 1;
    white-space: nowrap;
  }
  .accent {
    color: var(--md-primary);
  }
</style>
