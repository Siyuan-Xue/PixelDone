-- PixelDone Supabase remote schema update.
-- Current client database reference: Room version 4, PixelDone versionName 3.0.3.
--
-- Run this in the Supabase SQL editor with an owner/admin role.
-- Current Android clients use only these remote tables:
--   public.todo_checklists
--   public.todo_items
--   public.sync_mutation_log
--
-- Theme/Dock settings are local-only in current clients. The local Room
-- sync_conflict_records table is also local-only and does not need a Supabase table.

create extension if not exists pgcrypto;

create sequence if not exists public.pixeldone_remote_version_seq
  as bigint
  increment by 1
  minvalue 1
  start with 1;

create table if not exists public.todo_checklists (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  local_id text not null,
  sort_index integer not null default 0,
  name text not null,
  created_at_millis bigint not null,
  updated_at_millis bigint not null,
  deleted_at_millis bigint,
  remote_version bigint not null default nextval('public.pixeldone_remote_version_seq'::regclass),
  constraint todo_checklists_owner_local_id_key unique (owner_user_id, local_id)
);

create table if not exists public.todo_items (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  local_id text not null,
  checklist_local_id text not null,
  sort_index integer not null default 0,
  title text not null,
  priority text not null,
  due_at_millis bigint not null default 0,
  completed boolean not null default false,
  created_at_millis bigint not null,
  updated_at_millis bigint not null,
  deleted_at_millis bigint,
  reminder_repeat text not null default 'NONE',
  image_local_name text,
  image_remote_path text,
  image_sync_state text not null default 'LOCAL_ONLY',
  trashed_from_checklist_id text,
  trashed_from_checklist_name text,
  trashed_at_millis bigint,
  remote_version bigint not null default nextval('public.pixeldone_remote_version_seq'::regclass),
  constraint todo_items_owner_local_id_key unique (owner_user_id, local_id)
);

create table if not exists public.sync_mutation_log (
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  mutation_uuid text not null,
  created_at timestamptz not null default now(),
  primary key (owner_user_id, mutation_uuid)
);

alter table public.todo_checklists
  add column if not exists sort_index integer not null default 0,
  add column if not exists deleted_at_millis bigint,
  add column if not exists remote_version bigint;

alter table public.todo_items
  add column if not exists sort_index integer not null default 0,
  add column if not exists deleted_at_millis bigint,
  add column if not exists reminder_repeat text not null default 'NONE',
  add column if not exists image_local_name text,
  add column if not exists image_remote_path text,
  add column if not exists image_sync_state text not null default 'LOCAL_ONLY',
  add column if not exists trashed_from_checklist_id text,
  add column if not exists trashed_from_checklist_name text,
  add column if not exists trashed_at_millis bigint,
  add column if not exists remote_version bigint;

update public.todo_checklists
set
  sort_index = coalesce(sort_index, 0),
  remote_version = coalesce(remote_version, nextval('public.pixeldone_remote_version_seq'::regclass))
where sort_index is null
  or remote_version is null;

update public.todo_items
set
  sort_index = coalesce(sort_index, 0),
  reminder_repeat = coalesce(reminder_repeat, 'NONE'),
  image_sync_state = coalesce(image_sync_state, 'LOCAL_ONLY'),
  remote_version = coalesce(remote_version, nextval('public.pixeldone_remote_version_seq'::regclass))
where sort_index is null
  or reminder_repeat is null
  or image_sync_state is null
  or remote_version is null;

select setval(
  'public.pixeldone_remote_version_seq'::regclass,
  greatest(
    1,
    coalesce((select max(remote_version) from public.todo_checklists), 0),
    coalesce((select max(remote_version) from public.todo_items), 0)
  ),
  true
);

alter table public.todo_checklists
  alter column sort_index set default 0,
  alter column sort_index set not null,
  alter column remote_version set default nextval('public.pixeldone_remote_version_seq'::regclass),
  alter column remote_version set not null;

alter table public.todo_items
  alter column sort_index set default 0,
  alter column sort_index set not null,
  alter column reminder_repeat set default 'NONE',
  alter column reminder_repeat set not null,
  alter column image_sync_state set default 'LOCAL_ONLY',
  alter column image_sync_state set not null,
  alter column remote_version set default nextval('public.pixeldone_remote_version_seq'::regclass),
  alter column remote_version set not null;

