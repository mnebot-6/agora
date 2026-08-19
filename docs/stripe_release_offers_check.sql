\set ON_ERROR_STOP off
\set A '11111111-1111-4111-8111-111111111111'
\set B '55555555-5555-4555-8555-555555555555'
\set C '22222222-2222-4222-8222-222222222222'
\set ACT '33333333-3333-4333-8333-333333333333'
\set S '44444444-4444-4444-8444-444444444444'
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
VALUES (:'ACT',:'C','Entreno',now()+interval '5 days',60,'limited',:'A',650);
INSERT INTO slots (id, activity_id, sort_order) VALUES (:'S',:'ACT',0);

-- Ana ya pago su plaza.
UPDATE slots SET status='paid', reserved_by=:'A', reserved_at=now() WHERE id=:'S';
INSERT INTO payments (activity_id, slot_id, user_id, community_id, amount_cents, method, status, connected_account_id)
VALUES (:'ACT',:'S',:'A',:'C',650,'stripe','succeeded','acct_test');

\echo '--- 1. Bruno se apunta a la cola'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JB';
SELECT join_substitute_queue(:'ACT');
COMMIT;

\echo '--- 2. Ana libera. LA PLAZA DEBE SEGUIR SIENDO SUYA'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JA';
SELECT release_slot(:'S');
COMMIT;
SELECT status, reserved_by=:'A' AS sigue_de_ana, released_at IS NOT NULL AS liberada,
       offered_to=:'B' AS ofertada_a_bruno, offer_expires_at > now() AS oferta_viva
FROM slots WHERE id=:'S';
SELECT status AS pago_de_ana FROM payments WHERE user_id=:'A';

\echo ''
\echo '--- 3. Un tercero NO puede reclamarla mientras esta apalabrada para Bruno'
INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('77777777-7777-4777-8777-777777777777','00000000-0000-0000-0000-000000000000','authenticated','authenticated','c@t.com','x',now(),now(),now());
INSERT INTO profiles (id, display_name) VALUES ('77777777-7777-4777-8777-777777777777','Carla');
INSERT INTO community_members (community_id, user_id, role) VALUES (:'C','77777777-7777-4777-8777-777777777777','user');
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = '{"sub":"77777777-7777-4777-8777-777777777777","role":"authenticated"}';
SELECT begin_slot_payment(:'S');
COMMIT;

\echo '--- 4. Bruno SI puede: la oferta es su turno'
BEGIN; SET LOCAL role=authenticated; SET LOCAL request.jwt.claims = :'JB';
SELECT begin_slot_payment(:'S') ->> 'payment_id' AS pago_de_bruno;
COMMIT;

\echo '--- 5. La plaza SIGUE siendo de Ana mientras Bruno no pague'
SELECT status, reserved_by=:'A' AS sigue_de_ana FROM slots WHERE id=:'S';

\echo ''
\echo '--- 6. Caducidad: se fuerza y debe salir de la cola y quedar libre'
UPDATE slots SET offer_expires_at = now() - interval '1 minute' WHERE id=:'S';
SELECT expire_substitute_offers() AS ofertas_caducadas;
SELECT offered_to IS NULL AS oferta_limpia FROM slots WHERE id=:'S';
SELECT count(*) AS bruno_sigue_en_cola FROM substitute_queue WHERE user_id=:'B';
