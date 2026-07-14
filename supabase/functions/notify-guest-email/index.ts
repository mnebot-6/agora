// Supabase Edge Function: notify-guest-email
// Triggered by a Database Webhook on INSERT into the `notifications` table.
// Sends an email (via Resend) to guest users — anonymous users who joined an activity
// through a shared link and have an email on record in `profiles.guest_email`.
//
// Regular members have no `guest_email`, so they never receive these emails: this function
// no-ops for them. It is generic by design — it relays whatever title/body the DB function
// wrote (already localized), so new notification types (payment, removal, ...) work for free.
//
// Setup:
// 1. Set the RESEND_API_KEY secret in your Supabase project.
// 2. (Optional) Set EMAIL_FROM, e.g. "Agora <no-reply@share-agora.app>".
//    The sending domain must be verified in Resend (SPF/DKIM DNS records in Cloudflare).
// 3. Create a Database Webhook in Supabase Dashboard:
//    - Table: notifications
//    - Events: INSERT
//    - Type: Supabase Edge Function
//    - Function: notify-guest-email

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface NotificationPayload {
  type: "INSERT";
  table: string;
  record: {
    id: string;
    user_id: string;
    type: string;
    title: string;
    body: string;
    data: Record<string, string> | null;
  };
}

const DEFAULT_FROM = "Agora <no-reply@share-agora.app>";

function escapeHtml(s: string): string {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

interface ActivityDetails {
  name?: string | null;
  datetime?: string | null;
  location_name?: string | null;
  cost_description?: string | null;
}

function fmtDate(iso?: string | null): string | null {
  if (!iso) return null;
  try {
    return new Intl.DateTimeFormat("es-ES", {
      dateStyle: "full",
      timeStyle: "short",
      timeZone: "Europe/Madrid",
    }).format(new Date(iso));
  } catch {
    return null;
  }
}

// Renders the activity context (name, when, where, cost) so every email carries
// the same details the guest saw on the web when requesting.
function buildDetails(activity: ActivityDetails | null): string {
  if (!activity) return "";
  const rows: Array<[string, string]> = [];
  if (activity.name) rows.push(["Actividad", activity.name]);
  const when = fmtDate(activity.datetime);
  if (when) rows.push(["Cuándo", when]);
  if (activity.location_name) rows.push(["Dónde", activity.location_name]);
  if (activity.cost_description) rows.push(["Coste", activity.cost_description]);
  if (rows.length === 0) return "";
  const items = rows.map(([k, v]) =>
    `<tr><td style="padding:4px 12px 4px 0;color:#7a726a;font-size:13px;white-space:nowrap;vertical-align:top;">${escapeHtml(k)}</td><td style="padding:4px 0;font-size:14px;color:#2b2622;">${escapeHtml(v)}</td></tr>`
  ).join("");
  return `<table style="margin:16px 0 0;border-collapse:collapse;"><tbody>${items}</tbody></table>`;
}

function buildHtml(title: string, body: string, activity: ActivityDetails | null): string {
  const safeTitle = escapeHtml(title);
  const safeBody = escapeHtml(body).replace(/\n/g, "<br>");
  return `<!DOCTYPE html>
<html lang="es">
  <body style="margin:0;background:#f6f3ec;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#2b2622;">
    <div style="max-width:440px;margin:24px auto;background:#fffdf8;border:1px solid #e3dccd;border-radius:16px;padding:28px 24px;">
      <h1 style="color:#5b3a29;font-size:20px;margin:0 0 12px;">${safeTitle}</h1>
      <p style="font-size:15px;line-height:1.5;margin:0;">${safeBody}</p>
      ${buildDetails(activity)}
      <p style="color:#7a726a;font-size:12px;margin:24px 0 0;">Agora — gestión de comunidades</p>
    </div>
  </body>
</html>`;
}

Deno.serve(async (req) => {
  try {
    // Step 1: Parse request body
    const rawBody = await req.text();
    let payload: NotificationPayload;
    try {
      payload = JSON.parse(rawBody);
    } catch (e) {
      return new Response(
        JSON.stringify({ error: "Failed to parse request body", detail: e.message, bodyPreview: rawBody.substring(0, 100) }),
        { status: 400 },
      );
    }
    const { record } = payload;

    // Reminders are delivered in-app / push only — never by email (user preference).
    if (record.type === "activity_reminder") {
      return new Response(
        JSON.stringify({ message: "Reminder type is not emailed" }),
        { status: 200 },
      );
    }

    // Step 2: Fetch the guest email from the profile (only guests have one)
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: profile, error: profileError } = await supabase
      .from("profiles")
      .select("guest_email")
      .eq("id", record.user_id)
      .single();

    if (profileError) {
      return new Response(
        JSON.stringify({ error: "Profile query failed", detail: profileError.message }),
        { status: 200 },
      );
    }

    if (!profile?.guest_email) {
      // Not a guest (or no email): nothing to do.
      return new Response(
        JSON.stringify({ message: "No guest email found", user_id: record.user_id }),
        { status: 200 },
      );
    }

    // Step 3: Fetch activity context (name, when, where, cost) to include in the email
    let activity: ActivityDetails | null = null;
    const activityId = record.data?.activity_id;
    if (activityId) {
      const { data: act } = await supabase
        .from("activities")
        .select("name, datetime, location_name, cost_description")
        .eq("id", activityId)
        .single();
      activity = act ?? null;
    }

    // Step 4: Send the email via Resend
    const resendKey = Deno.env.get("RESEND_API_KEY");
    if (!resendKey) {
      return new Response(
        JSON.stringify({ error: "RESEND_API_KEY secret not set" }),
        { status: 500 },
      );
    }

    const from = Deno.env.get("EMAIL_FROM") || DEFAULT_FROM;
    const emailResponse = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from,
        to: profile.guest_email,
        subject: record.title,
        html: buildHtml(record.title, record.body, activity),
      }),
    });

    const result = await emailResponse.json();
    return new Response(
      JSON.stringify({ success: emailResponse.ok, emailStatus: emailResponse.status, result }),
      { status: 200 },
    );
  } catch (error) {
    return new Response(
      JSON.stringify({ error: "Unexpected error", detail: error.message }),
      { status: 500 },
    );
  }
});
