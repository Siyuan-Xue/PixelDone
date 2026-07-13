-- PixelDone 3.2.0 hard schema cutover.
-- Prerequisite: PixelDone schema 3.1 is installed.
-- Prerequisites:
--   1. Back up PostgreSQL and the Storage volume.
--   2. Run pixeldone-supabase-3.2.0-storage-policies.sql as
--      supabase_storage_admin. Supabase intentionally owns storage.objects
--      with that service role, not the Studio SQL Editor role.
-- Run this file once as the owner of the PixelDone public-schema objects.

begin;

do $$
declare
  v_schema text;
  v_storage_policy_count integer;
begin
  select schema_version into v_schema
  from public.pixeldone_schema_metadata
  where singleton;
  if v_schema is distinct from '3.1' then
    raise exception 'PixelDone 3.2 migration requires schema 3.1; found %', coalesce(v_schema, '<missing>');
  end if;

  select count(*) into v_storage_policy_count
  from pg_policies
  where schemaname = 'storage'
    and tablename = 'objects'
    and policyname in (
      'pixeldone_images_select_own',
      'pixeldone_images_insert_own',
      'pixeldone_images_update_own',
      'pixeldone_images_delete_own'
    );

  if v_storage_policy_count <> 4 then
    raise exception using
      errcode = '42501',
      message = 'PixelDone Storage policies are missing',
      hint = 'Run docs/pixeldone-supabase-3.2.0-storage-policies.sql as supabase_storage_admin, then rerun this migration.';
  end if;
end;
$$;

-- Private, original-byte image storage. Clients additionally validate content
-- signatures instead of trusting extensions or request MIME headers.
insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values (
  'pixeldone-todo-images',
  'pixeldone-todo-images',
  false,
  10485760,
  array['image/jpeg', 'image/png', 'image/webp']::text[]
)
on conflict (id) do update set
  name = excluded.name,
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create table if not exists public.todo_attachments (
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  todo_local_id text not null,
  attachment_id uuid,
  object_path text,
  content_sha256 text,
  content_type text,
  byte_size bigint,
  updated_at_millis bigint not null,
  deleted_at_millis bigint,
  remote_version bigint not null default nextval('public.pixeldone_remote_version_seq'::regclass),
  primary key (owner_user_id, todo_local_id),
  constraint todo_attachments_todo_fk
    foreign key (owner_user_id, todo_local_id)
    references public.todo_items(owner_user_id, local_id)
    on delete cascade,
  constraint todo_attachments_state_check check (
    (
      deleted_at_millis is null and
      attachment_id is not null and object_path is not null and
      content_sha256 is not null and content_type is not null and byte_size is not null
    ) or (
      deleted_at_millis is not null and
      attachment_id is null and object_path is null and
      content_sha256 is null and content_type is null and byte_size is null
    )
  ),
  constraint todo_attachments_hash_check
    check (content_sha256 is null or content_sha256 ~ '^[0-9a-f]{64}$'),
  constraint todo_attachments_type_check
    check (content_type is null or content_type in ('image/jpeg', 'image/png', 'image/webp')),
  constraint todo_attachments_size_check
    check (byte_size is null or byte_size between 1 and 10485760)
);

create table if not exists public.todo_image_cleanup_queue (
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  object_path text not null,
  queued_at_millis bigint not null,
  primary key (owner_user_id, object_path)
);

create index if not exists todo_attachments_owner_remote_version_idx
  on public.todo_attachments(owner_user_id, remote_version);
create index if not exists todo_image_cleanup_owner_queued_idx
  on public.todo_image_cleanup_queue(owner_user_id, queued_at_millis);

drop trigger if exists pixeldone_set_remote_version_on_todo_attachments on public.todo_attachments;
create trigger pixeldone_set_remote_version_on_todo_attachments
before insert or update on public.todo_attachments
for each row execute function public.pixeldone_set_remote_version();

alter table public.todo_attachments enable row level security;
alter table public.todo_image_cleanup_queue enable row level security;

drop policy if exists pixeldone_attachments_select_own on public.todo_attachments;
create policy pixeldone_attachments_select_own on public.todo_attachments
for select to authenticated using (owner_user_id = (select auth.uid()));

-- Attachment rows are changed only by the transaction RPC. Cleanup rows are
-- returned and acknowledged by the RPC and are never directly writable.
revoke all on public.todo_attachments, public.todo_image_cleanup_queue from anon, authenticated;
grant select on public.todo_attachments to authenticated;

-- Cached 3.1 mutation responses contain the old response shape. The mutation
-- UUID remains client-generated and a 3.2 client will safely resubmit after a
-- fresh pull.
delete from public.sync_mutation_log;

