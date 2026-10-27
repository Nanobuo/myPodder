# myPodder Desktop

Servono Node.js 22 e Rust (su Linux anche `libwebkit2gtk-4.1-dev`).

```sh
npm install
npm run tauri dev     # avvia l'app in sviluppo
npm run check         # controllo dei tipi
npm test              # test dei parser
```

Per la ricerca con Podcast Index imposta `VITE_PODCASTINDEX_KEY` e `VITE_PODCASTINDEX_SECRET`;
senza, si usa iTunes.
