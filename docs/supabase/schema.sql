-- Schema della sincronizzazione di myPodder.
-- Da incollare ed eseguire una volta in Supabase → SQL Editor. Si può rieseguire senza danni.
--
-- Ogni riga appartiene a un utente (user_id) e la Row Level Security fa sì che ognuno legga e
-- scriva solo le proprie. updated_at è l'istante (ms) della modifica sul dispositivo: vince la più
-- recente. synced_at è l'istante (ms) in cui il server ha salvato la riga: serve ai dispositivi per
-- scaricare solo ciò che è cambiato.

create table if not exists public.subscriptions (
    user_id    uuid   not null default auth.uid() references auth.users on delete cascade,
    feed_url   text   not null,
    title      text,
    image_url  text,
    updated_at bigint not null,
    synced_at  bigint not null default 0,
    primary key (user_id, feed_url)
);

create table if not exists public.episode_states (
    user_id        uuid    not null default auth.uid() references auth.users on delete cascade,
    feed_url       text    not null,
    guid           text    not null,
    position_ms    bigint  not null default 0,
    completed      boolean not null default false,
    last_played_at bigint  not null default 0,
    updated_at     bigint  not null,
    synced_at      bigint  not null default 0,
    primary key (user_id, feed_url, guid)
);

create index if not exists episode_states_synced on public.episode_states (user_id, synced_at);

-- La coda è un'unica lista per utente: [{"f": feed_url, "g": guid}, ...]
create table if not exists public.queue_state (
    user_id    uuid   primary key default auth.uid() references auth.users on delete cascade,
    items      jsonb  not null default '[]'::jsonb,
    updated_at bigint not null,
    synced_at  bigint not null default 0
);

-- Statistiche: ogni dispositivo carica i propri ascolti (per giorno ed episodio), le app ne
-- mostrano la somma. stats_reset segna l'ultimo azzeramento, valido per tutti i dispositivi.
create table if not exists public.listening_stats (
    user_id     uuid    not null default auth.uid() references auth.users on delete cascade,
    device_id   text    not null,
    day         bigint  not null,
    feed_url    text    not null,
    guid        text    not null,
    listened_ms bigint  not null default 0,
    content_ms  bigint  not null default 0,
    completed   boolean not null default false,
    updated_at  bigint  not null,
    synced_at   bigint  not null default 0,
    primary key (user_id, device_id, day, feed_url, guid)
);

create index if not exists listening_stats_synced on public.listening_stats (user_id, synced_at);

create table if not exists public.stats_reset (
    user_id    uuid   primary key default auth.uid() references auth.users on delete cascade,
    reset_at   bigint not null,
    updated_at bigint not null,
    synced_at  bigint not null default 0
);

-- Ignora le modifiche più vecchie di quella salvata e segna l'ora di salvataggio.
create or replace function public.keep_latest() returns trigger
language plpgsql set search_path = '' as $$
begin
    if tg_op = 'UPDATE' and new.updated_at <= old.updated_at then
        return null;
    end if;
    new.synced_at := (extract(epoch from clock_timestamp()) * 1000)::bigint;
    return new;
end;
$$;

drop trigger if exists keep_latest on public.subscriptions;
create trigger keep_latest before insert or update on public.subscriptions
    for each row execute function public.keep_latest();
drop trigger if exists keep_latest on public.episode_states;
create trigger keep_latest before insert or update on public.episode_states
    for each row execute function public.keep_latest();
drop trigger if exists keep_latest on public.queue_state;
create trigger keep_latest before insert or update on public.queue_state
    for each row execute function public.keep_latest();
drop trigger if exists keep_latest on public.listening_stats;
create trigger keep_latest before insert or update on public.listening_stats
    for each row execute function public.keep_latest();
drop trigger if exists keep_latest on public.stats_reset;
create trigger keep_latest before insert or update on public.stats_reset
    for each row execute function public.keep_latest();

alter table public.subscriptions  enable row level security;
alter table public.episode_states enable row level security;
alter table public.queue_state    enable row level security;
alter table public.listening_stats enable row level security;
alter table public.stats_reset    enable row level security;

drop policy if exists "own rows" on public.subscriptions;
create policy "own rows" on public.subscriptions for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
drop policy if exists "own rows" on public.episode_states;
create policy "own rows" on public.episode_states for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
drop policy if exists "own rows" on public.queue_state;
create policy "own rows" on public.queue_state for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
drop policy if exists "own rows" on public.listening_stats;
create policy "own rows" on public.listening_stats for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
drop policy if exists "own rows" on public.stats_reset;
create policy "own rows" on public.stats_reset for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.subscriptions, public.episode_states, public.queue_state,
    public.listening_stats, public.stats_reset to authenticated;
revoke all on public.subscriptions, public.episode_states, public.queue_state,
    public.listening_stats, public.stats_reset from anon;

-- Elimina l'account di chi la chiama e, a cascata, tutti i suoi dati sincronizzati.
create or replace function public.delete_account() returns void
language sql security definer set search_path = '' as $$
    delete from auth.users where id = auth.uid();
$$;
revoke execute on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;