-- Legacy pull entry point. Old 3.1 clients read this response, detect 3.2, and
-- stop before they can push an image-clearing item payload.
create or replace function public.pixeldone_pull_changes(p_since_version bigint default 0)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_owner uuid := auth.uid();
begin
  if v_owner is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;
  return jsonb_build_object(
    'schema_version', '3.2',
    'server_version', 0,
    'checklists', '[]'::jsonb,
    'items', '[]'::jsonb,
    'attachments', '[]'::jsonb,
    'settings', null,
    'tombstones', '[]'::jsonb,
    'image_cleanup_paths', '[]'::jsonb
  );
end;
$$;

create or replace function public.pixeldone_pull_changes(
  p_since_version bigint,
  p_client_schema_version text
)
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
  if p_client_schema_version is distinct from '3.2' then
    raise exception 'PixelDone client schema 3.2 is required' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtext('pixeldone-sync-v3'));

  select greatest(
    coalesce((select max(remote_version) from public.todo_checklists where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.todo_items where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.todo_attachments where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.user_settings where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.sync_tombstones where owner_user_id = v_owner), 0)
  ) into v_high_water;

  return jsonb_build_object(
    'schema_version', '3.2',
    'server_version', v_high_water,
    'checklists', coalesce((
      select jsonb_agg(to_jsonb(c) order by c.remote_version, c.sort_index, c.created_at_millis)
      from public.todo_checklists c
      where c.owner_user_id = v_owner
        and c.remote_version > coalesce(p_since_version, 0)
        and c.remote_version <= v_high_water
    ), '[]'::jsonb),
    'items', coalesce((
      select jsonb_agg(
        to_jsonb(i) - 'image_local_name' - 'image_remote_path' - 'image_sync_state'
        order by i.remote_version, i.checklist_local_id, i.sort_index, i.created_at_millis
      )
      from public.todo_items i
      where i.owner_user_id = v_owner
        and i.remote_version > coalesce(p_since_version, 0)
        and i.remote_version <= v_high_water
    ), '[]'::jsonb),
    'attachments', coalesce((
      select jsonb_agg(to_jsonb(a) order by a.remote_version, a.todo_local_id)
      from public.todo_attachments a
      where a.owner_user_id = v_owner
        and a.remote_version > coalesce(p_since_version, 0)
        and a.remote_version <= v_high_water
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
    ), '[]'::jsonb),
    'image_cleanup_paths', coalesce((
      select jsonb_agg(q.object_path order by q.queued_at_millis, q.object_path)
      from public.todo_image_cleanup_queue q
      where q.owner_user_id = v_owner
    ), '[]'::jsonb)
  );
end;
$$;

-- Legacy apply entry point. It intentionally never mutates schema 3.2.
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
begin
  raise exception 'PixelDone client schema 3.2 is required' using errcode = '22023';
end;
$$;

