# Sincronizzazione (Supabase)

Configurazione del progetto Supabase, da fare una volta sola:

1. In **SQL Editor** incolla ed esegui [`schema.sql`](schema.sql). Va rieseguito quando cambia:
   non tocca i dati già salvati.
2. In **Authentication → URL Configuration → Redirect URLs** aggiungi:
   - `com.nanobuo.mypodder://login`
   - `com.nanobuo.mypodder.debug://login`
   - `http://localhost:47823/**` (app desktop)

Il primo accesso avviene con il link via email. Le email di Supabase possono tardare qualche
minuto: dopo il primo accesso conviene impostare una password da *Impostazioni →
Sincronizzazione*, così sugli altri dispositivi si entra subito.
