-- PixelDone 3.1.0-rc.1 hard schema cutover.
-- Run once in the Supabase SQL editor as the database owner.
-- PixelDone 3.1 clients require this exact schema contract and do not fall back
-- to the pre-3.1 direct-table sync protocol.

begin;

create extension if not exists pgcrypto;
create extension if not exists pg_cron;

create sequence if not exists public.pixeldone_remote_version_seq
  as bigint increment by 1 minvalue 1 start with 1;

create table if not exists public.pixeldone_schema_metadata (
  singleton boolean primary key default true check (singleton),
  schema_version text not null,
  updated_at timestamptz not null default now()
);

insert into public.pixeldone_schema_metadata(singleton, schema_version)
values (true, '3.1')
on conflict (singleton) do update
set schema_version = excluded.schema_version,
    updated_at = now();

-- The current 3.0.3 schema stored deleted checklist rows in the active table.
-- Create the 3.1 tombstone target before removing that legacy marker so those
-- deletions cannot be resurrected during the hard cutover.
create table if not exists public.sync_tombstones (
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  record_type text not null check (record_type in ('checklist', 'item')),
  local_id text not null,
  deleted_at_millis bigint not null,
  remote_version bigint not null default nextval('public.pixeldone_remote_version_seq'::regclass),
  primary key (owner_user_id, record_type, local_id)
);

-- Preserve existing user data while removing the obsolete active-row deletion
-- marker. Trash remains recoverable and is represented only by trashed_at_millis.
alter table public.todo_checklists
  add column if not exists sort_index integer not null default 0,
  add column if not exists remote_version bigint;

alter table public.todo_items
  add column if not exists sort_index integer not null default 0,
  add column if not exists reminder_repeat text not null default 'NONE',
  add column if not exists image_local_name text,
  add column if not exists image_remote_path text,
  add column if not exists image_sync_state text not null default 'LOCAL_ONLY',
  add column if not exists trashed_from_checklist_id text,
  add column if not exists trashed_from_checklist_name text,
  add column if not exists trashed_at_millis bigint,
  add column if not exists remote_version bigint;

-- Old clients commonly wrote deleted_at_millis and trashed_at_millis together.
-- Keep those records as recoverable Trash during the cutover.
update public.todo_items
set trashed_at_millis = coalesce(trashed_at_millis, deleted_at_millis)
where deleted_at_millis is not null;

insert into public.sync_tombstones(owner_user_id, record_type, local_id, deleted_at_millis)
select owner_user_id, 'checklist', local_id, deleted_at_millis
from public.todo_checklists
where deleted_at_millis is not null
on conflict (owner_user_id, record_type, local_id) do update
set deleted_at_millis = greatest(public.sync_tombstones.deleted_at_millis, excluded.deleted_at_millis);

delete from public.todo_checklists where deleted_at_millis is not null;

alter table public.todo_checklists drop column if exists deleted_at_millis;
alter table public.todo_items drop column if exists deleted_at_millis;

update public.todo_checklists
set remote_version = nextval('public.pixeldone_remote_version_seq'::regclass)
where remote_version is null;

update public.todo_items
set remote_version = nextval('public.pixeldone_remote_version_seq'::regclass)
where remote_version is null;

alter table public.todo_checklists
  alter column remote_version set default nextval('public.pixeldone_remote_version_seq'::regclass),
  alter column remote_version set not null;

alter table public.todo_items
  alter column remote_version set default nextval('public.pixeldone_remote_version_seq'::regclass),
  alter column remote_version set not null;

create table if not exists public.user_settings (
  owner_user_id uuid primary key references auth.users(id) on delete cascade,
  language_mode text not null default 'system',
  updated_at_millis bigint not null,
  remote_version bigint not null default nextval('public.pixeldone_remote_version_seq'::regclass),
  constraint user_settings_language_mode_check
    check (language_mode in ('system', 'en', 'zh-Hans', 'ar', 'fr', 'ru', 'es'))
);

alter table public.sync_mutation_log
  add column if not exists response_json jsonb;

create unique index if not exists todo_checklists_owner_local_id_uidx
  on public.todo_checklists(owner_user_id, local_id);
create unique index if not exists todo_items_owner_local_id_uidx
  on public.todo_items(owner_user_id, local_id);
