-- PixelDone 3.2.8 attachment mutation hotfix.
-- Run once as the owner of public.pixeldone_apply_mutation after backing up PostgreSQL.
-- The remote contract remains 3.2. This fixes the attachment validator's accidental
-- use of the PostgreSQL concatenation operator (||) where logical OR is required.

begin;

do $hotfix$
declare
  v_signature regprocedure :=
    'public.pixeldone_apply_mutation(text,text,jsonb,jsonb,jsonb,jsonb,jsonb,jsonb)'::regprocedure;
  v_definition text;
  v_broken text := $broken$if v_row->>'object_path' not like (v_expected_prefix || '%') ||
         position('..' in (v_row->>'object_path')) > 0 ||
         (v_row->>'content_sha256') !~ '^[0-9a-f]{64}$' ||
         (v_row->>'content_type') not in ('image/jpeg', 'image/png', 'image/webp') ||
         (v_row->>'byte_size')::bigint not between 1 and 10485760 then$broken$;
  v_fixed text := $fixed$if v_row->>'object_path' not like (v_expected_prefix || '%') or
         position('..' in (v_row->>'object_path')) > 0 or
         (v_row->>'content_sha256') !~ '^[0-9a-f]{64}$' or
         (v_row->>'content_type') not in ('image/jpeg', 'image/png', 'image/webp') or
         (v_row->>'byte_size')::bigint not between 1 and 10485760 then$fixed$;
begin
  select pg_get_functiondef(v_signature) into v_definition;

  if position(v_broken in v_definition) > 0 then
    execute replace(v_definition, v_broken, v_fixed);
  elsif position(v_fixed in v_definition) = 0 then
    raise exception using
      errcode = 'P0001',
      message = 'PixelDone attachment validator does not match the expected 3.2 function',
      hint = 'Stop and compare the deployed function with docs/pixeldone-supabase-3.2.0-migration.sql.';
  end if;
end;
$hotfix$;

revoke all on function public.pixeldone_apply_mutation(
  text, text, jsonb, jsonb, jsonb, jsonb, jsonb, jsonb
) from public, anon;
grant execute on function public.pixeldone_apply_mutation(
  text, text, jsonb, jsonb, jsonb, jsonb, jsonb, jsonb
) to authenticated;

notify pgrst, 'reload schema';

commit;

-- Expected verification: schema 3.2 and attachment_validator_hotfix = true.
select schema_version, updated_at
from public.pixeldone_schema_metadata
where singleton;

select
  position($broken$if v_row->>'object_path' not like (v_expected_prefix || '%') ||$broken$
    in pg_get_functiondef(
      'public.pixeldone_apply_mutation(text,text,jsonb,jsonb,jsonb,jsonb,jsonb,jsonb)'::regprocedure
    )) = 0
  and position($fixed$if v_row->>'object_path' not like (v_expected_prefix || '%') or$fixed$
    in pg_get_functiondef(
      'public.pixeldone_apply_mutation(text,text,jsonb,jsonb,jsonb,jsonb,jsonb,jsonb)'::regprocedure
    )) > 0
  as attachment_validator_hotfix;
