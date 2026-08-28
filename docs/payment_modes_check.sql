-- Comprobacion manual de los tres modos de pago.
-- Uso:  psql "$SUPABASE_DB_URL" -f docs/payment_modes_check.sql
-- Deja los datos de prueba creados; el bloque final los borra.

\set ON_ERROR_STOP off
\set A  '11111111-1111-4111-8111-111111111111'
\set B  '55555555-5555-4555-8555-555555555555'
\set AD '66666666-6666-4666-8666-666666666666'
\set C  '22222222-2222-4222-8222-222222222222'
\set ACT '33333333-3333-4333-8333-333333333333'
\set S  '44444444-4444-4444-8444-444444444444'
\set JA  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}'
\set JB  '{"sub":"55555555-5555-4555-8555-555555555555","role":"authenticated"}'
\set JAD '{"sub":"66666666-6666-4666-8666-666666666666","role":"authenticated"}'

-- --- Montaje: comunidad SIN cobrador, actividad externa de 6,50 € ----------
INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES (:'A','00000000-0000-0000-0000-000000000000','authenticated','authenticated','a@t.com','x',now(),now(),now()),
       (:'B','00000000-0000-0000-0000-000000000000','authenticated','authenticated','b@t.com','x',now(),now(),now()),
       (:'AD','00000000-0000-0000-0000-000000000000','authenticated','authenticated','ad@t.com','x',now(),now(),now());
INSERT INTO profiles (id, display_name) VALUES (:'A','Ana'), (:'B','Bruno'), (:'AD','Admin');

INSERT INTO communities (id, name, invite_code, created_by)
VALUES (:'C','Voley PM','INVITEPM',:'AD');
-- ON CONFLICT porque handle_new_community() ya mete a created_by como admin.
INSERT INTO community_members (community_id, user_id, role)
VALUES (:'C',:'AD','admin'), (:'C',:'A','user'), (:'C',:'B','user')
ON CONFLICT (community_id, user_id) DO NOTHING;

INSERT INTO activities (id, community_id, name, datetime, duration_minutes, slot_mode,
                        created_by, price_cents, payment_mode, cost_description)
VALUES (:'ACT',:'C','Entreno',now()+interval '5 days',60,'limited',
        :'AD',650,'external','Bizum al 601386047');
INSERT INTO slots (id, activity_id, sort_order) VALUES (:'S',:'ACT',0);

\echo '--- 1. Sin cobrador, el modo efectivo es external'
SELECT activity_payment_mode(:'ACT') AS modo_efectivo;
-- Esperado: external

\echo ''
\echo '--- 2. Ana reserva al instante y el admin la marca pagada'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JA';
SELECT reserve_slot(:'S') AS ana_reserva;
COMMIT;
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JAD';
SELECT mark_slot_paid(:'S') AS admin_marca;
COMMIT;
SELECT status, count(*) OVER () AS filas FROM payments WHERE slot_id=:'S';
-- Esperado: reserva t, marca t, un pago 'succeeded'

\echo ''
\echo '--- 3. Desmarcar borra el apunte y deja la plaza en reservada'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JAD';
SELECT unmark_slot_paid(:'S') AS admin_desmarca;
COMMIT;
SELECT status AS estado_plaza FROM slots WHERE id=:'S';
SELECT count(*) AS pagos_restantes FROM payments WHERE slot_id=:'S';
-- Esperado: t / reserved / 0

\echo ''
\echo '--- 4. Liberar una plaza NO pagada la suelta del todo'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JA';
SELECT release_slot(:'S') AS ana_libera;
COMMIT;
SELECT status AS estado_plaza FROM slots WHERE id=:'S';
-- Esperado: t / available

\echo ''
\echo '--- 5. Liberar una plaza PAGADA la deja en manos de su dueno'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JA';
SELECT reserve_slot(:'S');
COMMIT;
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JAD';
SELECT mark_slot_paid(:'S');
COMMIT;
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JA';
SELECT release_slot(:'S') AS ana_libera_pagada;
COMMIT;
SELECT status, reserved_by=:'A' AS sigue_de_ana, released_at IS NOT NULL AS liberada
FROM slots WHERE id=:'S';
SELECT status AS pago_de_ana FROM payments WHERE slot_id=:'S';
-- Esperado: paid / t / t, y el pago en awaiting_substitute

\echo ''
\echo '--- 6. Bruno la ocupa: traspaso instantaneo y deuda a devolver'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JB';
SELECT reserve_slot(:'S') AS bruno_ocupa;
COMMIT;
SELECT status, reserved_by=:'B' AS ahora_de_bruno, released_at IS NULL AS liberacion_limpia
FROM slots WHERE id=:'S';
SELECT status AS pago_de_ana FROM payments WHERE slot_id=:'S';
SELECT count(*) AS aviso_a_ana FROM notifications WHERE user_id=:'A' AND type='refund_owed';
SELECT count(*) AS aviso_al_admin FROM notifications WHERE user_id=:'AD' AND type='refund_owed';
-- Esperado: t / reserved / t / t, pago en refund_owed, y 1 aviso a cada uno

\echo ''
\echo '--- 7. Una actividad externa NO abre Checkout'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JB';
SELECT begin_slot_payment(:'S');
COMMIT;
-- Esperado: ERROR payments_not_enabled

\echo ''
\echo '--- 8. Con cobrador y modo agora, el modo efectivo cambia'
UPDATE communities SET stripe_account_id='acct_test', stripe_charges_enabled=true WHERE id=:'C';
UPDATE activities SET payment_mode='agora' WHERE id=:'ACT';
SELECT activity_payment_mode(:'ACT') AS modo_efectivo;
UPDATE communities SET stripe_charges_enabled=false WHERE id=:'C';
SELECT activity_payment_mode(:'ACT') AS modo_degradado;
-- Esperado: agora, luego external

\echo ''
\echo '--- Limpieza'
DELETE FROM notifications WHERE user_id IN (:'A',:'B',:'AD');
DELETE FROM payments WHERE community_id=:'C';
DELETE FROM slots WHERE activity_id=:'ACT';
DELETE FROM activities WHERE id=:'ACT';
DELETE FROM community_members WHERE community_id=:'C';
DELETE FROM communities WHERE id=:'C';
DELETE FROM auth.users WHERE id IN (:'A',:'B',:'AD');
