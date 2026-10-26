<script lang="ts">
  let { name, fill = false, size = 24, class: cls = "" }: { name: string; fill?: boolean; size?: number; class?: string } = $props();
</script>

<span class="ms {cls}" class:fill style:font-size="{size}px" aria-hidden="true">{name}</span>
