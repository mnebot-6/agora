// Supabase Edge Function: stripe-webhook
//
// La red que confirma un pago aunque el usuario cierre el navegador. Junto al barrido
// hace tres capas: retorno por deep link (segundos), webhook (segundos, sin depender del
// usuario) y barrido (un minuto, si todo lo demas falla).
//
// LLAMA A LAS MISMAS FUNCIONES QUE EL RETORNO (_shared/payments.ts). Si fueran dos
// implementaciones acabarian divergiendo, y el dia que diverjan alguien se queda cobrado
// sin plaza.
//
// verify_jwt = false: Stripe no manda JWT de Supabase. LA AUTENTICACION ES LA FIRMA.
//
// Alta del endpoint en Stripe (Desarrolladores > Webhooks):
//   URL: https://<proyecto>.supabase.co/functions/v1/stripe-webhook
//   Eventos: checkout.session.completed, checkout.session.async_payment_succeeded,
//            checkout.session.async_payment_failed, checkout.session.expired,
//            refund.updated, refund.failed, account.updated
//   (charge.refunded ya no se usa: con Bizum llega antes de que el dinero salga)
//   Marcar tambien los eventos de CUENTAS CONECTADAS, o no llegara ninguno de los pagos.
// Despues: supabase secrets set STRIPE_WEBHOOK_SECRET=whsec_...

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { jsonResponse } from "../_shared/stripe.ts";
import {
  applyPaymentNotCompleted,
  applyPaymentSucceeded,
  refundStatusToPayment,
} from "../_shared/payments.ts";

/** Margen de reloj admitido entre Stripe y nosotros. */
const TOLERANCE_SECONDS = 300;

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/**
 * Verifica la cabecera stripe-signature SOBRE EL CUERPO CRUDO. Un JSON.parse antes de
 * tiempo y la firma no cuadra nunca, que es el error clasico de esta integracion.
 */
async function verifySignature(rawBody: string, header: string, secret: string): Promise<boolean> {
  const parts = Object.fromEntries(
    header.split(",").map((p) => p.split("=", 2) as [string, string]),
  );
  const timestamp = parts.t;
  const signature = parts.v1;
  if (!timestamp || !signature) return false;

  // Sin esta comprobacion, cualquiera podria reenviar indefinidamente un evento
  // legitimo capturado hace meses.
  const age = Math.abs(Math.floor(Date.now() / 1000) - Number(timestamp));
  if (!Number.isFinite(age) || age > TOLERANCE_SECONDS) return false;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${timestamp}.${rawBody}`),
  );
  const expected = Array.from(new Uint8Array(mac))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");

  return timingSafeEqual(expected, signature);
}

Deno.serve(async (req) => {
  const secret = Deno.env.get("STRIPE_WEBHOOK_SECRET");
  if (!secret) return jsonResponse({ error: "webhook_secret_missing" }, 500);

  const signature = req.headers.get("stripe-signature");
  if (!signature) return jsonResponse({ error: "missing_signature" }, 400);

  const rawBody = await req.text();
  if (!(await verifySignature(rawBody, signature, secret))) {
    return jsonResponse({ error: "invalid_signature" }, 400);
  }

  const event = JSON.parse(rawBody);

  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // DEDUPLICACION: se inserta PRIMERO. Si viola la clave primaria, este evento ya se
  // proceso y se sale con 200. Es la defensa mas barata que existe y no depende de que
  // el resto del codigo sea perfecto: Stripe reintenta, y reintenta de verdad.
  const { error: dupe } = await admin
    .from("stripe_events")
    .insert({ id: event.id, type: event.type });

  if (dupe) return jsonResponse({ received: true, duplicate: true });

  try {
    const object = event.data?.object ?? {};

    switch (event.type) {
      case "checkout.session.completed":
      case "checkout.session.async_payment_succeeded": {
        const paymentId = object.metadata?.payment_id ?? object.client_reference_id;
        if (paymentId && object.payment_status === "paid") {
          await applyPaymentSucceeded(admin, paymentId, {
            paymentIntentId: typeof object.payment_intent === "string"
              ? object.payment_intent
              : undefined,
          });
        }
        break;
      }

      case "checkout.session.expired":
      case "checkout.session.async_payment_failed": {
        const paymentId = object.metadata?.payment_id ?? object.client_reference_id;
        if (paymentId) {
          await applyPaymentNotCompleted(
            admin,
            paymentId,
            event.type.endsWith("expired") ? "expired" : "failed",
          );
        }
        break;
      }

      case "refund.updated":
      case "refund.failed": {
        // Se mira el objeto Refund y NO el Charge a proposito: `charge.refunded` llega
        // en cuanto se crea el reembolso, asi que con Bizum diria "devuelto" con el
        // dinero todavia sin salir. El veredicto solo lo trae el Refund.
        //
        // Si el barrido aun no habia guardado el refund_id esto no encuentra la fila y
        // no pasa nada: la pasada siguiente consulta el reembolso y lo resuelve igual.
        const next = refundStatusToPayment(object.status);
        if (object.id && next) {
          await admin
            .from("payments")
            .update({ status: next })
            .eq("refund_id", object.id)
            .eq("status", "refund_pending");
        }
        break;
      }

      case "account.updated": {
        const accountId = object.id;
        if (accountId) {
          await admin
            .from("communities")
            .update({
              stripe_charges_enabled: object.charges_enabled === true,
              stripe_details_submitted: object.details_submitted === true,
            })
            .eq("stripe_account_id", accountId);
        }
        break;
      }
    }
  } catch (e) {
    // Se borra la marca para que el reintento de Stripe vuelva a procesarlo: si se
    // dejara puesta, el fallo quedaria enterrado y el pago sin confirmar para siempre.
    await admin.from("stripe_events").delete().eq("id", event.id);
    console.error("stripe-webhook", event.id, event.type, String(e));
    return jsonResponse({ error: "processing_failed" }, 500);
  }

  return jsonResponse({ received: true });
});