create index if not exists todo_checklists_owner_remote_version_idx
  on public.todo_checklists(owner_user_id, remote_version);
create index if not exists todo_items_owner_remote_version_idx
  on public.todo_items(owner_user_id, remote_version);
create index if not exists todo_items_owner_trash_idx
  on public.todo_items(owner_user_id, trashed_at_millis)
  where trashed_at_millis is not null;
create index if not exists user_settings_owner_remote_version_idx
  on public.user_settings(owner_user_id, remote_version);
create index if not exists sync_tombstones_owner_remote_version_idx
  on public.sync_tombstones(owner_user_id, remote_version);

create or replace function public.pixeldone_set_remote_version()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  new.remote_version := nextval('public.pixeldone_remote_version_seq'::regclass);
  return new;
end;
$$;

drop trigger if exists pixeldone_set_remote_version_on_todo_checklists on public.todo_checklists;
create trigger pixeldone_set_remote_version_on_todo_checklists
before insert or update on public.todo_checklists
for each row execute function public.pixeldone_set_remote_version();

drop trigger if exists pixeldone_set_remote_version_on_todo_items on public.todo_items;
create trigger pixeldone_set_remote_version_on_todo_items
before insert or update on public.todo_items
for each row execute function public.pixeldone_set_remote_version();

drop trigger if exists pixeldone_set_remote_version_on_user_settings on public.user_settings;
create trigger pixeldone_set_remote_version_on_user_settings
before insert or update on public.user_settings
for each row execute function public.pixeldone_set_remote_version();

drop trigger if exists pixeldone_set_remote_version_on_sync_tombstones on public.sync_tombstones;
create trigger pixeldone_set_remote_version_on_sync_tombstones
before insert or update on public.sync_tombstones
for each row execute function public.pixeldone_set_remote_version();

-- A shared transaction lock serializes pulls, mutation commits and cleanup.
-- This makes the single high-water cursor safe even though PostgreSQL sequences
-- themselves are not ordered by transaction commit time.
create or replace function public.pixeldone_pull_changes(p_since_version bigint default 0)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_owner uuid := auth.uid();
  v_high_water bigint;
begin
  if v_owner is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;

  perform pg_advisory_xact_lock(hashtext('pixeldone-sync-v3'));

  select greatest(
    coalesce((select max(remote_version) from public.todo_checklists where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.todo_items where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.user_settings where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.sync_tombstones where owner_user_id = v_owner), 0)
  ) into v_high_water;

  return jsonb_build_object(
    'schema_version', '3.1',
    'server_version', v_high_water,
    'checklists', coalesce((
      select jsonb_agg(to_jsonb(c) order by c.remote_version, c.sort_index, c.created_at_millis)
      from public.todo_checklists c
      where c.owner_user_id = v_owner
        and c.remote_version > coalesce(p_since_version, 0)
        and c.remote_version <= v_high_water
    ), '[]'::jsonb),
    'items', coalesce((
      select jsonb_agg(to_jsonb(i) order by i.remote_version, i.checklist_local_id, i.sort_index, i.created_at_millis)
      from public.todo_items i
      where i.owner_user_id = v_owner
        and i.remote_version > coalesce(p_since_version, 0)
        and i.remote_version <= v_high_water
    ), '[]'::jsonb),
    'settings', (
      select to_jsonb(s)
      from public.user_settings s
      where s.owner_user_id = v_owner
        and s.remote_version > coalesce(p_since_version, 0)
        and s.remote_version <= v_high_water
    ),
    'tombstones', coalesce((
      select jsonb_agg(to_jsonb(t) order by t.remote_version, t.record_type, t.local_id)
      from public.sync_tombstones t
      where t.owner_user_id = v_owner
        and t.remote_version > coalesce(p_since_version, 0)
        and t.remote_version <= v_high_water
    ), '[]'::jsonb)
  );
end;
$$;

