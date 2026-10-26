import type { ParsedChapter } from "./feed.ts";

/** Formato JSON dei capitoli Podcasting 2.0. */
export function parseJsonChapters(textValue: string): ParsedChapter[] {
  const data = JSON.parse(textValue) as {
    chapters?: { startTime?: number; title?: string; img?: string; url?: string; toc?: boolean }[];
  };
  return (data.chapters ?? [])
    .filter((c) => c.toc !== false)
    .map((c) => ({
      startMs: Math.round((c.startTime ?? 0) * 1000),
      title: c.title ?? "",
      imageUrl: c.img ?? null,
      url: c.url ?? null,
    }))
    .sort((a, b) => a.startMs - b.startMs);
}

export interface Id3Chapter {
  startMs: number;
  title: string | null;
  url: string | null;
  image: Uint8Array | null;
  imageMime: string | null;
}

const syncSafe = (b: Uint8Array, o: number) =>
  ((b[o] & 0x7f) << 21) | ((b[o + 1] & 0x7f) << 14) | ((b[o + 2] & 0x7f) << 7) | (b[o + 3] & 0x7f);
const int32 = (b: Uint8Array, o: number) => ((b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3]) >>> 0;
const latin1 = (b: Uint8Array, from: number, to: number) => new TextDecoder("latin1").decode(b.subarray(from, to));

/** Dimensione totale del tag (header incluso) dai primi 10 byte, o null se non c'è un tag ID3. */
export function id3TagSize(header: Uint8Array): number | null {
  if (header.length < 10 || header[0] !== 0x49 || header[1] !== 0x44 || header[2] !== 0x33) return null;
  return syncSafe(header, 6) + 10;
}

function forEachFrame(
  data: Uint8Array, from: number, to: number, version: number,
  block: (id: string, start: number, length: number) => void,
) {
  let pos = from;
  while (pos + 10 <= to) {
    if (data[pos] === 0) break; // padding
    const id = latin1(data, pos, pos + 4);
    const len = version === 4 ? syncSafe(data, pos + 4) : int32(data, pos + 4);
    const start = pos + 10;
    if (len <= 0 || start + len > to) break;
    block(id, start, len);
    pos = start + len;
  }
}

function decode(data: Uint8Array, from: number, to: number, enc: number): string {
  const slice = data.subarray(from, to);
  switch (enc) {
    case 1: return new TextDecoder("utf-16").decode(slice); // con BOM
    case 2: return new TextDecoder("utf-16be").decode(slice);
    case 3: return new TextDecoder("utf-8").decode(slice);
    default: return new TextDecoder("latin1").decode(slice);
  }
}

const clean = (s: string) => s.replace(/^[\u0000\s]+|[\u0000\s]+$/g, "");

/** Fine di una stringa terminata: per UTF-16 il terminatore è di due byte. */
function terminator(data: Uint8Array, from: number, to: number, enc: number): number {
  let p = from;
  if (enc === 1 || enc === 2) {
    while (p + 1 < to && !(data[p] === 0 && data[p + 1] === 0)) p += 2;
    return Math.min(p + 2, to);
  }
  while (p < to && data[p] !== 0) p++;
  return Math.min(p + 1, to);
}

/** Lettore dei frame CHAP di un tag ID3v2.3/2.4, con titolo, link e immagine. */
export function parseId3Chapters(data: Uint8Array): Id3Chapter[] {
  const size = id3TagSize(data);
  if (size == null) return [];
  const version = data[3];
  if (version < 3) return [];
  const flags = data[5];
  let pos = 10;
  if (flags & 0x40) pos += version === 4 ? syncSafe(data, pos) : int32(data, pos) + 4;
  const end = Math.min(size, data.length);
  const result: Id3Chapter[] = [];
  forEachFrame(data, pos, end, version, (id, start, len) => {
    if (id !== "CHAP") return;
    const to = start + len;
    let p = start;
    while (p < to && data[p] !== 0) p++; // element ID
    p++;
    if (p + 16 > to) return;
    const chapter: Id3Chapter = { startMs: int32(data, p), title: null, url: null, image: null, imageMime: null };
    forEachFrame(data, p + 16, to, version, (sub, s, l) => {
      const e = s + l;
      const enc = data[s];
      if (sub === "TIT2" && l >= 2) chapter.title = clean(decode(data, s + 1, e, enc)) || null;
      else if (sub === "WXXX") {
        const urlStart = terminator(data, s + 1, e, enc);
        chapter.url = clean(latin1(data, urlStart, e)) || null;
      } else if (sub === "APIC") {
        let q = s + 1;
        const mimeStart = q;
        while (q < e && data[q] !== 0) q++;
        chapter.imageMime = latin1(data, mimeStart, q) || "image/jpeg";
        q += 2; // terminatore + tipo immagine
        q = terminator(data, q, e, enc); // descrizione
        if (q < e) chapter.image = data.slice(q, e);
      }
    });
    result.push(chapter);
  });
  return result.sort((a, b) => a.startMs - b.startMs);
}

const TIMESTAMP = /(?<![\d:])(\d{1,2}:)?\d{1,2}:\d{2}(?![\d:])/;

export function timestampToMs(value: string): number | null {
  const parts = value.split(":").map(Number);
  if (parts.some(Number.isNaN)) return null;
  if (parts.length === 2) return (parts[0] * 60 + parts[1]) * 1000;
  if (parts.length === 3) return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000;
  return null;
}

/** Capitoli ricavati dai timestamp (es. "12:34 Titolo") presenti nella descrizione. */
export function parseDescriptionChapters(html: string | null | undefined): ParsedChapter[] {
  if (!html?.trim()) return [];
  const textValue = html
    .replace(/<br\s*\/?>|<\/p>|<\/li>|<\/div>|<\/h\d>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/&#39;/g, "'").replace(/&quot;/g, '"');
  const chapters: ParsedChapter[] = [];
  for (const line of textValue.split(/\r?\n/)) {
    const m = TIMESTAMP.exec(line);
    if (!m) continue;
    const ms = timestampToMs(m[0]);
    if (ms == null) continue;
    const title = (line.slice(0, m.index) + line.slice(m.index + m[0].length))
      .trim()
      .replace(/^[-–—:|()[\]•·\s]+|[-–—:|()[\]•·\s]+$/g, "")
      .trim();
    chapters.push({ startMs: ms, title: title || m[0], imageUrl: null, url: null });
  }
  // Accetta solo una sequenza crescente di almeno due capitoli: altrimenti sono timestamp sparsi.
  if (chapters.length < 2) return [];
  for (let i = 1; i < chapters.length; i++) if (chapters[i].startMs <= chapters[i - 1].startMs) return [];
  return chapters;
}
