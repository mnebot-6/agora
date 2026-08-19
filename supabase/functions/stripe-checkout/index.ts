// Supabase Edge Function: stripe-checkout
//
// Cobrar una plaza. Cargos DIRECTOS sobre la cuenta conectada del admin: el dinero no
// pasa nunca por el saldo de Agora y el comercio de registro es el admin.
//
// Acciones:
//   { action: "create", slot_id }     abre el cobro y devuelve la URL de Checkout
//   { action: "sync",   payment_id }  recupera la sesion y aplica la transicion
//
// El "sync" se llama al volver por deep link y ejecuta LAS MISMAS funciones que el
// webhook. Eso hace que el webhook sea la red de seguridad y no el camino critico: el
// usuario ve el resultado al instante y, si el webhook nunca llega, no pasa nada.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import {
  appBaseUrl,
  applicationFeeCents,
  corsHeaders,
  errorResponse,
  jsonResponse,
  StripeError,
  stripeCall,
} from "../_shared/stripe.ts";
import { applyPaymentNotCompleted, applyPaymentSucceeded } from "../_shared/payments.ts";

interface CheckoutSession {
  id: string;
  url: string | null;
  status: string;
  payment_status: string;
  payment_intent: string | null;
  metadata?: Record<string, string>;
}

/** Los errores que begin_slot_payment lanza como excepcion y la app sabe traducir. */
const KNOWN_ERRORS = new Set([
  "not_authenticated",
  "slot_not_found",
  "activity_is_free",
  "payments_not_enabled",
  "not_a_member",
  "slot_not_claimable",
  "slot_offered_to_someone_else",
  "queue_priority",
  "slot_being_paid",
]);

function toKnownError(message: string): string | null {
  for (const code of KNOWN_ERRORS) if (message.includes(code)) return code;
  return null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const body = await req.json().catch(() => ({}));
    const action = body.action as string | undefined;

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return errorResponse("not_authenticated", "Falta la sesion", 401);

    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const jwt = authHeader.replace("Bearer ", "");
    const { data: userData, error: userError } = await admin.auth.getUser(jwt);
    if (userError || !userData.user) {
      return errorResponse("not_authenticated", "Sesion no valida", 401);
    }

    if (action === "create") {
      const slotId = body.slot_id as string | undefined;
      if (!slotId) return errorResponse("bad_request", "Falta slot_id");

      // Cliente con la sesion del usuario: begin_slot_payment usa auth.uid() por dentro,
      // asi que tiene que ejecutarse COMO el, no como service_role.
      const asUser = createClient(
        Deno.env.get("SUPABASE_URL")!,
        Deno.env.get("SUPABASE_ANON_KEY")!,
        { global: { headers: { Authorization: authHeader } } },
      );

      const { data: claim, error: claimError } = await asUser.rpc("begin_slot_payment", {
        p_slot_id: slotId,
      });

      if (claimError) {
        const known = toKnownError(claimError.message);
        return errorResponse(known ?? "claim_failed", claimError.message, known ? 409 : 500);
      }

      const paymentId = claim.payment_id as string;
      const amountCents = claim.amount_cents as number;
      const connectedAccount = claim.connected_account_id as string;

      // Reanudacion: ya habia un cobro abierto de esta persona. Se recupera SU sesion en
      // vez de crear una segunda, que dejaria dos sesiones vivas por el mismo cobro.
      if (claim.resumed === true) {
        const { data: existing } = await admin
          .from("payments").select("checkout_session_id").eq("id", paymentId).maybeSingle();

        if (existing?.checkout_session_id) {
          const session = await stripeCall<CheckoutSession>(
            `/v1/checkout/sessions/${existing.checkout_session_id}`,
            { method: "GET", account: connectedAccount },
          );
          if (session.url) {
            return jsonResponse({ payment_id: paymentId, url: session.url, resumed: true });
          }
        }
        // Sin sesion utilizable (caduco o nunca se llego a crear): se crea una nueva abajo.
      }

      const fee = applicationFeeCents(amountCents);
      const expiresAt = Math.floor(Date.now() / 1000) + 30 * 60;

      const session = await stripeCall<CheckoutSession>("/v1/checkout/sessions", {
        account: connectedAccount,
        // Reintentar la creacion no genera una segunda sesion por el mismo cobro.
        idempotencyKey: `agora-checkout-${paymentId}`,
        body: {
          mode: "payment",
          expires_at: expiresAt,
          client_reference_id: paymentId,
          customer_email: userData.user.email ?? undefined,
          success_url: `${appBaseUrl()}/pay/ok?p=${paymentId}`,
          cancel_url: `${appBaseUrl()}/pay/ko?p=${paymentId}`,
          metadata: { payment_id: paymentId },
          line_items: [
            {
              quantity: 1,
              price_data: {
                currency: "eur",
                unit_amount: amountCents,
                product_data: { name: `${claim.activity_name} — plaza` },
              },
            },
          ],
          payment_intent_data: {
            metadata: { payment_id: paymentId },
            // Stripe RECHAZA application_fee_amount = 0, asi que con la comision a 0
            // (que es como se lanza) el parametro no se manda.
            ...(fee > 0 ? { application_fee_amount: fee } : {}),
          },
        },
      });

      await admin
        .from("payments")
        .update({ checkout_session_id: session.id, application_fee_cents: fee })
        .eq("id", paymentId);

      return jsonResponse({ payment_id: paymentId, url: session.url });
    }

    if (action === "sync") {
      const paymentId = body.payment_id as string | undefined;
      if (!paymentId) return errorResponse("bad_request", "Falta payment_id");

      const { data: payment } = await admin
        .from("payments")
        .select("id, status, user_id, activity_id, checkout_session_id, connected_account_id")
        .eq("id", paymentId)
        .maybeSingle();

      if (!payment) return errorResponse("not_found", "Pago no encontrado", 404);
      if (payment.user_id !== userData.user.id) {
        return errorResponse("not_yours", "Ese pago no es tuyo", 403);
      }

      // Ya resuelto por el webhook: se contesta con lo que hay, sin volver a Stripe.
      if (payment.status !== "pending") {
        return jsonResponse({ payment_id: paymentId, status: payment.status, activity_id: payment.activity_id });
      }

      if (!payment.checkout_session_id) {
        return jsonResponse({ payment_id: paymentId, status: "pending", activity_id: payment.activity_id });
      }

      const session = await stripeCall<CheckoutSession>(
        `/v1/checkout/sessions/${payment.checkout_session_id}`,
        { method: "GET", account: payment.connected_account_id ?? undefined },
      );

      if (session.payment_status === "paid") {
        await applyPaymentSucceeded(admin, paymentId, {
          paymentIntentId: session.payment_intent ?? undefined,
        });
        return jsonResponse({ payment_id: paymentId, status: "succeeded", activity_id: payment.activity_id });
      }

      if (session.status === "expired") {
        await applyPaymentNotCompleted(admin, paymentId, "expired");
        return jsonResponse({ payment_id: paymentId, status: "expired", activity_id: payment.activity_id });
      }

      // Sesion todavia abierta: el usuario volvio sin pagar. No se toca nada, que le
      // quedan sus 30 minutos de retencion para reintentarlo.
      return jsonResponse({ payment_id: paymentId, status: "pending", activity_id: payment.activity_id });
    }

    return errorResponse("bad_request", `Accion desconocida: ${action}`);
  } catch (e) {
    if (e instanceof StripeError) {
      return jsonResponse({ error: "stripe_error", code: e.code, message: e.message }, 502);
    }
    return jsonResponse({ error: "internal", message: String(e) }, 500);
  }
});
