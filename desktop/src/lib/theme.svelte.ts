import {
  Hct,
  MaterialDynamicColors,
  SchemeTonalSpot,
  argbFromHex,
  hexFromArgb,
  sourceColorFromImageBytes,
  type DynamicColor,
} from "@material/material-color-utilities";
import { httpFetch } from "./net.ts";

// Colori Material 3 come variabili CSS, ricavati da un colore di partenza: quello di myPodder o,
// con "Colori dalla copertina", quello della copertina in ascolto.

const BRAND = argbFromHex("#6750A4");

const roles: Record<string, DynamicColor> = {
  primary: MaterialDynamicColors.primary,
  "on-primary": MaterialDynamicColors.onPrimary,
  "primary-container": MaterialDynamicColors.primaryContainer,
  "on-primary-container": MaterialDynamicColors.onPrimaryContainer,
  secondary: MaterialDynamicColors.secondary,
  "on-secondary": MaterialDynamicColors.onSecondary,
  "secondary-container": MaterialDynamicColors.secondaryContainer,
  "on-secondary-container": MaterialDynamicColors.onSecondaryContainer,
  tertiary: MaterialDynamicColors.tertiary,
  "on-tertiary": MaterialDynamicColors.onTertiary,
  "tertiary-container": MaterialDynamicColors.tertiaryContainer,
  "on-tertiary-container": MaterialDynamicColors.onTertiaryContainer,
  error: MaterialDynamicColors.error,
  "on-error": MaterialDynamicColors.onError,
  "error-container": MaterialDynamicColors.errorContainer,
  "on-error-container": MaterialDynamicColors.onErrorContainer,
  surface: MaterialDynamicColors.surface,
  "on-surface": MaterialDynamicColors.onSurface,
  "surface-variant": MaterialDynamicColors.surfaceVariant,
  "on-surface-variant": MaterialDynamicColors.onSurfaceVariant,
  "surface-container-lowest": MaterialDynamicColors.surfaceContainerLowest,
  "surface-container-low": MaterialDynamicColors.surfaceContainerLow,
  "surface-container": MaterialDynamicColors.surfaceContainer,
  "surface-container-high": MaterialDynamicColors.surfaceContainerHigh,
  "surface-container-highest": MaterialDynamicColors.surfaceContainerHighest,
  outline: MaterialDynamicColors.outline,
  "outline-variant": MaterialDynamicColors.outlineVariant,
  "inverse-surface": MaterialDynamicColors.inverseSurface,
  "inverse-on-surface": MaterialDynamicColors.inverseOnSurface,
  "inverse-primary": MaterialDynamicColors.inversePrimary,
};

export function schemeCss(seed: number, dark: boolean): string {
  const scheme = new SchemeTonalSpot(Hct.fromInt(seed), dark, 0);
  return Object.entries(roles)
    .map(([name, role]) => `--md-${name}: ${hexFromArgb(role.getArgb(scheme))};`)
    .join(" ");
}

export function applyScheme(seed: number | null, dark: boolean) {
  const style = document.documentElement.style;
  style.cssText = schemeCss(seed ?? BRAND, dark);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
}

const cache = new Map<string, number | null>();

/** Colore principale di una copertina (scaricata senza limiti CORS e ridotta per l'analisi). */
export async function coverSeed(url: string | null): Promise<number | null> {
  if (!url) return null;
  if (cache.has(url)) return cache.get(url)!;
  let seed: number | null = null;
  try {
    const r = await httpFetch(url);
    if (r.ok) {
      const bitmap = await createImageBitmap(await r.blob(), { resizeWidth: 96, resizeHeight: 96 });
      const canvas = new OffscreenCanvas(96, 96);
      const ctx = canvas.getContext("2d")!;
      ctx.drawImage(bitmap, 0, 0);
      seed = sourceColorFromImageBytes(ctx.getImageData(0, 0, 96, 96).data);
    }
  } catch {
    seed = null;
  }
  cache.set(url, seed);
  return seed;
}

/** true se il sistema usa il tema scuro (aggiornato quando cambia). */
export const systemDark = $state({ value: matchMedia("(prefers-color-scheme: dark)").matches });
matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => (systemDark.value = e.matches));
