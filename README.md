# myPodder

App Android per ascoltare i podcast, scritta in Kotlin con Jetpack Compose e Material 3 Expressive.
Interfaccia in italiano e inglese.

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

## Scaricare l'APK

Ogni push su `main` viene compilato da GitHub Actions:

- l'APK di debug più recente è nella release [**latest-debug**](../../releases/tag/latest-debug), scaricabile anche dal telefono;
- ogni esecuzione del workflow allega l'APK anche come *artifact*.

Per installarlo bisogna consentire l'installazione da origini sconosciute.

## Credenziali Podcast Index

La ricerca usa l'API di Podcast Index, che richiede una chiave e un secret gratuiti
(registrazione su <https://api.podcastindex.org>). Le credenziali **non** sono nel codice:

- **GitHub Actions**: aggiungerle in *Settings → Secrets and variables → Actions* come
  `PODCASTINDEX_KEY` e `PODCASTINDEX_SECRET`.
- **Build locale**: aggiungerle a `local.properties` (escluso da git):

  ```properties
  PODCASTINDEX_KEY=...
  PODCASTINDEX_SECRET=...
  ```

Senza credenziali l'app funziona comunque e usa come riserva l'iTunes Search API (senza i podcast di tendenza).

## Compilare

Serve JDK 17 o successivo e l'Android SDK (API 36).

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Tecnologie

Kotlin, Jetpack Compose, Material 3 Expressive, Media3 (ExoPlayer + MediaSessionService),
Room, DataStore, WorkManager, Hilt, Coil, OkHttp, kotlinx.serialization,
[Reorderable](https://github.com/Calvin-LL/Reorderable).

## Licenza

[MIT](LICENSE)
