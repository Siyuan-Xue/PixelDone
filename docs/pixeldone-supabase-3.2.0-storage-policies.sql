-- PixelDone 3.2.0 private Storage policies.
-- Run this file as supabase_storage_admin before the main 3.2.0 migration.
-- Do not change ownership of storage.objects: Supabase Storage must continue
-- to own its managed schema objects.

begin;

do $$
declare
  v_current_role text := current_user;
  v_owner text;
  v_rls_enabled boolean;
begin
  select owner.rolname, objects.relrowsecurity
    into v_owner, v_rls_enabled
  from pg_class objects
  join pg_namespace namespace on namespace.oid = objects.relnamespace
  join pg_roles owner on owner.oid = objects.relowner
  where namespace.nspname = 'storage'
    and objects.relname = 'objects'
    and objects.relkind in ('r', 'p');

  if v_owner is null then
    raise exception 'Supabase table storage.objects does not exist';
  end if;

  if v_current_role <> v_owner then
    raise exception using
      errcode = '42501',
      message = format('Run this script as the storage.objects owner %s; current role is %s', v_owner, v_current_role),
      hint = 'For the official Docker stack, connect inside supabase-db over TCP with -h 127.0.0.1 -U supabase_storage_admin; the local socket may enforce peer authentication.';
  end if;

  if not v_rls_enabled then
    raise exception using
      message = 'RLS is unexpectedly disabled on storage.objects',
      hint = 'Repair or upgrade the self-hosted Supabase Storage schema before installing PixelDone policies.';
  end if;
end;
$$;

drop policy if exists pixeldone_images_select_own on storage.objects;
drop policy if exists pixeldone_images_insert_own on storage.objects;
drop policy if exists pixeldone_images_update_own on storage.objects;
drop policy if exists pixeldone_images_delete_own on storage.objects;

-- Do not call auth.uid() here. The storage.objects owner is intentionally not
-- granted access to the managed auth schema. This is the same JWT subject
-- lookup performed by auth.uid(), expressed only with PostgreSQL settings that
-- PostgREST installs for every authenticated request.
create policy pixeldone_images_select_own on storage.objects
for select to authenticated using (
  bucket_id = 'pixeldone-todo-images' and
  (storage.foldername(name))[1] = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  ) and
  owner_id = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  )
);

create policy pixeldone_images_insert_own on storage.objects
for insert to authenticated with check (
  bucket_id = 'pixeldone-todo-images' and
  (storage.foldername(name))[1] = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  )
);

create policy pixeldone_images_update_own on storage.objects
for update to authenticated using (
  bucket_id = 'pixeldone-todo-images' and
  (storage.foldername(name))[1] = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  ) and
  owner_id = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  )
) with check (
  bucket_id = 'pixeldone-todo-images' and
  (storage.foldername(name))[1] = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  ) and
  owner_id = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  )
);

create policy pixeldone_images_delete_own on storage.objects
for delete to authenticated using (
  bucket_id = 'pixeldone-todo-images' and
  (storage.foldername(name))[1] = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  ) and
  owner_id = (
    select nullif(coalesce(
      current_setting('request.jwt.claim.sub', true),
      nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    ), '')
  )
);

commit;

select current_user as applied_by, policyname, cmd, roles
from pg_policies
where schemaname = 'storage'
  and tablename = 'objects'
  and policyname like 'pixeldone_images_%'
order by policyname;
