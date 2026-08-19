\set ON_ERROR_STOP off
\set A_ID '11111111-1111-4111-8111-111111111111'
\set B_ID '55555555-5555-4555-8555-555555555555'
\set C '22222222-2222-4222-8222-222222222222'
\set ACT '33333333-3333-4333-8333-333333333333'
\set S '44444444-4444-4444-8444-444444444444'
\set JWT_A '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}'
\set JWT_B '{"sub":"55555555-5555-4555-8555-555555555555","role":"authenticated"}'

INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES (:'A_ID','00000000-0000-0000-0000-000000000000','authenticated','authenticated','a@t.com','x',now(),now(),now()),
       (:'B_ID','00000000-0000-0000-0000-000000000000','authenticated','authenticated','b@t.com','x',now(),now(),now());
INSERT INTO profiles (id, display_name) VALUES (:'A_ID','Ana'), (:'B_ID','Bruno');
INSERT INTO communities (id, name, invite_code, created_by, stripe_account_id, stripe_charges_enabled)
VALUES (:'C','Voley','INVITE01',:'A_ID','acct_test',true);
-- Ana ya es admin: la mete el trigger handle_new_community. Solo hay que anadir a Bruno.
INSERT INTO community_members (community_id, user_id, role) VALUES (:'C',:'B_ID','user');
INSERT INTO activities (id, community_id, name, datetime, duration_minutes, slot_mode, created_by, price_cents)
VALUES (:'ACT',:'C','Entreno',now()+interval '2 days',60,'limited',:'A_ID',650);
INSERT INTO slots (id, activity_id, sort_order) VALUES (:'S',:'ACT',0);
SELECT 'montaje: ' || count(*)::text || ' miembros' FROM community_members WHERE community_id=:'C';

\echo ''
\echo '--- 1. Ana abre el cobro -> payment_id + importe'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JWT_A';
SELECT begin_slot_payment(:'S') ->> 'payment_id' AS pago, begin_slot_payment(:'S') ->> 'resumed' AS reanudado;
COMMIT;
\echo '--- 2. plaza retenida por Ana'
SELECT status, reserved_by = :'A_ID' AS de_ana, hold_expires_at > now() AS retencion_viva FROM slots WHERE id=:'S';

\echo ''
\echo '--- 3. Bruno (miembro) sobre la plaza retenida -> slot_not_claimable'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JWT_B';
SELECT begin_slot_payment(:'S');
COMMIT;

\echo '--- 4. UN solo cobro abierto pese a todos los intentos'
SELECT count(*) AS pendientes FROM payments WHERE status='pending';

\echo ''
\echo '--- 5. Actividad GRATUITA -> activity_is_free (nunca pasa por Stripe)'
UPDATE activities SET price_cents = NULL WHERE id=:'ACT';
DELETE FROM payments; UPDATE slots SET status='available', reserved_by=NULL, hold_expires_at=NULL WHERE id=:'S';
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JWT_A';
SELECT begin_slot_payment(:'S');
COMMIT;

\echo ''
\echo '--- 6. Comunidad SIN cobros activos -> payments_not_enabled (cae a cobro manual)'
UPDATE activities SET price_cents = 650 WHERE id=:'ACT';
UPDATE communities SET stripe_charges_enabled = false WHERE id=:'C';
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JWT_A';
SELECT begin_slot_payment(:'S');
COMMIT;
