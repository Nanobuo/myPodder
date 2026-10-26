import { locale } from "./i18n.ts";

const languageDefaults: Record<string, string> = {
  it: "IT", en: "US", fr: "FR", de: "DE", es: "ES", pt: "BR", nl: "NL", sv: "SE", da: "DK", nb: "NO", no: "NO",
  fi: "FI", pl: "PL", ja: "JP", ko: "KR", zh: "CN", ru: "RU", tr: "TR",
};

/** Lingua principale dei paesi proposti. */
const countryLanguage: Record<string, string> = {
  IT: "it", US: "en", GB: "en", IE: "en", CA: "en", AU: "en", NZ: "en", IN: "en", FR: "fr", BE: "fr", CH: "de",
  DE: "de", AT: "de", ES: "es", PT: "pt", MX: "es", AR: "es", CO: "es", CL: "es", BR: "pt", NL: "nl", SE: "sv",
  NO: "nb", DK: "da", FI: "fi", PL: "pl", JP: "ja", KR: "ko", CN: "zh", RU: "ru", TR: "tr",
};

/** Paesi principali per i podcast (classifiche Apple disponibili e cataloghi ampi). */
const main = ["IT", "US", "GB", "IE", "CA", "AU", "NZ", "IN", "FR", "BE", "CH", "DE", "AT", "ES", "PT",
  "MX", "AR", "CO", "CL", "BR", "NL", "SE", "NO", "DK", "FI", "PL", "JP", "KR"];

export interface Country {
  code: string;
  name: string;
  flag: string;
}

/** Paese predefinito: quello del sistema, altrimenti quello tipico della sua lingua. */
export function defaultCountry(): string {
  const [lang, region] = locale.split(/[-_]/);
  if (region && /^[A-Za-z]{2}$/.test(region)) return region.toUpperCase();
  return languageDefaults[lang?.toLowerCase() ?? ""] ?? "US";
}

export function languageOf(country: string): string {
  return countryLanguage[country.toUpperCase()] ?? locale.split(/[-_]/)[0].toLowerCase();
}

const names = new Intl.DisplayNames([locale], { type: "region" });

export function countryOf(code: string): Country {
  const cc = code.toUpperCase();
  const flag = [...cc].map((c) => String.fromCodePoint(0x1f1e6 + c.charCodeAt(0) - 65)).join("");
  return { code: cc, name: names.of(cc) ?? cc, flag };
}

export function allCountries(): Country[] {
  return [...new Set([...main, defaultCountry()])].map(countryOf).sort((a, b) => a.name.localeCompare(b.name, locale));
}
