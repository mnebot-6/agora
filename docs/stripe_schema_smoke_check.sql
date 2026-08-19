-- ============================================================================
-- Smoke del esquema de pagos. Se ejecuta contra una base LIMPIA:
--   supabase db reset
--   docker exec -i supabase_db_Agora psql -U postgres -d postgres -f - < docs/stripe_schema_smoke_check.sql
--
-- Lo que tiene que salir:
--   1 FALLA (activities_price_positive)      6 FALLA (payments_one_pending_per_slot) <- el cerrojo
--   2 pasa                                   7 FALLA (payments_stripe_has_account)
--   3 FALLA (slots_offer_has_expiry)         8 FALLA (payments_activity_id_fkey)
--   4 pasa                                   9 FALLA (payments_community_id_fkey)
--   5 pasa                                  10 falla por activities_created_by_fkey, que es un
--                                              bug PREEXISTENTE del repo, no de esta migracion.
--                                              Ver el caso aislado del pagador puro mas abajo.
-- ============================================================================

\set ON_ERROR_STOP off
\set U '11111111-1111-4111-8111-111111111111'
\set C '22222222-2222-4222-8222-222222222222'
\set A '33333333-3333-4333-8333-333333333333'
\set S '44444444-4444-4444-8444-444444444444'

INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES (:'U','00000000-0000-0000-0000-000000000000','authenticated','authenticated','a@test.com','x',now(),now(),now());
INSERT INTO profiles (id, display_name) VALUES (:'U','Tester');
INSERT INTO communities (id, name, invite_code, created_by) VALUES (:'C','C','INV12345',:'U');
INSERT INTO activities (id, community_id, name, datetime, duration_minutes, slot_mode, created_by, price_cents)
VALUES (:'A',:'C','A',now(),60,'limited',:'U',650);
INSERT INTO slots (id, activity_id, sort_order) VALUES (:'S',:'A',0);
SELECT 'montaje: ' || count(*)::text || ' plaza(s)' FROM slots WHERE id=:'S';

\echo ''
\echo '--- 1. price_cents = 0 -> debe FALLAR'
UPDATE activities SET price_cents = 0 WHERE id=:'A';
\echo '--- 2. status pending_payment -> debe PASAR'
UPDATE slots SET status='pending_payment', hold_expires_at=now()+interval '30 min' WHERE id=:'S';
\echo '--- 3. oferta SIN caducidad -> debe FALLAR'
UPDATE slots SET offered_to=:'U' WHERE id=:'S';
\echo '--- 4. oferta CON caducidad -> debe PASAR'
UPDATE slots SET offered_to=:'U', offer_expires_at=now()+interval '6 hours' WHERE id=:'S';
\echo '--- 5. primer pago pendiente -> debe PASAR'
INSERT INTO payments (activity_id, slot_id, user_id, community_id, amount_cents, method, status, connected_account_id)
VALUES (:'A',:'S',:'U',:'C',650,'stripe','pending','acct_1');
\echo '--- 6. EL CERROJO: segundo pendiente misma plaza -> debe FALLAR'
INSERT INTO payments (activity_id, slot_id, user_id, community_id, amount_cents, method, status, connected_account_id)
VALUES (:'A',:'S',:'U',:'C',650,'stripe','pending','acct_1');
\echo '--- 7. pago stripe SIN cuenta conectada -> debe FALLAR'
INSERT INTO payments (activity_id, slot_id, user_id, community_id, amount_cents, method, status)
VALUES (:'A',NULL,:'U',:'C',650,'stripe','pending');
\echo '--- 8. borrar ACTIVIDAD con pagos -> debe FALLAR (RESTRICT)'
DELETE FROM activities WHERE id=:'A';
\echo '--- 9. borrar COMUNIDAD con pagos -> debe FALLAR (RESTRICT)'
DELETE FROM communities WHERE id=:'C';
\echo '--- 10. borrar CUENTA -> debe PASAR'
DELETE FROM auth.users WHERE id=:'U';

\echo ''
\echo '=== RESULTADO FINAL ==='
SELECT count(*) AS pagos_vivos, count(user_id) AS con_dueno, max(amount_cents) AS importe FROM payments;
SELECT status, offered_to IS NULL AS oferta_limpia, offer_expires_at IS NOT NULL AS fecha_huerfana FROM slots WHERE id=:'S';
