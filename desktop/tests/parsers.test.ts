import { test } from "node:test";
import assert from "node:assert/strict";
import { parseClockToMs, parseFeedDate } from "../src/lib/time.ts";
import { parseDescriptionChapters, parseId3Chapters, parseJsonChapters } from "../src/lib/chapters.ts";

test("clock", () => {
  assert.equal(parseClockToMs("1:02:03"), 3723000);
  assert.equal(parseClockToMs("62:03"), 3723000);
  assert.equal(parseClockToMs("3723"), 3723000);
  assert.equal(parseClockToMs("00:01:02.500"), 62500);
  assert.equal(parseClockToMs("abc"), null);
});

test("feed dates", () => {
  assert.equal(parseFeedDate("Mon, 06 Jan 2025 10:00:00 GMT"), Date.UTC(2025, 0, 6, 10));
  assert.equal(parseFeedDate("Mon, 06 Jan 2025 10:00:00 +0100"), Date.UTC(2025, 0, 6, 9));
  assert.equal(parseFeedDate("Thurs, 9 Jan 2025 10:00:00 PST"), Date.UTC(2025, 0, 9, 18));
  assert.equal(parseFeedDate("Tue, 5 Sept 2023 08:00:00 EDT"), Date.UTC(2023, 8, 5, 12));
  assert.equal(parseFeedDate("2024-03-01T12:00:00Z"), Date.UTC(2024, 2, 1, 12));
  assert.equal(parseFeedDate("garbage"), 0);
});

test("description chapters", () => {
  const html = "<p>00:00 Intro</p><p>12:34 - Tema</p><p>1:02:03 Saluti</p>";
  assert.deepEqual(parseDescriptionChapters(html).map((c) => [c.startMs, c.title]), [
    [0, "Intro"], [754000, "Tema"], [3723000, "Saluti"],
  ]);
  assert.deepEqual(parseDescriptionChapters("<p>Alle 10:30 in diretta</p>"), []);
});

test("json chapters", () => {
  const c = parseJsonChapters('{"chapters":[{"startTime":60.5,"title":"B"},{"startTime":0,"title":"A"},{"startTime":30,"title":"X","toc":false}]}');
  assert.deepEqual(c.map((x) => [x.startMs, x.title]), [[0, "A"], [60500, "B"]]);
});

test("id3 chapters", () => {
  const enc = new TextEncoder();
  const frame = (id: string, body: Uint8Array) => {
    const out = new Uint8Array(10 + body.length);
    out.set(enc.encode(id), 0);
    new DataView(out.buffer).setUint32(4, body.length);
    out.set(body, 10);
    return out;
  };
  const concat = (...parts: Uint8Array[]) => {
    const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
    let o = 0;
    for (const p of parts) { out.set(p, o); o += p.length; }
    return out;
  };
  const chap = (id: string, startMs: number, title: string) => {
    const times = new Uint8Array(16);
    new DataView(times.buffer).setUint32(0, startMs);
    return frame("CHAP", concat(enc.encode(id + "\0"), times, frame("TIT2", concat(new Uint8Array([3]), enc.encode(title)))));
  };
  const frames = concat(chap("c1", 90000, "Secondo"), chap("c0", 0, "Primo"));
  const header = new Uint8Array([0x49, 0x44, 0x33, 3, 0, 0, 0, 0, 0, 0]);
  const size = frames.length;
  header[6] = (size >> 21) & 0x7f; header[7] = (size >> 14) & 0x7f; header[8] = (size >> 7) & 0x7f; header[9] = size & 0x7f;
  const chapters = parseId3Chapters(concat(header, frames));
  assert.deepEqual(chapters.map((c) => [c.startMs, c.title]), [[0, "Primo"], [90000, "Secondo"]]);
});