create or replace function public.pixeldone_apply_mutation(
  p_mutation_uuid text,
  p_checklists jsonb default '[]'::jsonb,
  p_items jsonb default '[]'::jsonb,
  p_settings jsonb default null,
  p_tombstones jsonb default '[]'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_owner uuid := auth.uid();
  v_row jsonb;
  v_existing_version bigint;
  v_result jsonb;
  v_accepted_checklists jsonb := '[]'::jsonb;
  v_accepted_items jsonb := '[]'::jsonb;
  v_accepted_tombstones jsonb := '[]'::jsonb;
  v_accepted_settings jsonb := null;
  v_conflicts jsonb := '[]'::jsonb;
  v_high_water bigint;
begin
  if v_owner is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;
  if nullif(trim(p_mutation_uuid), '') is null then
    raise exception 'mutation UUID is required' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtext('pixeldone-sync-v3'));

  select response_json into v_result
  from public.sync_mutation_log
  where owner_user_id = v_owner and mutation_uuid = p_mutation_uuid;
  if v_result is not null then
    return v_result;
  end if;

  -- Tombstones are applied first and always win over active content.
  for v_row in select value from jsonb_array_elements(coalesce(p_tombstones, '[]'::jsonb)) loop
    insert into public.sync_tombstones(owner_user_id, record_type, local_id, deleted_at_millis)
    values (
      v_owner,
      v_row->>'record_type',
      v_row->>'local_id',
      (v_row->>'deleted_at_millis')::bigint
    )
    on conflict (owner_user_id, record_type, local_id) do update
    set deleted_at_millis = greatest(public.sync_tombstones.deleted_at_millis, excluded.deleted_at_millis);

    if v_row->>'record_type' = 'item' then
      delete from public.todo_items where owner_user_id = v_owner and local_id = v_row->>'local_id';
    elsif v_row->>'record_type' = 'checklist' then
      delete from public.todo_checklists where owner_user_id = v_owner and local_id = v_row->>'local_id';
    end if;

    v_accepted_tombstones := v_accepted_tombstones || jsonb_build_array((
      select to_jsonb(t) from public.sync_tombstones t
      where t.owner_user_id = v_owner
        and t.record_type = v_row->>'record_type'
        and t.local_id = v_row->>'local_id'
    ));
  end loop;

  for v_row in select value from jsonb_array_elements(coalesce(p_checklists, '[]'::jsonb)) loop
    if exists (
      select 1 from public.sync_tombstones t
      where t.owner_user_id = v_owner and t.record_type = 'checklist' and t.local_id = v_row->>'local_id'
    ) then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'record_type', 'checklist', 'local_id', v_row->>'local_id', 'message', 'tombstone_wins'
      ));
      continue;
    end if;

    select remote_version into v_existing_version from public.todo_checklists
    where owner_user_id = v_owner and local_id = v_row->>'local_id';
    if v_existing_version is not null
       and coalesce((v_row->>'remote_version')::bigint, -1) <> v_existing_version then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'record_type', 'checklist', 'local_id', v_row->>'local_id', 'message', 'remote_version_changed'
      ));
      continue;
    end if;

    insert into public.todo_checklists(
      owner_user_id, local_id, sort_index, name, created_at_millis, updated_at_millis
    ) values (
      v_owner, v_row->>'local_id', (v_row->>'sort_index')::integer, v_row->>'name',
      (v_row->>'created_at_millis')::bigint, (v_row->>'updated_at_millis')::bigint
    )
    on conflict (owner_user_id, local_id) do update set
      sort_index = excluded.sort_index,
      name = excluded.name,
      created_at_millis = excluded.created_at_millis,
      updated_at_millis = excluded.updated_at_millis;

    v_accepted_checklists := v_accepted_checklists || jsonb_build_array((
      select to_jsonb(c) from public.todo_checklists c
      where c.owner_user_id = v_owner and c.local_id = v_row->>'local_id'
    ));
  end loop;

  for v_row in select value from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) loop
    if exists (
      select 1 from public.sync_tombstones t
      where t.owner_user_id = v_owner and t.record_type = 'item' and t.local_id = v_row->>'local_id'
    ) then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'record_type', 'item', 'local_id', v_row->>'local_id', 'message', 'tombstone_wins'
      ));
      continue;
    end if;

    select remote_version into v_existing_version from public.todo_items
    where owner_user_id = v_owner and local_id = v_row->>'local_id';
    if v_existing_version is not null
       and coalesce((v_row->>'remote_version')::bigint, -1) <> v_existing_version then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'record_type', 'item', 'local_id', v_row->>'local_id', 'message', 'remote_version_changed'
      ));
      continue;
    end if;

    insert into public.todo_items(
      owner_user_id, local_id, checklist_local_id, sort_index, title, priority,
      due_at_millis, completed, created_at_millis, updated_at_millis,
      reminder_repeat, image_local_name, image_remote_path, image_sync_state,
      trashed_from_checklist_id, trashed_from_checklist_name, trashed_at_millis
    ) values (
      v_owner, v_row->>'local_id', v_row->>'checklist_local_id', (v_row->>'sort_index')::integer,
      v_row->>'title', v_row->>'priority', (v_row->>'due_at_millis')::bigint,
      (v_row->>'completed')::boolean, (v_row->>'created_at_millis')::bigint,
      (v_row->>'updated_at_millis')::bigint, v_row->>'reminder_repeat',
      v_row->>'image_local_name', v_row->>'image_remote_path', v_row->>'image_sync_state',
      v_row->>'trashed_from_checklist_id', v_row->>'trashed_from_checklist_name',
      nullif(v_row->>'trashed_at_millis', '')::bigint
    )
    on conflict (owner_user_id, local_id) do update set
      checklist_local_id = excluded.checklist_local_id,
      sort_index = excluded.sort_index,
      title = excluded.title,
      priority = excluded.priority,
      due_at_millis = excluded.due_at_millis,
      completed = excluded.completed,
      created_at_millis = excluded.created_at_millis,
      updated_at_millis = excluded.updated_at_millis,
      reminder_repeat = excluded.reminder_repeat,
      image_local_name = excluded.image_local_name,
      image_remote_path = excluded.image_remote_path,
      image_sync_state = excluded.image_sync_state,
      trashed_from_checklist_id = excluded.trashed_from_checklist_id,
      trashed_from_checklist_name = excluded.trashed_from_checklist_name,
      trashed_at_millis = excluded.trashed_at_millis;

    v_accepted_items := v_accepted_items || jsonb_build_array((
      select to_jsonb(i) from public.todo_items i
      where i.owner_user_id = v_owner and i.local_id = v_row->>'local_id'
    ));
  end loop;

  if p_settings is not null and p_settings <> 'null'::jsonb then
    select remote_version into v_existing_version from public.user_settings where owner_user_id = v_owner;
    if v_existing_version is not null
       and coalesce((p_settings->>'remote_version')::bigint, -1) <> v_existing_version then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'record_type', 'settings', 'local_id', v_owner::text, 'message', 'remote_version_changed'
      ));
    else
      insert into public.user_settings(owner_user_id, language_mode, updated_at_millis)
      values (v_owner, p_settings->>'language_mode', (p_settings->>'updated_at_millis')::bigint)
      on conflict (owner_user_id) do update set
        language_mode = excluded.language_mode,
        updated_at_millis = excluded.updated_at_millis;
      select to_jsonb(s) into v_accepted_settings from public.user_settings s where s.owner_user_id = v_owner;
    end if;
  end if;

  select greatest(
    coalesce((select max(remote_version) from public.todo_checklists where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.todo_items where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.user_settings where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.sync_tombstones where owner_user_id = v_owner), 0)
  ) into v_high_water;

  v_result := jsonb_build_object(
    'schema_version', '3.1',
    'server_version', v_high_water,
    'accepted', jsonb_build_object('checklists', v_accepted_checklists, 'items', v_accepted_items),
    'settings', v_accepted_settings,
    'tombstones', v_accepted_tombstones,
    'conflicts', v_conflicts
  );

  insert into public.sync_mutation_log(owner_user_id, mutation_uuid, response_json)
  values (v_owner, p_mutation_uuid, v_result)
  on conflict (owner_user_id, mutation_uuid) do update set response_json = excluded.response_json;
  return v_result;
