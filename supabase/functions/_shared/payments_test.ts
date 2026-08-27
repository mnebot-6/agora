// deno test supabase/functions/_shared/payments_test.ts
//
// Solo la tabla de estados de reembolso. Es una funcion pura y es dinero: si `pending`
// se tradujera a "refunded", Agora le diria a alguien que ya tiene su dinero de vuelta
// mientras Bizum todavia lo esta moviendo, y un `failed` posterior no lo corregiria.

import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { refundStatusToPayment } from "./payments.ts";

Deno.test("un reembolso confirmado cierra el pago", () => {
  assertEquals(refundStatusToPayment("succeeded"), "refunded");
});

Deno.test("un reembolso fallido pasa a deuda del admin, no se reintenta", () => {
  assertEquals(refundStatusToPayment("failed"), "refund_owed");
  assertEquals(refundStatusToPayment("canceled"), "refund_owed");
});

Deno.test("sin veredicto no se toca el estado: Bizum tarda hasta 5 minutos", () => {
  assertEquals(refundStatusToPayment("pending"), null);
  assertEquals(refundStatusToPayment("requires_action"), null);
});