create unique index if not exists todo_checklists_owner_local_id_uidx
  on public.todo_checklists(owner_user_id, local_id);

create unique index if not exists todo_items_owner_local_id_uidx
  on public.todo_items(owner_user_id, local_id);

create index if not exists todo_checklists_owner_remote_version_idx
  on public.todo_checklists(owner_user_id, remote_version);

create index if not exists todo_checklists_owner_updated_idx
  on public.todo_checklists(owner_user_id, updated_at_millis);

create index if not exists todo_items_owner_remote_version_idx
  on public.todo_items(owner_user_id, remote_version);

create index if not exists todo_items_owner_updated_idx
  on public.todo_items(owner_user_id, updated_at_millis);

create index if not exists todo_items_owner_checklist_sort_idx
  on public.todo_items(owner_user_id, checklist_local_id, sort_index, created_at_millis);

create or replace function public.pixeldone_set_remote_version()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'UPDATE' then
    new.remote_version := nextval('public.pixeldone_remote_version_seq'::regclass);
  elsif new.remote_version is null or new.remote_version < 1 then
    new.remote_version := nextval('public.pixeldone_remote_version_seq'::regclass);
  end if;

  return new;
end;
$$;

drop trigger if exists pixeldone_set_remote_version_on_todo_checklists
  on public.todo_checklists;

create trigger pixeldone_set_remote_version_on_todo_checklists
  before insert or update on public.todo_checklists
  for each row
  execute function public.pixeldone_set_remote_version();

drop trigger if exists pixeldone_set_remote_version_on_todo_items
  on public.todo_items;

create trigger pixeldone_set_remote_version_on_todo_items
  before insert or update on public.todo_items
  for each row
  execute function public.pixeldone_set_remote_version();

alter table public.todo_checklists enable row level security;
alter table public.todo_items enable row level security;
alter table public.sync_mutation_log enable row level security;

drop policy if exists "Users can read their checklists" on public.todo_checklists;
create policy "Users can read their checklists"
  on public.todo_checklists for select
  using (owner_user_id = auth.uid());

drop policy if exists "Users can insert their checklists" on public.todo_checklists;
create policy "Users can insert their checklists"
  on public.todo_checklists for insert
  with check (owner_user_id = auth.uid());

drop policy if exists "Users can update their checklists" on public.todo_checklists;
create policy "Users can update their checklists"
  on public.todo_checklists for update
  using (owner_user_id = auth.uid())
  with check (owner_user_id = auth.uid());

drop policy if exists "Users can read their todo items" on public.todo_items;
create policy "Users can read their todo items"
  on public.todo_items for select
  using (owner_user_id = auth.uid());

drop policy if exists "Users can insert their todo items" on public.todo_items;
create policy "Users can insert their todo items"
  on public.todo_items for insert
  with check (owner_user_id = auth.uid());

drop policy if exists "Users can update their todo items" on public.todo_items;
create policy "Users can update their todo items"
  on public.todo_items for update
  using (owner_user_id = auth.uid())
  with check (owner_user_id = auth.uid());

drop policy if exists "Users can read their mutation log" on public.sync_mutation_log;
create policy "Users can read their mutation log"
  on public.sync_mutation_log for select
  using (owner_user_id = auth.uid());

drop policy if exists "Users can insert their mutation log" on public.sync_mutation_log;
create policy "Users can insert their mutation log"
  on public.sync_mutation_log for insert
  with check (owner_user_id = auth.uid());

grant usage on schema public to authenticated;
grant usage, select on sequence public.pixeldone_remote_version_seq to authenticated;
grant select, insert, update on public.todo_checklists to authenticated;
grant select, insert, update on public.todo_items to authenticated;
grant select, insert on public.sync_mutation_log to authenticated;

select
  table_name,
  column_name,
  data_type,
  is_nullable,
  column_default
from information_schema.columns
where table_schema = 'public'
  and table_name in ('todo_checklists', 'todo_items', 'sync_mutation_log')
order by table_name, ordinal_position;