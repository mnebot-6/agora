// Supabase Edge Function: stripe-connect
//
// Alta y estado de la cuenta conectada de una comunidad. Cuentas `standard`: la cuenta es
// del admin, y su relacion de KYC, disputas e impuestos es con Stripe, no con Agora.
//
// Acciones:
//   { action: "ping" }                      diagnostico: la clave funciona y Connect esta activo
//   { action: "onboard", community_id }     crea la cuenta si no existe y devuelve el enlace de alta
//   { action: "status",  community_id }     refresca charges_enabled / details_submitted
//
// Secretos: STRIPE_SECRET_KEY (obligatorio), APP_BASE_URL (opcional).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import {
  appBaseUrl,
  corsHeaders,
  errorResponse,
  jsonResponse,
  StripeError,
  stripeCall,
} from "../_shared/stripe.ts";

interface StripeAccount {
  id: string;
  charges_enabled?: boolean;
  details_submitted?: boolean;
  payouts_enabled?: boolean;
  country?: string;
  capabilities?: Record<string, string>;
  metadata?: Record<string, string>;
}

/**
 * Busca una cuenta conectada ya creada para esta comunidad que Agora haya perdido de vista.
 *
 * Pasa si la cuenta se crea en Stripe pero el UPDATE de communities falla justo despues.
 * La Idempotency-Key cubre ese hueco solo 24 h, que es lo que duran en Stripe; pasado ese
 * plazo el reintento crearia una cuenta NUEVA y la primera quedaria huerfana para siempre,
 * con su KYC hecho y sin nadie apuntando a ella. Esto lo recupera sin plazo.
 *
 * ponytail: escaneo lineal de las 100 cuentas mas recientes. A la escala de Agora (unas
 * pocas comunidades) sobra. Si algun dia hay cientos de comunidades, hay que paginar con
 * starting_after — la API de busqueda de Stripe no cubre accounts, asi que no hay atajo.
 */
