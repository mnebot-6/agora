// Transiciones de estado de un cobro.
//
// EL WEBHOOK Y EL RETORNO POR DEEP LINK LLAMAN A ESTAS MISMAS FUNCIONES. Si fueran dos
// implementaciones acabarian divergiendo, y el dia que diverjan alguien se queda cobrado
// sin plaza. No dupliques esta logica en ningun sitio.
//
// Todas son idempotentes por construccion: el primer UPDATE lleva la condicion de estado
// esperado y, si no afecta a ninguna fila, la transicion ya se aplico y se sale sin tocar
// nada mas. Da igual que Stripe entregue el mismo evento cinco veces.

import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

export interface PaymentRow {
  id: string;
  activity_id: string;
  slot_id: string | null;
  user_id: string | null;
  community_id: string;
  amount_cents: number;
  status: string;
  method: string;
  connected_account_id: string | null;
}

export type TransitionResult = "applied" | "already_applied" | "not_found";

/**
 * El pago se confirmo. La plaza pasa a ser del pagador y, si habia un ocupante anterior
 * esperando sustituto, su cobro queda marcado para reembolso.
 */
export async function applyPaymentSucceeded(
  supabase: SupabaseClient,
  paymentId: string,
  stripe: { paymentIntentId?: string; chargeId?: string },
): Promise<TransitionResult> {
  // La condicion status='pending' es lo que hace idempotente todo el bloque: el segundo
  // evento no afecta a ninguna fila y se sale antes de tocar la plaza.
  const { data: updated } = await supabase
    .from("payments")
    .update({
      status: "succeeded",
      payment_intent_id: stripe.paymentIntentId ?? null,
      charge_id: stripe.chargeId ?? null,
    })
    .eq("id", paymentId)
    .eq("status", "pending")
    .select("*");

  if (!updated || updated.length === 0) {
    const { data: existing } = await supabase
      .from("payments").select("id").eq("id", paymentId).maybeSingle();
    return existing ? "already_applied" : "not_found";
  }

  const payment = updated[0] as PaymentRow;
  if (!payment.slot_id) return "applied";

  // Quien ocupaba la plaza antes, si es que la habia liberado esperando sustituto.
  const { data: previous } = await supabase
    .from("payments")
    .select("id, method, user_id, amount_cents")
    .eq("slot_id", payment.slot_id)
    .eq("status", "awaiting_substitute")
    .neq("id", paymentId);

  await supabase
    .from("slots")
    .update({
      status: "paid",
      reserved_by: payment.user_id,
      reserved_at: new Date().toISOString(),
      hold_expires_at: null,
      released_at: null,
      offered_to: null,
      offer_expires_at: null,
    })
    .eq("id", payment.slot_id);

  // Quien paga deja de ser suplente de esta actividad.
  if (payment.user_id) {
    await supabase
      .from("substitute_queue")
      .delete()
      .eq("activity_id", payment.activity_id)
      .eq("user_id", payment.user_id);
  }

  for (const prev of previous ?? []) {
    // Lo que paso por Stripe se devuelve solo; lo demas lo debe el admin a mano.
    await supabase
      .from("payments")
      .update({
        status: prev.method === "stripe" ? "refund_pending" : "refund_owed",
        refund_reason: "substitute",
      })
      .eq("id", prev.id);

    if (prev.user_id) {
      await notify(supabase, prev.user_id, "payment_refunded", "Plaza ocupada", {
        activity_id: payment.activity_id,
      });
    }
  }

  if (payment.user_id) {
    await notify(supabase, payment.user_id, "payment_confirmed", "Pago confirmado", {
      activity_id: payment.activity_id,
    });
  }

  return "applied";
}

/**
 * El pago no salio adelante: caduco, se cancelo o lo rechazaron. Se suelta la retencion.
 */
export async function applyPaymentNotCompleted(
  supabase: SupabaseClient,
  paymentId: string,
  reason: "expired" | "failed",
  failureCode?: string,
): Promise<TransitionResult> {
  const { data: updated } = await supabase
    .from("payments")
    .update({ status: reason, failure_code: failureCode ?? null })
    .eq("id", paymentId)
    .eq("status", "pending")
    .select("*");

  if (!updated || updated.length === 0) {
    const { data: existing } = await supabase
      .from("payments").select("id").eq("id", paymentId).maybeSingle();
    return existing ? "already_applied" : "not_found";
  }

  const payment = updated[0] as PaymentRow;
  if (!payment.slot_id) return "applied";

  // Las tres condiciones importan. Si la plaza ya no esta retenida por esta persona no es
  // suya y no se toca: sobre una plaza liberada (paid + released_at) este UPDATE no hace
  // nada, que es lo correcto, porque sigue siendo del ocupante anterior.
  await supabase
    .from("slots")
    .update({ status: "available", reserved_by: null, reserved_at: null, hold_expires_at: null })
    .eq("id", payment.slot_id)
    .eq("status", "pending_payment")
    .eq("reserved_by", payment.user_id);

  return "applied";
}

async function notify(
  supabase: SupabaseClient,
  userId: string,
  type: string,
  title: string,
  data: Record<string, string>,
) {
  await supabase.from("notifications").insert({
    user_id: userId,
    type,
    title,
    body: title,
    data,
  });
}

/**
 * Estado de pago que corresponde al estatus de un Refund de Stripe, o null si Stripe
 * todavia no tiene veredicto.
 *
 * Existe porque BIZUM REEMBOLSA DE FORMA ASINCRONA. Con tarjeta el reembolso nace
 * `succeeded` y no hay nada que esperar; con Bizum nace `pending`, tarda hasta 5
 * minutos y puede acabar en `failed`. Dar por devuelto lo que aun no ha salido es
 * mentirle al usuario sobre su dinero.
 *
 * Un reembolso `failed` NO se reintenta: Stripe devuelve el importe al saldo del admin
 * y hay que buscar otra via, asi que pasa a la lista de deudas para que lo pague una
 * persona.
 */
export function refundStatusToPayment(status: string): "refunded" | "refund_owed" | null {
  if (status === "succeeded") return "refunded";
  if (status === "failed" || status === "canceled") return "refund_owed";
  return null; // pending / requires_action: el proximo barrido lo vuelve a mirar
}
