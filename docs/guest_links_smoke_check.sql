-- ============================================================================
-- Guest activity links — smoke check
-- Pégalo en Supabase Dashboard → SQL Editor. Lee resultados de arriba a abajo.
-- Ningún bloque escribe datos; solo SELECTs.
-- ============================================================================

-- 1) Migraciones aplicadas — SOLO si usas Supabase CLI (tabla schema_migrations)
--    Si has lanzado los .sql a mano esta tabla no existe; salta a la 2.
-- select version
-- from supabase_migrations.schema_migrations
-- where version like '02%'
-- order by version;

-- 2) Funciones RPC presentes (espera las 6)
select proname
from pg_proc
where proname in (
    'generate_activity_guest_link',
    'get_activity_guest_preview',
    'request_guest_slot',
    'approve_guest_request',
    'reject_guest_request',
    'list_pending_guest_requests'
)
order by proname;

-- 3) Columnas/estado nuevos en slots (espera is_guest bool y status incluye 'pending')
select column_name, data_type, column_default
from information_schema.columns
where table_name = 'slots' and column_name in ('is_guest','status');

select pg_get_constraintdef(c.oid)
from pg_constraint c
join pg_class t on t.oid = c.conrelid
where t.relname = 'slots' and c.conname = 'slots_status_check';

-- 4) Tabla de solicitudes existe
select column_name, data_type
from information_schema.columns
where table_name = 'activity_guest_requests'
order by ordinal_position;

-- 5) Anonymous provider habilitado (no es consultable por SQL — verificar en
--    Dashboard → Authentication → Providers → Anonymous = ON)

-- 6) Trigger handle_new_user actualizado (default 'Invitado' para anónimos)
select pg_get_functiondef('handle_new_user'::regproc);

-- 7) RLS policy de admin para ver perfiles invitados (commit c31ed46)
select polname, polcmd, pg_get_expr(polqual, polrelid) as using_expr
from pg_policy
where polrelid = 'profiles'::regclass;

-- ============================================================================
-- Tras hacer una prueba real, estas queries inspeccionan el estado.
-- Sustituye <ACTIVITY_ID> antes de ejecutar.
-- ============================================================================

-- 8) Tras "Solicitar plaza" desde la landing: debe existir 1 fila pending
-- select id, guest_name, guest_phone, position, status, created_at
-- from activity_guest_requests
-- where activity_id = '<ACTIVITY_ID>'
-- order by created_at desc;

-- select id, status, is_guest, profile_id
-- from slots
-- where activity_id = '<ACTIVITY_ID>' and is_guest = true
-- order by created_at desc;

-- 9) Tras aprobar: el slot pasa a 'reserved' (o el que corresponda) y el
--    profile del invitado tiene el nombre que envió (no 'Invitado')
-- select s.id, s.status, p.display_name
-- from slots s join profiles p on p.id = s.profile_id
-- where s.activity_id = '<ACTIVITY_ID>' and s.is_guest = true;

-- 10) Tras rechazar: el slot del invitado se borra/libera y el aforo sube
-- select count(*) filter (where status in ('reserved','paid','pending')) as ocupados,
--        count(*) as total
-- from slots where activity_id = '<ACTIVITY_ID>';