create or replace function public.pixeldone_apply_mutation(
  p_mutation_uuid text,
  p_client_schema_version text,
  p_checklists jsonb,
  p_items jsonb,
  p_attachments jsonb,
  p_settings jsonb,
  p_tombstones jsonb,
  p_cleaned_image_paths jsonb
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
  v_existing_path text;
  v_expected_prefix text;
  v_result jsonb;
  v_accepted_checklists jsonb := '[]'::jsonb;
  v_accepted_items jsonb := '[]'::jsonb;
  v_accepted_attachments jsonb := '[]'::jsonb;
  v_accepted_tombstones jsonb := '[]'::jsonb;
  v_accepted_settings jsonb := null;
  v_conflicts jsonb := '[]'::jsonb;
  v_high_water bigint;
begin
  if v_owner is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;
  if p_client_schema_version is distinct from '3.2' then
    raise exception 'PixelDone client schema 3.2 is required' using errcode = '22023';
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

  for v_row in select value from jsonb_array_elements(coalesce(p_cleaned_image_paths, '[]'::jsonb)) loop
    delete from public.todo_image_cleanup_queue
    where owner_user_id = v_owner and object_path = (v_row #>> '{}');
  end loop;

  -- Permanent content tombstones win. Queue the current Storage object before
  -- deleting an item so the physical object can be removed by any client.
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
      insert into public.todo_image_cleanup_queue(owner_user_id, object_path, queued_at_millis)
      select v_owner, a.object_path, (v_row->>'deleted_at_millis')::bigint
      from public.todo_attachments a
      where a.owner_user_id = v_owner
        and a.todo_local_id = v_row->>'local_id'
        and a.deleted_at_millis is null
      on conflict (owner_user_id, object_path) do nothing;
      delete from public.todo_items where owner_user_id = v_owner and local_id = v_row->>'local_id';
    elsif v_row->>'record_type' = 'checklist' then
      insert into public.todo_image_cleanup_queue(owner_user_id, object_path, queued_at_millis)
      select v_owner, a.object_path, (v_row->>'deleted_at_millis')::bigint
      from public.todo_attachments a
      join public.todo_items i
        on i.owner_user_id = a.owner_user_id and i.local_id = a.todo_local_id
      where i.owner_user_id = v_owner
        and i.checklist_local_id = v_row->>'local_id'
        and a.deleted_at_millis is null
      on conflict (owner_user_id, object_path) do nothing;
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
      reminder_repeat, trashed_from_checklist_id, trashed_from_checklist_name, trashed_at_millis
    ) values (
      v_owner, v_row->>'local_id', v_row->>'checklist_local_id', (v_row->>'sort_index')::integer,
      v_row->>'title', v_row->>'priority', (v_row->>'due_at_millis')::bigint,
      (v_row->>'completed')::boolean, (v_row->>'created_at_millis')::bigint,
      (v_row->>'updated_at_millis')::bigint, v_row->>'reminder_repeat',
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
      trashed_from_checklist_id = excluded.trashed_from_checklist_id,
      trashed_from_checklist_name = excluded.trashed_from_checklist_name,
      trashed_at_millis = excluded.trashed_at_millis;

    v_accepted_items := v_accepted_items || jsonb_build_array((
      select to_jsonb(i) - 'image_local_name' - 'image_remote_path' - 'image_sync_state'
      from public.todo_items i
      where i.owner_user_id = v_owner and i.local_id = v_row->>'local_id'
    ));
  end loop;

  for v_row in select value from jsonb_array_elements(coalesce(p_attachments, '[]'::jsonb)) loop
    if not exists (
      select 1 from public.todo_items i
      where i.owner_user_id = v_owner and i.local_id = v_row->>'todo_local_id'
    ) then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'record_type', 'attachment', 'local_id', v_row->>'todo_local_id', 'message', 'parent_missing'
      ));
      continue;
    end if;

    select remote_version, object_path into v_existing_version, v_existing_path
    from public.todo_attachments
    where owner_user_id = v_owner and todo_local_id = v_row->>'todo_local_id';
    if v_existing_version is not null
       and coalesce((v_row->>'remote_version')::bigint, -1) <> v_existing_version then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'record_type', 'attachment', 'local_id', v_row->>'todo_local_id', 'message', 'remote_version_changed'
      ));
      continue;
    end if;

    if nullif(v_row->>'deleted_at_millis', '') is null then
      v_expected_prefix := v_owner::text || '/' || (v_row->>'todo_local_id') || '/';
      if v_row->>'object_path' not like (v_expected_prefix || '%') ||
         position('..' in (v_row->>'object_path')) > 0 ||
         (v_row->>'content_sha256') !~ '^[0-9a-f]{64}$' ||
         (v_row->>'content_type') not in ('image/jpeg', 'image/png', 'image/webp') ||
         (v_row->>'byte_size')::bigint not between 1 and 10485760 then
        raise exception 'invalid PixelDone attachment descriptor' using errcode = '22023';
      end if;

      insert into public.todo_attachments(
        owner_user_id, todo_local_id, attachment_id, object_path, content_sha256,
        content_type, byte_size, updated_at_millis, deleted_at_millis
      ) values (
        v_owner, v_row->>'todo_local_id', (v_row->>'attachment_id')::uuid,
        v_row->>'object_path', v_row->>'content_sha256', v_row->>'content_type',
        (v_row->>'byte_size')::bigint, (v_row->>'updated_at_millis')::bigint, null
      )
      on conflict (owner_user_id, todo_local_id) do update set
        attachment_id = excluded.attachment_id,
        object_path = excluded.object_path,
        content_sha256 = excluded.content_sha256,
        content_type = excluded.content_type,
        byte_size = excluded.byte_size,
        updated_at_millis = excluded.updated_at_millis,
        deleted_at_millis = null;

      delete from public.todo_image_cleanup_queue
      where owner_user_id = v_owner and object_path = v_row->>'object_path';
    else
      insert into public.todo_attachments(
        owner_user_id, todo_local_id, attachment_id, object_path, content_sha256,
        content_type, byte_size, updated_at_millis, deleted_at_millis
      ) values (
        v_owner, v_row->>'todo_local_id', null, null, null, null, null,
        (v_row->>'updated_at_millis')::bigint,
        (v_row->>'deleted_at_millis')::bigint
      )
      on conflict (owner_user_id, todo_local_id) do update set
        attachment_id = null,
        object_path = null,
        content_sha256 = null,
        content_type = null,
        byte_size = null,
        updated_at_millis = excluded.updated_at_millis,
        deleted_at_millis = excluded.deleted_at_millis;
    end if;

    if v_existing_path is not null and v_existing_path is distinct from (v_row->>'object_path') then
      insert into public.todo_image_cleanup_queue(owner_user_id, object_path, queued_at_millis)
      values (v_owner, v_existing_path, (v_row->>'updated_at_millis')::bigint)
      on conflict (owner_user_id, object_path) do nothing;
    end if;

    v_accepted_attachments := v_accepted_attachments || jsonb_build_array((
      select to_jsonb(a) from public.todo_attachments a
      where a.owner_user_id = v_owner and a.todo_local_id = v_row->>'todo_local_id'
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
    coalesce((select max(remote_version) from public.todo_attachments where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.user_settings where owner_user_id = v_owner), 0),
    coalesce((select max(remote_version) from public.sync_tombstones where owner_user_id = v_owner), 0)
  ) into v_high_water;

  v_result := jsonb_build_object(
    'schema_version', '3.2',
    'server_version', v_high_water,
    'accepted', jsonb_build_object(
      'checklists', v_accepted_checklists,
      'items', v_accepted_items,
      'attachments', v_accepted_attachments
    ),
    'settings', v_accepted_settings,
    'tombstones', v_accepted_tombstones,
    'conflicts', v_conflicts,
    'image_cleanup_paths', coalesce((
      select jsonb_agg(q.object_path order by q.queued_at_millis, q.object_path)
      from public.todo_image_cleanup_queue q
      where q.owner_user_id = v_owner
    ), '[]'::jsonb)
  );

  insert into public.sync_mutation_log(owner_user_id, mutation_uuid, response_json)
  values (v_owner, p_mutation_uuid, v_result)
  on conflict (owner_user_id, mutation_uuid) do update set response_json = excluded.response_json;
  return v_result;
end;
$$;

-- Queue Storage paths before the existing 30-day trash job removes item rows.
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
    select owner_user_id, local_id,
           floor(extract(epoch from clock_timestamp()) * 1000)::bigint as deleted_at_millis
    from public.todo_items
    where trashed_at_millis is not null
      and trashed_at_millis <= floor(extract(epoch from clock_timestamp() - interval '30 days') * 1000)::bigint
  )
  insert into public.todo_image_cleanup_queue(owner_user_id, object_path, queued_at_millis)
  select e.owner_user_id, a.object_path, e.deleted_at_millis
  from expired e
  join public.todo_attachments a
    on a.owner_user_id = e.owner_user_id and a.todo_local_id = e.local_id
  where a.deleted_at_millis is null
  on conflict (owner_user_id, object_path) do nothing;

  with expired as (
    select owner_user_id, local_id,
           floor(extract(epoch from clock_timestamp()) * 1000)::bigint as deleted_at_millis
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

revoke all on function public.pixeldone_pull_changes(bigint) from public, anon;
revoke all on function public.pixeldone_pull_changes(bigint, text) from public, anon;
revoke all on function public.pixeldone_apply_mutation(text, jsonb, jsonb, jsonb, jsonb) from public, anon;
revoke all on function public.pixeldone_apply_mutation(text, text, jsonb, jsonb, jsonb, jsonb, jsonb, jsonb) from public, anon;
grant execute on function public.pixeldone_pull_changes(bigint) to authenticated;
grant execute on function public.pixeldone_pull_changes(bigint, text) to authenticated;
grant execute on function public.pixeldone_apply_mutation(text, jsonb, jsonb, jsonb, jsonb) to authenticated;
grant execute on function public.pixeldone_apply_mutation(text, text, jsonb, jsonb, jsonb, jsonb, jsonb, jsonb) to authenticated;

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'todo_attachments'
  ) then
    alter publication supabase_realtime add table public.todo_attachments;
  end if;
