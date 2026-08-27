// Cliente minimo de la API REST de Stripe.
//
// No se usa el SDK de node: en Deno arrastra dependencias y lo unico que hace falta
// son peticiones form-encoded con dos cabeceras. Menos superficie que mantener.

const STRIPE_API = "https://api.stripe.com";

// La v1 habla form-encoded; la v2 habla JSON y pide su propia version de API. Se decide
// por el prefijo de la ruta para no tener que acordarse en cada llamada.
const V1_VERSION = "2025-08-27.basil";
const V2_VERSION = "2026-07-29.dahlia";

export interface StripeCallOptions {
  method?: "GET" | "POST" | "DELETE";
  body?: Record<string, unknown>;
  /** Cuenta conectada sobre la que actuar. Es lo que convierte el cargo en directo. */
  account?: string;
  /** Con la misma clave, Stripe devuelve el resultado original en vez de repetir la operacion. */
  idempotencyKey?: string;
}

export class StripeError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    message: string,
  ) {
    super(message);
    this.name = "StripeError";
  }
}

/**
 * Stripe espera form-encoding con notacion de corchetes para lo anidado:
 *   { a: { b: 1 } }  ->  a[b]=1
 *   { a: [{ b: 1 }] } ->  a[0][b]=1
 * Los undefined y null se omiten: mandar un campo vacio no es lo mismo que no mandarlo.
 */
function formEncode(obj: Record<string, unknown>, prefix = ""): string[] {
  const parts: string[] = [];
  for (const [rawKey, value] of Object.entries(obj)) {
    if (value === undefined || value === null) continue;
    const key = prefix ? `${prefix}[${rawKey}]` : rawKey;
    if (Array.isArray(value)) {
      value.forEach((item, i) => {
        if (item !== null && typeof item === "object") {
          parts.push(...formEncode(item as Record<string, unknown>, `${key}[${i}]`));
        } else {
          parts.push(`${encodeURIComponent(`${key}[${i}]`)}=${encodeURIComponent(String(item))}`);
        }
      });
    } else if (typeof value === "object") {
      parts.push(...formEncode(value as Record<string, unknown>, key));
    } else {
      parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
    }
  }
  return parts;
}

export async function stripeCall<T = Record<string, unknown>>(
  path: string,
  options: StripeCallOptions = {},
): Promise<T> {
  const secretKey = Deno.env.get("STRIPE_SECRET_KEY");
  if (!secretKey) throw new StripeError(500, "config", "STRIPE_SECRET_KEY no esta configurada");

  const { method = "POST", body, account, idempotencyKey } = options;
  const isV2 = path.startsWith("/v2/");

  const headers: Record<string, string> = {
    Authorization: `Bearer ${secretKey}`,
    "Stripe-Version": isV2 ? V2_VERSION : V1_VERSION,
  };
  if (account) headers["Stripe-Account"] = account;
  if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;

  let url = `${STRIPE_API}${path}`;
  let payload: string | undefined;
  if (body && method === "GET") {
    const qs = formEncode(body).join("&");
    if (qs) url += `?${qs}`;
  } else if (body && isV2) {
    payload = JSON.stringify(body);
    headers["Content-Type"] = "application/json";
  } else if (body) {
    payload = formEncode(body).join("&");
    headers["Content-Type"] = "application/x-www-form-urlencoded";
  }

  const res = await fetch(url, { method, headers, body: payload });
  const text = await res.text();
  let json: Record<string, unknown>;
  try {
    json = JSON.parse(text);
  } catch {
    throw new StripeError(res.status, undefined, `Respuesta no-JSON de Stripe: ${text.slice(0, 200)}`);
  }

  if (!res.ok) {
    const err = (json.error ?? {}) as Record<string, string>;
    throw new StripeError(res.status, err.code ?? err.type, err.message ?? "Error de Stripe");
  }
  return json as T;
}

/**
 * Comision de Agora, en centimos. Arranca a 0 por decision de negocio: sin entidad legal
 * detras, cobrar comision convierte al titular de la plataforma en perceptor de ingresos.
 * El dia que compense el papeleo se sube PLATFORM_FEE_BPS y no se toca codigo.
 */
export function applicationFeeCents(amountCents: number): number {
  const bps = Number(Deno.env.get("PLATFORM_FEE_BPS") ?? "0");
  if (!Number.isFinite(bps) || bps <= 0) return 0;
  return Math.floor((amountCents * bps) / 10_000);
}

export function appBaseUrl(): string {
  return Deno.env.get("APP_BASE_URL") ?? "https://share-agora.app";
}

// La app web (wasmJs) llama a estas funciones desde el navegador, asi que hacen falta
// cabeceras CORS y responder al preflight. Las funciones que ya existian en el repo se
// disparan por webhook de base de datos (servidor a servidor) y no las necesitaban.
// La lista de cabeceras NO se enumera a mano. supabase-kt manda x-supabase-api-version
// ademas de las cuatro obvias, y cualquier cabecera que falte aqui hace que el navegador
// rechace el preflight: el fetch ni sale, y en la app se ve un spinner infinito. Enumerar
// se rompe sola cada vez que la libreria anade una cabecera nueva.
//
// El comodin no cubre Authorization (asi lo define la spec de Fetch), por eso va listada
// aparte. Es seguro porque Allow-Origin es "*" y por tanto no hay credenciales de por
// medio: la autenticacion viaja en el JWT del cuerpo de la peticion, no en cookies.
export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, *",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

export function errorResponse(code: string, message: string, status = 400): Response {
  return jsonResponse({ error: code, message }, status);
}
