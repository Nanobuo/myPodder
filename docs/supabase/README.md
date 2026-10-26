# Sincronizzazione con Supabase

myPodder può sincronizzare tra dispositivi:

- le iscrizioni;
- l'avanzamento degli episodi (posizione, ascoltato, ultimo ascolto);
- la coda.

I dati stanno in un progetto Supabase gratuito.

## Configurazione del progetto (una volta sola)

1. **Tabelle**: in *SQL Editor*, incolla ed esegui [`schema.sql`](schema.sql).
2. **Link di ritorno nell'app**: in *Authentication → URL Configuration → Redirect URLs* aggiungi:
   - `com.nanobuo.mypodder://login`
   - `com.nanobuo.mypodder.debug://login` (build di debug)

Il template standard delle email contiene già il link di accesso: basta questo, i template non
vanno modificati. Modificarli, ad esempio per aggiungere il codice `{{ .Token }}`, sul piano
gratuito richiede un SMTP proprio. L'app accetta anche il codice, se presente.

URL del progetto e chiave pubblicabile sono già impostati in `app/build.gradle.kts`. Puoi
sostituirli con le variabili `SUPABASE_URL` e `SUPABASE_KEY`, da Secrets o da `local.properties`.
La chiave pubblicabile è pensata per stare nelle app: ogni utente vede solo i propri dati grazie
alla Row Level Security.

## Come funziona

- **Iscrizioni**: fusione a tre vie tra l'elenco locale, quello sul server e quello presente
  all'ultima sincronizzazione. Un podcast aggiunto o rimosso su un dispositivo viene aggiunto o
  rimosso anche sugli altri.
- **Episodi**: vengono identificati da URL del feed e guid. Per ciascuno vince la modifica più
  recente.
- **Coda**: è un'unica lista e vince la versione modificata per ultima.
- **Quando si sincronizza**:
  - all'apertura dell'app;
  - quando metti in pausa;
  - a fine episodio;
  - quando cambi iscrizioni o coda;
  - durante l'aggiornamento automatico dei feed.

## Limiti del piano gratuito

- Un progetto senza attività per 7 giorni viene messo in pausa. Lo riattivi dalla dashboard e i
  dati restano. L'aggiornamento automatico dei feed sincronizza anche in background, quindi di
  solito il progetto resta attivo.
- Le email di Supabase hanno un limite di pochi invii l'ora. Per molti utenti va configurato un
  SMTP proprio (*Authentication → Emails → SMTP Settings*).
