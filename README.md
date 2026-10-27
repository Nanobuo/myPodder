<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/logo-dark.svg">
    <img src="docs/logo.svg" alt="myPodder" width="260">
  </picture>
</h1>

App per ascoltare i podcast su Android e su computer (Windows, macOS, Linux), con
sincronizzazione tra i dispositivi.

**Download**: l'[ultima release](../../releases/latest) contiene l'APK per Android e gli
installer per Windows, macOS e Linux.

## Funzioni

- Libreria, ricerca con [Podcast Index](https://podcastindex.org), classifiche e suggerimenti
- Coda, download per l'ascolto offline, import ed export OPML
- Player con capitoli, velocità variabile e timer di spegnimento
- Statistiche e cronologia di ascolto
- Sincronizzazione di iscrizioni, avanzamento, coda e statistiche ([configurazione](docs/supabase/README.md))

## Sviluppo

- **Android** (`app/`): Kotlin e Jetpack Compose. Si compila con `./gradlew assembleDebug`.
- **Desktop** (`desktop/`): Tauri 2 e Svelte. Istruzioni in [`desktop/README.md`](desktop/README.md).

Una nuova `versionName` in `app/build.gradle.kts` su `main` pubblica la release per entrambe le
piattaforme, con le note di `docs/release-notes/`.

## Licenza

[MIT](LICENSE)
