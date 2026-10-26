# myPodder Desktop

myPodder per Windows, macOS e Linux, con le stesse funzioni dell'app Android e la stessa
sincronizzazione (iscrizioni, avanzamento degli episodi e coda).

## Installazione

Scarica l'installer dall'ultima release `vX.Y.Z`, la stessa che contiene l'APK Android
(oppure dall'ultima build di prova, `desktop-latest`):

- **Windows**: `myPodder_X.Y.Z_x64-setup.exe`. Al primo avvio SmartScreen può mostrare un avviso
  perché l'app non è firmata: *Ulteriori informazioni → Esegui comunque*.
- **macOS** (Intel e Apple Silicon): `myPodder_X.Y.Z_universal.dmg`. L'app non è autenticata da
  Apple: la prima volta aprila con tasto destro → *Apri*, oppure da *Impostazioni di sistema →
  Privacy e sicurezza → Apri comunque*.
- **Linux**: `.AppImage` (rendila eseguibile e avviala) oppure `.deb`.

## Sviluppo

Servono Node.js 22 e Rust (più, su Linux, `libwebkit2gtk-4.1-dev`).

```sh
npm install
npm run tauri dev     # app con ricaricamento automatico
npm run check         # controllo dei tipi
npm test              # test dei parser
npm run tauri build   # installer per il sistema corrente
```

Le credenziali di Podcast Index si passano come `VITE_PODCASTINDEX_KEY` e
`VITE_PODCASTINDEX_SECRET` (in CI arrivano dai Secrets del repository). Senza, la ricerca usa
iTunes.

## Struttura

- `src/lib`: dati e logica (database SQLite, feed, capitoli, player, download, sincronizzazione).
- `src/screens`, `src/player`, `src/components`: interfaccia in Svelte.
- `src-tauri`: parte nativa in Rust. Oltre ai plugin di Tauri contiene un piccolo server locale
  per riprodurre gli episodi scaricati e quello che riceve il ritorno dal link di accesso.

Android e desktop condividono il numero di versione, quello di `versionName` in
`app/build.gradle.kts`: quando su `main` arriva una versione nuova, `release.yml` compila APK e
installer e li pubblica insieme nella release `vX.Y.Z`. In `src-tauri/tauri.conf.json` la
versione serve solo per le build locali.
