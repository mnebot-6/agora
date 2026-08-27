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
// La invoca pg_cron con net.http_post. No lleva secreto ni JWT a proposito: responde
// solo con recuentos, nunca con identificadores, y es idempotente, asi que lo peor que
// consigue quien la llame a mano es adelantar un minuto algo que iba a pasar igual.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { jsonResponse, stripeCall } from "../_shared/stripe.ts";
import {
  applyPaymentNotCompleted,
  applyPaymentSucceeded,
  refundStatusToPayment,
} from "../_shared/payments.ts";

interface CheckoutSession {
  id: string;
  status: string;
  payment_status: string;
  payment_intent: string | null;
}

interface StripeRefund {
  id: string;
  status: string;
}

Deno.serve(async (req) => {
  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // Solo cobros con un par de minutos de antiguedad. Los recientes los resuelve el
  // retorno por deep link, que es el camino rapido; este es el lento. Filtrar tambien
  // hace que invocar el barrido a mano no cueste apenas llamadas a Stripe.
  const cutoff = new Date(Date.now() - 2 * 60 * 1000).toISOString();

  const { data: pending } = await admin
    .from("payments")
    .select("id, checkout_session_id, connected_account_id")
    .eq("status", "pending")
    .not("checkout_session_id", "is", null)
    .lt("created_at", cutoff)
    .limit(200);

  // Solo recuentos en la respuesta: sin identificadores no hay nada que filtrar a
  // quien llame sin permiso, y por eso este endpoint no necesita secreto.
  const tally = {
    confirmados: 0,
    liberados: 0,
    abiertos: 0,
    errores: 0,
    reembolsados: 0,
    reembolsos_en_curso: 0,
    reembolsos_fallidos: 0,
  };

  for (const payment of pending ?? []) {
    try {
      const session = await stripeCall<CheckoutSession>(
        `/v1/checkout/sessions/${payment.checkout_session_id}`,
        { method: "GET", account: payment.connected_account_id ?? undefined },
      );

      if (session.payment_status === "paid") {
        await applyPaymentSucceeded(admin, payment.id, {
          paymentIntentId: session.payment_intent ?? undefined,
        });
        tally.confirmados++;
      } else if (session.status === "expired") {
        await applyPaymentNotCompleted(admin, payment.id, "expired");
        tally.liberados++;
      } else {
        tally.abiertos++;
      }
    } catch (e) {
      // Se registra en los logs de la funcion, no en la respuesta.
      console.error("stripe-sweep", payment.id, String(e));
      tally.errores++;
    }
  }

  // ---- Reembolsos ---------------------------------------------------------
  // Se ejecutan SIEMPRE aqui, nunca en linea. Asi cancelar una actividad con 20
  // pagos es una transaccion rapida de base de datos y las devoluciones ocurren
  // despues, con reintentos gratis.
  //
  // DOS FASES, no una: crear el reembolso y CONFIRMAR QUE EL DINERO SALIO. Con tarjeta
  // las dos pasan en la misma llamada, pero Bizum reembolsa de forma asincrona (hasta 5
  // minutos, y puede acabar en `failed`), asi que un pago se queda en refund_pending
  // hasta que Stripe da veredicto y el barrido lo vuelve a mirar en la pasada siguiente.
  const { data: refunds } = await admin
    .from("payments")
    .select("id, charge_id, payment_intent_id, connected_account_id, refund_attempts, refund_id")
    .eq("status", "refund_pending")
    .lt("refund_attempts", 5)
    .limit(50);

  for (const refund of refunds ?? []) {
    try {
      // Con refund_id el reembolso YA EXISTE y solo se consulta. Volver a hacer POST
      // seria devolver el dinero dos veces en cuanto caduque la Idempotency-Key, que en
      // Stripe vive 24 h: el reintento del dia siguiente crearia un reembolso nuevo.
      const result = refund.refund_id
        ? await stripeCall<StripeRefund>(`/v1/refunds/${refund.refund_id}`, {
          method: "GET",
          account: refund.connected_account_id ?? undefined,
        })
        : await stripeCall<StripeRefund>("/v1/refunds", {
          account: refund.connected_account_id ?? undefined,
          // Con la misma clave, reintentar NO devuelve el dinero dos veces.
          idempotencyKey: `agora-refund-${refund.id}`,
          body: refund.charge_id
            ? { charge: refund.charge_id }
            : { payment_intent: refund.payment_intent_id },
        });

      // El id se guarda SIEMPRE, tenga veredicto o no: es lo que impide que la proxima
      // pasada cree un segundo reembolso.
      const next = refundStatusToPayment(result.status);
      await admin
        .from("payments")
        .update({ refund_id: result.id, ...(next ? { status: next } : {}) })
        .eq("id", refund.id);

      if (next === "refunded") tally.reembolsados++;
      else if (next) tally.reembolsos_fallidos++;
      else tally.reembolsos_en_curso++;
    } catch (e) {
      const attempts = (refund.refund_attempts ?? 0) + 1;
      // A los 5 intentos se deja de insistir y pasa a la lista de deudas del
      // admin: mejor que lo resuelva una persona que reintentar en vano y que
      // nadie se entere de que ese dinero no ha vuelto.
      await admin
        .from("payments")
        .update({
          refund_attempts: attempts,
          status: attempts >= 5 ? "refund_owed" : "refund_pending",
        })
        .eq("id", refund.id);
      console.error("stripe-sweep refund", refund.id, String(e));
      tally.reembolsos_fallidos++;
    }
  }

  return jsonResponse({ revisados: pending?.length ?? 0, ...tally });
});