end;
$$;

-- Exactly 30 * 24 hours after entering Trash, content is scrubbed and replaced
-- by an indefinitely retained minimal tombstone. Restoring clears trashed_at_millis;
-- moving to Trash again writes a new timestamp and therefore restarts the timer.
create or replace function public.pixeldone_cleanup_expired_trash()
returns bigint
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_deleted bigint;
begin
  perform pg_advisory_xact_lock(hashtext('pixeldone-sync-v3'));

  with expired as (
    select owner_user_id, local_id, floor(extract(epoch from clock_timestamp()) * 1000)::bigint as deleted_at_millis
    from public.todo_items
    where trashed_at_millis is not null
      and trashed_at_millis <= floor(extract(epoch from clock_timestamp() - interval '30 days') * 1000)::bigint
  ), inserted as (
    insert into public.sync_tombstones(owner_user_id, record_type, local_id, deleted_at_millis)
    select owner_user_id, 'item', local_id, deleted_at_millis from expired
    on conflict (owner_user_id, record_type, local_id) do update
    set deleted_at_millis = greatest(public.sync_tombstones.deleted_at_millis, excluded.deleted_at_millis)
    returning owner_user_id, local_id
  )
  delete from public.todo_items i
  using inserted x
  where i.owner_user_id = x.owner_user_id and i.local_id = x.local_id;

  get diagnostics v_deleted = row_count;
  return v_deleted;
