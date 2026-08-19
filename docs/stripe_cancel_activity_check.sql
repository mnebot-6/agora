\set ON_ERROR_STOP off
\set A '11111111-1111-4111-8111-111111111111'
\set B '55555555-5555-4555-8555-555555555555'
\set C '22222222-2222-4222-8222-222222222222'
\set ACT '33333333-3333-4333-8333-333333333333'
\set JA '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}'
\set JB '{"sub":"55555555-5555-4555-8555-555555555555","role":"authenticated"}'

INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES (:'A','00000000-0000-0000-0000-000000000000','authenticated','authenticated','a@t.com','x',now(),now(),now()),
       (:'B','00000000-0000-0000-0000-000000000000','authenticated','authenticated','b@t.com','x',now(),now(),now());
INSERT INTO profiles (id, display_name) VALUES (:'A','Ana'), (:'B','Bruno');
INSERT INTO communities (id, name, invite_code, created_by, stripe_account_id, stripe_charges_enabled)
VALUES (:'C','Voley','INVITE01',:'A','acct_test',true);
INSERT INTO community_members (community_id, user_id, role) VALUES (:'C',:'B','user');
INSERT INTO activities (id, community_id, name, datetime, duration_minutes, slot_mode, created_by, price_cents)
VALUES (:'ACT',:'C','Entreno',now()+interval '3 days',60,'limited',:'A',650);
INSERT INTO slots (id, activity_id, sort_order, status, reserved_by)
VALUES ('44444444-4444-4444-8444-444444444444',:'ACT',0,'paid',:'A'),
       ('66666666-6666-4666-8666-666666666666',:'ACT',1,'paid',:'B');
-- Ana pago por Stripe; Bruno en efectivo.
INSERT INTO payments (activity_id, slot_id, user_id, community_id, amount_cents, method, status, connected_account_id)
VALUES (:'ACT','44444444-4444-4444-8444-444444444444',:'A',:'C',650,'stripe','succeeded','acct_test');
INSERT INTO payments (activity_id, slot_id, user_id, community_id, amount_cents, method, status)
VALUES (:'ACT','66666666-6666-4666-8666-666666666666',:'B',:'C',650,'manual','succeeded');

\echo '--- 1. Vista previa: 1 automatico (650) y Bruno a mano'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JA';
SELECT jsonb_pretty(activity_cancellation_preview(:'ACT'));
COMMIT;

\echo '--- 2. Un NO admin no puede cancelar'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JB';
SELECT cancel_activity(:'ACT');
COMMIT;

\echo '--- 3. El admin cancela'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JA';
SELECT jsonb_pretty(cancel_activity(:'ACT'));
COMMIT;

\echo '--- 4. Estados resultantes'
SELECT method, status FROM payments ORDER BY method;
SELECT status AS actividad FROM activities WHERE id=:'ACT';
SELECT count(*) AS avisos_de_cancelacion FROM notifications WHERE type='activity_cancelled';