end;
$$;

update public.pixeldone_schema_metadata
set schema_version = '3.2', updated_at = now()
where singleton;

notify pgrst, 'reload schema';

commit;

-- Expected verification: schema 3.2, private 10 MiB bucket, five Realtime
-- tables, both RPC overload pairs, four Storage policies and one trash cron.
select schema_version, updated_at
from public.pixeldone_schema_metadata where singleton;

select id, public, file_size_limit, allowed_mime_types
from storage.buckets where id = 'pixeldone-todo-images';

select routine_name, specific_name
from information_schema.routines
where routine_schema = 'public'
  and routine_name in ('pixeldone_pull_changes', 'pixeldone_apply_mutation', 'pixeldone_cleanup_expired_trash')
order by routine_name, specific_name;

select tablename
from pg_publication_tables
where pubname = 'supabase_realtime' and schemaname = 'public'
  and tablename in ('todo_checklists', 'todo_items', 'todo_attachments', 'user_settings', 'sync_tombstones')
order by tablename;

select policyname, cmd, roles
from pg_policies
where schemaname = 'storage' and tablename = 'objects'
  and policyname like 'pixeldone_images_%'
order by policyname;

select jobid, jobname, schedule, command, active
from cron.job where jobname = 'pixeldone-expired-trash-daily';
