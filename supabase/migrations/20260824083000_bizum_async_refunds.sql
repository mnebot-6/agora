-- Bizum reembolsa de forma ASINCRONA: crear el reembolso no es que el dinero haya
-- salido. Tarda hasta 5 minutos y puede acabar en `failed`, con el importe de vuelta
-- en el saldo del admin y el usuario sin cobrar.
--
-- Guardar el id es lo que permite CONSULTAR el reembolso en las pasadas siguientes del
-- barrido en vez de volver a crearlo. La Idempotency-Key solo vive 24 h en Stripe: un
-- segundo POST pasado ese plazo devolveria el dinero DOS VECES.
ALTER TABLE payments ADD COLUMN refund_id text;

COMMENT ON COLUMN payments.refund_id IS
  'Refund de Stripe ya creado. Si esta puesto, el barrido CONSULTA en vez de crear otro.';
