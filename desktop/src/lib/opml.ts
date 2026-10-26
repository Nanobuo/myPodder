import { open, save } from "@tauri-apps/plugin-dialog";
import { readTextFile, writeTextFile } from "@tauri-apps/plugin-fs";
import { podcastsDb } from "./db.ts";
import { parallel, subscribe } from "./repo.ts";

export interface OpmlFeed {
  title: string | null;
  url: string;
}

export function parseOpml(xml: string): OpmlFeed[] {
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  const seen = new Set<string>();
  const feeds: OpmlFeed[] = [];
  for (const el of Array.from(doc.getElementsByTagName("outline"))) {
    const url = (el.getAttribute("xmlUrl") ?? el.getAttribute("xmlurl"))?.trim();
    if (!url || seen.has(url)) continue;
    seen.add(url);
    feeds.push({ title: el.getAttribute("title") ?? el.getAttribute("text"), url });
  }
  return feeds;
}

const escape = (s: string) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

export function writeOpml(feeds: OpmlFeed[]): string {
  const lines = feeds.map((f) => {
    const t = escape(f.title ?? "");
    return `    <outline type="rss" text="${t}" title="${t}" xmlUrl="${escape(f.url)}" />`;
  });
  return `<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head>
    <title>myPodder</title>
  </head>
  <body>
${lines.join("\n")}
  </body>
</opml>
`;
}

/** Chiede un file OPML e si iscrive ai feed; null se l'utente annulla. */
export async function importOpml(onProgress: (done: number, total: number) => void): Promise<{ imported: number; failed: number } | null> {
  const path = await open({ multiple: false, filters: [{ name: "OPML", extensions: ["opml", "xml"] }] });
  if (!path) return null;
  const feeds = parseOpml(await readTextFile(path));
  let done = 0;
  onProgress(0, feeds.length);
  const results = await parallel(feeds, 4, async (f) => {
    const ok = await subscribe(f.url).then(() => true, () => false);
    onProgress(++done, feeds.length);
    return ok;
  });
  return { imported: results.filter(Boolean).length, failed: results.filter((r) => !r).length };
}

/** Chiede dove salvare e scrive le iscrizioni; restituisce quanti podcast, o null se annullato. */
export async function exportOpml(): Promise<number | null> {
  const path = await save({ defaultPath: "myPodder.opml", filters: [{ name: "OPML", extensions: ["opml"] }] });
  if (!path) return null;
  const podcasts = await podcastsDb.all();
  await writeTextFile(path, writeOpml(podcasts.map((p) => ({ title: p.title, url: p.feedUrl }))));
  return podcasts.length;
}
