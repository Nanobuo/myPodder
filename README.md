<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/logo-dark.svg">
    <img src="docs/logo.svg" alt="myPodder" width="260">
  </picture>
</h1>

App per ascoltare i podcast, per Android e per desktop (Windows, macOS, Linux), con sincronizzazione tra dispositivi.

## Funzionalità

- **Libreria** dei podcast seguiti, con aggiornamento dei feed manuale o automatico (WorkManager).
- **Ricerca** tramite [Podcast Index](https://podcastindex.org), podcast di tendenza e anteprima prima di iscriversi; si può anche incollare direttamente l'URL di un feed.
- **Importazione ed esportazione OPML** per passare da e verso altre app.
- **Ascolto offline**: download per episodio, download automatico per podcast, solo Wi-Fi, spazio occupato, eliminazione dopo l'ascolto.
- **Coda** riordinabile con il trascinamento, "Riproduci dopo", nuovi episodi aggiunti in automatico.
- **Player** a schermo intero e mini-player, velocità da 0,5× a 3×, salti avanti/indietro configurabili, ripresa dalla posizione salvata, controlli in notifica e sulla schermata di blocco (Media3).
- **Sleep timer** a minuti o a fine episodio, con dissolvenza finale e prolungamento scuotendo il telefono.
- **Capitoli** da JSON Podcasting 2.0, tag ID3 `CHAP` del file audio, Podlove Simple Chapters o timestamp nella descrizione. I timestamp nella descrizione sono cliccabili.
- **Copertina per episodio**: immagine del capitolo, dell'episodio o, in mancanza, del podcast.
- **Statistiche**: tempo totale, grafico per giorni/settimane/mesi, podcast più ascoltati, episodi completati, tempo risparmiato con la velocità, serie di giorni consecutivi.
- **Tema** chiaro/scuro/di sistema e colori dinamici (Material You).

- **Sincronizzazione** di iscrizioni, avanzamento degli episodi e coda tra telefono e computer, con accesso tramite link via email ([Supabase](docs/supabase/README.md)).

## App desktop

La cartella [`desktop`](desktop) contiene myPodder per Windows, macOS e Linux, con le stesse
funzioni dell'app Android. Gli installer si scaricano dalle release `desktop-v…`; l'ultima build
di prova è nella release [`desktop-latest`](../../releases/tag/desktop-latest).

## Tecnologie

Kotlin, Jetpack Compose, Material 3 Expressive, Media3 (ExoPlayer + MediaSessionService),
Room, DataStore, WorkManager, Hilt, Coil, OkHttp, kotlinx.serialization,
[Reorderable](https://github.com/Calvin-LL/Reorderable).

Desktop: [Tauri 2](https://tauri.app) (Rust) con interfaccia in Svelte 5 e TypeScript, SQLite,
colori Material 3 da [material-color-utilities](https://github.com/material-foundation/material-color-utilities).

## Licenza

[MIT](LICENSE)