end;
$$;

-- Replace the schedule idempotently. pg_cron is expected to use UTC.
do $$
declare
  v_job_id bigint;
begin
  select jobid into v_job_id from cron.job where jobname = 'pixeldone-expired-trash-daily';
  if v_job_id is not null then
    perform cron.unschedule(v_job_id);
  end if;
  perform cron.schedule(
    'pixeldone-expired-trash-daily',
    '15 3 * * *',
    'select public.pixeldone_cleanup_expired_trash();'
  );
end;
$$;

alter table public.pixeldone_schema_metadata enable row level security;
alter table public.todo_checklists enable row level security;
alter table public.todo_items enable row level security;
alter table public.user_settings enable row level security;
alter table public.sync_tombstones enable row level security;
alter table public.sync_mutation_log enable row level security;

-- Remove legacy direct-write policies. The 3.1 client writes only through the
-- security-definer transaction RPC, but needs SELECT for Realtime filtering.
do $$
declare p record;
begin
  for p in select schemaname, tablename, policyname from pg_policies
           where schemaname = 'public'
             and tablename in ('todo_checklists','todo_items','user_settings','sync_tombstones','sync_mutation_log')
  loop
    execute format('drop policy if exists %I on %I.%I', p.policyname, p.schemaname, p.tablename);
  end loop;
end;
$$;

create policy pixeldone_checklists_select_own on public.todo_checklists
for select using (owner_user_id = auth.uid());
create policy pixeldone_items_select_own on public.todo_items
for select using (owner_user_id = auth.uid());
create policy pixeldone_settings_select_own on public.user_settings
for select using (owner_user_id = auth.uid());
create policy pixeldone_tombstones_select_own on public.sync_tombstones
for select using (owner_user_id = auth.uid());

revoke all on public.todo_checklists, public.todo_items, public.user_settings,
  public.sync_tombstones, public.sync_mutation_log from anon, authenticated;
grant select on public.todo_checklists, public.todo_items, public.user_settings,
  public.sync_tombstones to authenticated;
revoke all on function public.pixeldone_pull_changes(bigint) from public, anon;
revoke all on function public.pixeldone_apply_mutation(text, jsonb, jsonb, jsonb, jsonb) from public, anon;
revoke all on function public.pixeldone_cleanup_expired_trash() from public, anon, authenticated;
grant execute on function public.pixeldone_pull_changes(bigint) to authenticated;
grant execute on function public.pixeldone_apply_mutation(text, jsonb, jsonb, jsonb, jsonb) to authenticated;

-- Add all change-bearing tables to Supabase Realtime without failing if already present.
do $$
declare t text;
begin
  foreach t in array array['todo_checklists','todo_items','user_settings','sync_tombstones'] loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end;
$$;

commit;

-- Manual verification output. PixelDone 3.1 requires schema_version = 3.1,
-- both RPCs, all four Realtime tables, and one active cron job.
select schema_version, updated_at from public.pixeldone_schema_metadata where singleton;
select routine_name from information_schema.routines
where routine_schema = 'public'
  and routine_name in ('pixeldone_pull_changes','pixeldone_apply_mutation','pixeldone_cleanup_expired_trash')
order by routine_name;
select tablename from pg_publication_tables
where pubname = 'supabase_realtime' and schemaname = 'public'
  and tablename in ('todo_checklists','todo_items','user_settings','sync_tombstones')
order by tablename;
select jobid, jobname, schedule, command, active
from cron.job where jobname = 'pixeldone-expired-trash-daily';
