import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";
import { readFileSync } from "node:fs";

// Android e desktop hanno lo stesso numero di versione: in CI arriva da app/build.gradle.kts
// (APP_VERSION), in locale da tauri.conf.json.
const version =
  process.env.APP_VERSION ||
  JSON.parse(readFileSync(new URL("./src-tauri/tauri.conf.json", import.meta.url), "utf8")).version;

// Configurazione consigliata da Tauri: porta fissa e niente pulizia dello schermo nel terminale.
export default defineConfig({
  plugins: [svelte()],
  clearScreen: false,
  server: { port: 1420, strictPort: true },
  envPrefix: ["VITE_", "TAURI_ENV_"],
  define: { __APP_VERSION__: JSON.stringify(version) },
  build: { target: "es2022", chunkSizeWarningLimit: 2000 },
});
