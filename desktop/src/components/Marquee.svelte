<script lang="ts">
  /** Testo su una riga che, se non ci sta, scorre avanti e indietro con una pausa alle estremità. */
  let { text, class: klass = "" }: { text: string; class?: string } = $props();
  let box = $state<HTMLElement>();
  let inner = $state<HTMLElement>();
  let distance = $state(0);

  $effect(() => {
    void text;
    const b = box;
    const i = inner;
    if (!b || !i) return;
    const measure = () => {
      // Larghezza utile senza il margine delle sfumature, che compare solo quando il testo scorre.
      const cs = getComputedStyle(b);
      const width = b.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
      distance = Math.max(0, Math.ceil(i.scrollWidth - width));
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(b);
    ro.observe(i);
    return () => ro.disconnect();
  });

  // ~40 px al secondo, più le pause: la durata cresce con la lunghezza del testo.
  const duration = $derived(distance > 0 ? (distance / 40) * 2 + 4 : 0);
</script>

<div class="marquee {klass}" class:moving={distance > 0} bind:this={box} title={text}>
  <span bind:this={inner} style:--dist="-{distance}px" style:animation-duration="{duration}s">{text}</span>
</div>

<style>
  .marquee {
    overflow: hidden;
    white-space: nowrap;
  }
  .marquee span {
    display: inline-block;
  }
  .marquee.moving {
    mask-image: linear-gradient(to right, transparent, black 12px, black calc(100% - 12px), transparent);
    padding: 0 12px;
    margin: 0 -12px;
  }
  .marquee.moving span {
    animation-name: scroll;
    animation-timing-function: linear;
    animation-iteration-count: infinite;
  }
  @keyframes scroll {
    0%, 15% { transform: translateX(0); }
    50%, 65% { transform: translateX(var(--dist)); }
    100% { transform: translateX(0); }
  }
  @media (prefers-reduced-motion: reduce) {
    .marquee.moving span {
      animation: none;
    }
    .marquee.moving {
      text-overflow: ellipsis;
    }
  }
</style>
