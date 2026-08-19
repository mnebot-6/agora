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
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const body = await req.json().catch(() => ({}));
    const action = body.action as string | undefined;

    // El ping no toca la base de datos ni crea nada: sirve para comprobar la clave
    // y si Connect esta habilitado antes de intentar dar de alta a nadie.
    if (action === "ping") {
      const account = await stripeCall<StripeAccount>("/account", { method: "GET" });
      let connectEnabled = true;
      let connectDetail = "Connect activo";
      try {
        // Listar cuentas conectadas falla con un error explicito si Connect no esta activado.
        await stripeCall("/accounts", { method: "GET", body: { limit: 1 } });
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
        const created = await stripeCall<StripeAccount>("/accounts", {
          body: {
            type: "standard",
            country: "ES",
            email: userData.user.email ?? undefined,
            business_profile: { name: community.name },
            metadata: { community_id: communityId, agora_admin: userId },
          },
          // Si el admin pulsa dos veces, la segunda devuelve la MISMA cuenta en vez de
          // crear una segunda cuenta conectada huerfana para la misma comunidad.
          idempotencyKey: `agora-account-${communityId}`,
        });
        accountId = created.id;

        const { error: saveError } = await supabase
          .from("communities")
          .update({ stripe_account_id: accountId })
          .eq("id", communityId);

        // Si no se puede guardar, la cuenta ya existe en Stripe pero Agora la perderia de
        // vista. Se avisa en vez de seguir: reintentar creara otra cuenta distinta.
        if (saveError) {
          return errorResponse(
            "save_failed",
            `Cuenta ${accountId} creada en Stripe pero no guardada: ${saveError.message}`,
            500,
          );
        }
      }

      const returnUrl = `${appBaseUrl()}/pay/connect?community=${communityId}`;
      const link = await stripeCall<{ url: string; expires_at: number }>("/account_links", {
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

      const account = await stripeCall<StripeAccount>(`/accounts/${accountId}`, { method: "GET" });
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
