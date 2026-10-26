import { parseClockToMs, parseFeedDate } from "./time.ts";
import { getText } from "./net.ts";

export interface ParsedChapter {
  startMs: number;
  title: string;
  imageUrl: string | null;
  url: string | null;
}

export interface ParsedEpisode {
  guid: string;
  title: string;
  description: string | null;
  audioUrl: string;
  mimeType: string | null;
  sizeBytes: number;
  imageUrl: string | null;
  link: string | null;
  pubDate: number;
  durationMs: number;
  chaptersUrl: string | null;
  inlineChapters: ParsedChapter[];
}

export interface ParsedFeed {
  title: string;
  author: string | null;
  description: string | null;
  imageUrl: string | null;
  link: string | null;
  episodes: ParsedEpisode[];
  /** Categorie iTunes principali (es. "Technology", "Society & Culture"). */
  categories: string[];
}

const NS_ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd";
const NS_CONTENT = "http://purl.org/rss/1.0/modules/content/";
const NS_PSC = "http://podlove.org/simple-chapters";
const NS_MEDIA = "http://search.yahoo.com/mrss/";
const isPodcastNs = (ns: string | null) => !!ns && ns.includes("podcastindex.org/namespace");

export class FeedParseError extends Error {}

function text(el: Element | null | undefined): string | null {
  const v = el?.textContent?.trim();
  return v ? v : null;
}

const longest = (...values: (string | null)[]) =>
  values.filter((v): v is string => !!v).sort((a, b) => b.length - a.length)[0] ?? null;

/** Parser RSS 2.0 con estensioni iTunes, content:encoded, Podcasting 2.0 e Podlove Simple Chapters. */
export function parseFeed(xml: string): ParsedFeed {
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  const channel = Array.from(doc.getElementsByTagName("channel")).find((c) => !c.namespaceURI) ?? null;
  if (!channel) {
    throw new FeedParseError("Not a podcast RSS feed");
  }

  let title: string | null = null;
  let author: string | null = null;
  let description: string | null = null;
  let summary: string | null = null;
  let itunesImage: string | null = null;
  let rssImage: string | null = null;
  let link: string | null = null;
  const categories: string[] = [];
  const episodes: ParsedEpisode[] = [];

  for (const el of Array.from(channel.children)) {
    const ns = el.namespaceURI;
    const name = el.localName;
    if (!ns) {
      if (name === "title") title = text(el);
      else if (name === "description") description = text(el);
      else if (name === "link") link = text(el);
      else if (name === "image") rssImage = text(Array.from(el.children).find((c) => c.localName === "url"));
      else if (name === "item") {
        const ep = parseItem(el);
        if (ep) episodes.push(ep);
      }
    } else if (ns === NS_ITUNES) {
      if (name === "author") author = text(el);
      else if (name === "summary") summary = text(el);
      else if (name === "image") itunesImage = el.getAttribute("href")?.trim() || itunesImage;
      // Solo le categorie di primo livello, non le sottocategorie annidate.
      else if (name === "category") {
        const c = el.getAttribute("text")?.trim();
        if (c && !categories.includes(c)) categories.push(c);
      }
    }
  }

  if (!title) throw new FeedParseError("Not a podcast RSS feed");
  return {
    title,
    author,
    description: longest(description, summary),
    imageUrl: itunesImage ?? rssImage,
    link,
    episodes,
    categories,
  };
}

function parseItem(item: Element): ParsedEpisode | null {
  let guid: string | null = null;
  let title: string | null = null;
  let itunesTitle: string | null = null;
  let description: string | null = null;
  let contentEncoded: string | null = null;
  let summary: string | null = null;
  let audioUrl: string | null = null;
  let mimeType: string | null = null;
  let size = 0;
  let image: string | null = null;
  let link: string | null = null;
  let pubDate = 0;
  let duration = 0;
  let chaptersUrl: string | null = null;
  const chapters: ParsedChapter[] = [];

  for (const el of Array.from(item.children)) {
    const ns = el.namespaceURI;
    const name = el.localName;
    if (!ns) {
      switch (name) {
        case "title": title = text(el); break;
        case "guid": guid = text(el); break;
        case "link": link = text(el); break;
        case "description": description = text(el); break;
        case "pubDate": pubDate = parseFeedDate(text(el)); break;
        case "enclosure": {
          // Preferisce la prima enclosure.
          const url = el.getAttribute("url")?.trim();
          if (url && !audioUrl) {
            audioUrl = url;
            mimeType = el.getAttribute("type");
            size = Number(el.getAttribute("length")?.trim()) || 0;
          }
          break;
        }
      }
    } else if (ns === NS_CONTENT && name === "encoded") {
      contentEncoded = text(el);
    } else if (ns === NS_ITUNES) {
      if (name === "summary") summary = text(el);
      else if (name === "duration") duration = parseClockToMs(text(el)) ?? 0;
      else if (name === "image") image = el.getAttribute("href")?.trim() || null;
      else if (name === "title") itunesTitle = text(el);
    } else if (ns === NS_MEDIA && name === "content" && !audioUrl) {
      const type = el.getAttribute("type");
      if (!type || type.startsWith("audio")) {
        audioUrl = el.getAttribute("url");
        mimeType = type;
      }
    } else if (isPodcastNs(ns) && name === "chapters") {
      chaptersUrl = el.getAttribute("url")?.trim() || null;
    } else if (ns === NS_PSC && name === "chapters") {
      for (const c of Array.from(el.children)) {
        if (c.localName !== "chapter") continue;
        const start = parseClockToMs(c.getAttribute("start"));
        const t = c.getAttribute("title");
        if (start != null && t != null) {
          chapters.push({ startMs: start, title: t, imageUrl: c.getAttribute("image"), url: c.getAttribute("href") });
        }
      }
    }
  }

  if (!audioUrl) return null;
  const t = (title ?? itunesTitle)?.trim() || audioUrl.substring(audioUrl.lastIndexOf("/") + 1);
  return {
    guid: guid?.trim() || audioUrl,
    title: t,
    description: longest(contentEncoded, description, summary),
    audioUrl,
    mimeType,
    sizeBytes: size,
    imageUrl: image,
    link,
    pubDate,
    durationMs: duration,
    chaptersUrl,
    inlineChapters: chapters.sort((a, b) => a.startMs - b.startMs),
  };
}

export async function fetchFeed(url: string): Promise<ParsedFeed> {
  return parseFeed(await getText(url, { cache: "no-store" }));
}
