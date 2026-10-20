# myPodder

App Android per ascoltare i podcast.

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

## Tecnologie

Kotlin, Jetpack Compose, Material 3 Expressive, Media3 (ExoPlayer + MediaSessionService),
Room, DataStore, WorkManager, Hilt, Coil, OkHttp, kotlinx.serialization,
[Reorderable](https://github.com/Calvin-LL/Reorderable).

## Licenza

[MIT](LICENSE)
