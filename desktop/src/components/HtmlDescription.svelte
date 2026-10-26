<script lang="ts">
  import { openUrl } from "@tauri-apps/plugin-opener";
  import { isTauri } from "../lib/net.ts";
  import { timestampToMs } from "../lib/chapters.ts";

  let { html, onTimestamp }: { html: string | null; onTimestamp?: (ms: number) => void } = $props();

  const TS = /(?<![\d:])((?:\d{1,2}:)?\d{1,2}:\d{2})(?![\d:])/g;

  /** HTML del feed ripulito: niente script, stili o attributi attivi; timestamp cliccabili. */
  const clean = $derived.by(() => {
    if (!html) return "";
    const source = /<[a-z][\s\S]*>/i.test(html) ? html : html.replace(/\n/g, "<br>");
    const doc = new DOMParser().parseFromString(source, "text/html");
    doc.querySelectorAll("script, style, iframe, object, embed, form, input, button, link, meta").forEach((el) => el.remove());
    doc.querySelectorAll("*").forEach((el) => {
      for (const attr of Array.from(el.attributes)) {
        const name = attr.name.toLowerCase();
        const value = attr.value.trim().toLowerCase();
        if (name.startsWith("on") || name === "style" || name === "class" || name === "id") el.removeAttribute(attr.name);
        else if ((name === "href" || name === "src") && value.startsWith("javascript:")) el.removeAttribute(attr.name);
      }
      if (el.tagName === "IMG") el.setAttribute("loading", "lazy");
    });
    if (onTimestamp) {
      const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT);
      const nodes: Text[] = [];
      while (walker.nextNode()) nodes.push(walker.currentNode as Text);
      for (const node of nodes) {
        if (node.parentElement?.closest("a")) continue;
        const text = node.data;
        TS.lastIndex = 0;
        if (!TS.test(text)) continue;
        TS.lastIndex = 0;
        const frag = doc.createDocumentFragment();
        let last = 0;
        for (const m of text.matchAll(TS)) {
          const ms = timestampToMs(m[1]);
          if (ms == null) continue;
          frag.append(text.slice(last, m.index));
          const b = doc.createElement("button");
          b.className = "ts";
          b.dataset.ms = String(ms);
          b.textContent = m[1];
          frag.append(b);
          last = m.index! + m[0].length;
        }
        frag.append(text.slice(last));
        node.replaceWith(frag);
      }
    }
    return doc.body.innerHTML;
  });

  function onclick(e: MouseEvent) {
    const target = e.target as HTMLElement;
    const ts = target.closest<HTMLElement>("button.ts");
    if (ts?.dataset.ms) {
      e.preventDefault();
      onTimestamp?.(Number(ts.dataset.ms));
      return;
    }
    const a = target.closest("a");
    if (a?.href) {
      e.preventDefault();
      if (isTauri) void openUrl(a.href);
      else window.open(a.href, "_blank");
    }
  }
</script>

<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
<div class="html-content selectable body-medium" {onclick}>{@html clean}</div>
