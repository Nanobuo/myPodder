/** Converte "1:02:03", "62:03", "3723" o "00:01:02.500" in millisecondi. */
export function parseClockToMs(value: string | null | undefined): number | null {
  const s = value?.trim();
  if (!s) return null;
  const parts = s.split(":");
  if (parts.length > 3) return null;
  let total = 0;
  for (const p of parts) {
    if (!/^\d+(\.\d+)?$/.test(p.trim())) return null;
    total = total * 60 + Number(p);
  }
  return Math.round(total * 1000);
}

const TZ: Record<string, string> = {
  UT: "+0000", GMT: "+0000", Z: "+0000",
  EST: "-0500", EDT: "-0400", CST: "-0600", CDT: "-0500", MST: "-0700", MDT: "-0600", PST: "-0800", PDT: "-0700",
  CET: "+0100", CEST: "+0200", BST: "+0100", MET: "+0100", MEST: "+0200", EET: "+0200", EEST: "+0300",
  IST: "+0530", JST: "+0900", AEST: "+1000", AEDT: "+1100",
};

/** Interpreta le date RSS (RFC 822 e varianti comuni). Restituisce 0 se non riconosciuta. */
export function parseFeedDate(value: string | null | undefined): number {
  let s = value?.trim().replace(/\s+/g, " ");
  if (!s) return 0;
  // Fusi orari testuali che Date.parse non conosce.
  s = s.replace(/ ([A-Z]{1,4})$/, (m, tz: string) => (TZ[tz] ? ` ${TZ[tz]}` : m));
  // Giorni della settimana o mesi in forme non standard (es. "Thurs", "Sept").
  s = s.replace(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*\.?,?\s*/i, "").replace(/\bSept\b/i, "Sep");
  if (!/\d/.test(s)) return 0;
  let t = Date.parse(s);
  if (Number.isNaN(t)) t = Date.parse(s + " +0000");
  return Number.isNaN(t) ? 0 : t;
}