async function findOrphanAccount(communityId: string): Promise<string | null> {
  const page = await stripeCall<{ data: StripeAccount[] }>("/v1/accounts", {
    method: "GET",
    body: { limit: 100 },
  });
  const match = page.data.find((a) => a.metadata?.community_id === communityId);
  return match?.id ?? null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const body = await req.json().catch(() => ({}));
    const action = body.action as string | undefined;

    // El ping no toca la base de datos ni crea nada: sirve para comprobar la clave
    // y si Connect esta habilitado antes de intentar dar de alta a nadie.
    if (action === "ping") {
      const account = await stripeCall<StripeAccount>("/v1/account", { method: "GET" });
      let connectEnabled = true;
      let connectDetail = "Connect activo";
      try {
        // Listar cuentas conectadas falla con un error explicito si Connect no esta activado.
        await stripeCall("/v1/accounts", { method: "GET", body: { limit: 1 } });
      } catch (e) {
        connectEnabled = false;
        connectDetail = e instanceof StripeError ? e.message : String(e);
      }
      return jsonResponse({
        ok: true,
        platform_account: account.id,
        country: account.country,
        connect_enabled: connectEnabled,
        connect_detail: connectDetail,
      });
    }

    // El resto de acciones exigen sesion y ser admin de la comunidad.
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return errorResponse("not_authenticated", "Falta la cabecera Authorization", 401);

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const jwt = authHeader.replace("Bearer ", "");
    const { data: userData, error: userError } = await supabase.auth.getUser(jwt);
    if (userError || !userData.user) {
      return errorResponse("not_authenticated", "Sesion no valida", 401);
    }
    const userId = userData.user.id;

    const communityId = body.community_id as string | undefined;
    if (!communityId) return errorResponse("bad_request", "Falta community_id");

    const { data: membership } = await supabase
      .from("community_members")
      .select("role")
      .eq("community_id", communityId)
      .eq("user_id", userId)
      .maybeSingle();

    if (membership?.role !== "admin") {
      return errorResponse("not_admin", "Solo los administradores gestionan los cobros", 403);
    }

    const { data: community, error: communityError } = await supabase
      .from("communities")
      .select("id, name, stripe_account_id, stripe_charges_enabled, stripe_details_submitted")
      .eq("id", communityId)
      .single();

    if (communityError || !community) {
      return errorResponse("not_found", "Comunidad no encontrada", 404);
    }

    if (action === "onboard") {
      let accountId = community.stripe_account_id as string | null;

      if (!accountId) {
        // Antes de crear nada, mirar si ya hay una cuenta de esta comunidad que se
        // quedo huerfana en un intento anterior. Crear la segunda seria irreversible:
        // dos cuentas conectadas para una comunidad y una de ellas sin dueno conocido.
        accountId = await findOrphanAccount(communityId);

        if (!accountId) {
          const created = await stripeCall<StripeAccount>("/v2/core/accounts", {
            body: {
              contact_email: userData.user.email ?? undefined,
              display_name: community.name,
              identity: { country: "es" },
              // El equivalente de la vieja cuenta "standard": panel completo de Stripe
              // para el admin, y Stripe le cobra a EL las comisiones y le imputa a EL
              // las perdidas. Agora no responde de nada, que es la razon de elegir esto.
              dashboard: "full",
              defaults: {
                currency: "eur",
                responsibilities: { fees_collector: "stripe", losses_collector: "stripe" },
              },
              configuration: {
                merchant: { capabilities: { card_payments: { requested: true } } },
              },
              metadata: { community_id: communityId, agora_admin: userId },
            },
            // Doble clic del admin: la segunda llamada devuelve la MISMA cuenta en vez
            // de crear otra. Cubre solo 24 h, que es lo que Stripe guarda estas claves;
            // pasado ese plazo el rescate es findOrphanAccount, que no caduca.
            idempotencyKey: `agora-account-${communityId}`,
          });
          accountId = created.id;
        }

        const { error: saveError } = await supabase
          .from("communities")
          .update({ stripe_account_id: accountId })
          .eq("id", communityId);

        // Ya no es un callejon sin salida: el proximo intento encuentra esta misma cuenta
        // por metadata. Se devuelve error igualmente para no seguir como si nada.
        if (saveError) {
          return errorResponse(
            "save_failed",
            `Cuenta ${accountId} creada en Stripe pero no guardada: ${saveError.message}. ` +
              "Se recuperara sola al reintentar.",
            500,
          );
        }
      }

      const returnUrl = `${appBaseUrl()}/pay/connect?community=${communityId}`;
      const link = await stripeCall<{ url: string; expires_at: number }>("/v1/account_links", {
        body: {
          account: accountId,
          refresh_url: returnUrl,
          return_url: returnUrl,
          type: "account_onboarding",
        },
      });

      return jsonResponse({ account_id: accountId, url: link.url, expires_at: link.expires_at });
    }

    if (action === "status") {
      const accountId = community.stripe_account_id as string | null;
      if (!accountId) {
        return jsonResponse({
          account_id: null,
          charges_enabled: false,
          details_submitted: false,
        });
      }

      const account = await stripeCall<StripeAccount>(`/v1/accounts/${accountId}`, { method: "GET" });
      const chargesEnabled = account.charges_enabled === true;
      const detailsSubmitted = account.details_submitted === true;

      const update: Record<string, unknown> = {
        stripe_charges_enabled: chargesEnabled,
        stripe_details_submitted: detailsSubmitted,
      };
      // Se sella la primera vez que puede cobrar de verdad, y no se vuelve a tocar.
      if (chargesEnabled && !community.stripe_charges_enabled) {
        update.stripe_onboarded_at = new Date().toISOString();
      }

      await supabase.from("communities").update(update).eq("id", communityId);

      return jsonResponse({
        account_id: accountId,
        charges_enabled: chargesEnabled,
        details_submitted: detailsSubmitted,
        payouts_enabled: account.payouts_enabled === true,
      });
    }

    return errorResponse("bad_request", `Accion desconocida: ${action}`);
  } catch (e) {
    if (e instanceof StripeError) {
      return jsonResponse({ error: "stripe_error", code: e.code, message: e.message }, 502);
    }
    return jsonResponse({ error: "internal", message: String(e) }, 500);
  }
});
