import { fetch as tauriFetch } from "@tauri-apps/plugin-http";

declare const __APP_VERSION__: string;

export const appVersion: string = typeof __APP_VERSION__ === "string" ? __APP_VERSION__ : "dev";

/**
 * fetch senza i limiti CORS del browser (passa dal processo nativo di Tauri); fuori da Tauri,
 * ad esempio durante lo sviluppo nel browser, usa il fetch normale.
 */
export const isTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

export async function httpFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has("User-Agent")) headers.set("User-Agent", `myPodder/${appVersion} (Desktop)`);
  const options = { ...init, headers };
  return isTauri ? tauriFetch(url, options) : fetch(url, options);
}

export class HttpError extends Error {
  constructor(public status: number, message?: string) {
    super(message ?? `HTTP ${status}`);
  }
}

export async function getText(url: string, init: RequestInit = {}): Promise<string> {
  const r = await httpFetch(url, init);
  if (!r.ok) throw new HttpError(r.status);
  return r.text();
}

export async function getJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  return JSON.parse(await getText(url, init)) as T;
}
