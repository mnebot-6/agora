// Supabase Edge Function: stripe-sweep
//
// Reconcilia cobros que se quedaron a medias. Es la red de seguridad para el caso
// "el usuario pago y nunca volvio a la app": sin esto la plaza se queda en
// pending_payment para siempre pese a estar cobrada, que es exactamente lo que paso
// la primera vez que se probo el flujo de verdad.
//
// PREGUNTA A STRIPE ANTES DE SOLTAR NADA. Liberar una retencion caducada a ciegas
// dejaria a alguien cobrado y sin plaza, que es el peor fallo posible aqui.
//
// Se invoca desde pg_cron con net.http_post, y va protegida por cabecera secreta en
// vez de JWT porque no la llama ninguna persona.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { jsonResponse, stripeCall } from "../_shared/stripe.ts";
import { applyPaymentNotCompleted, applyPaymentSucceeded } from "../_shared/payments.ts";

interface CheckoutSession {
  id: string;
  status: string;
  payment_status: string;
  payment_intent: string | null;
}

Deno.serve(async (req) => {
  const expected = Deno.env.get("SWEEP_SECRET");
  if (expected && req.headers.get("x-sweep-secret") !== expected) {
    return jsonResponse({ error: "forbidden" }, 403);
  }

  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // ponytail: se revisan TODOS los cobros abiertos, no solo los caducados. A la escala
  // de Agora son un punado y preguntar a Stripe es barato; un cobro recien abierto
  // simplemente sale "todavia abierta" y no se toca. Si algun dia hay volumen, filtrar
  // por hold_expires_at < now().
  const { data: pending } = await admin
    .from("payments")
    .select("id, checkout_session_id, connected_account_id")
    .eq("status", "pending")
    .not("checkout_session_id", "is", null)
    .limit(200);

  const results: Array<Record<string, unknown>> = [];

  for (const payment of pending ?? []) {
    try {
      const session = await stripeCall<CheckoutSession>(
        `/v1/checkout/sessions/${payment.checkout_session_id}`,
        { method: "GET", account: payment.connected_account_id ?? undefined },
      );

      if (session.payment_status === "paid") {
        const outcome = await applyPaymentSucceeded(admin, payment.id, {
          paymentIntentId: session.payment_intent ?? undefined,
        });
        results.push({ payment: payment.id, action: "confirmado", outcome });
      } else if (session.status === "expired") {
        const outcome = await applyPaymentNotCompleted(admin, payment.id, "expired");
        results.push({ payment: payment.id, action: "liberado", outcome });
      } else {
        results.push({ payment: payment.id, action: "sigue abierto" });
      }
    } catch (e) {
      results.push({ payment: payment.id, action: "error", detail: String(e) });
    }
  }

  return jsonResponse({ revisados: pending?.length ?? 0, results });
});
