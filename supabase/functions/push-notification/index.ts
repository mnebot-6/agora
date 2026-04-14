// Supabase Edge Function: push-notification
// Triggered by a Database Webhook on INSERT into the `notifications` table.
// Sends an FCM push notification to the user's registered device(s).
//
// Setup:
// 1. Set the FIREBASE_SERVICE_ACCOUNT secret in your Supabase project
// 2. Create a Database Webhook in Supabase Dashboard:
//    - Table: notifications
//    - Events: INSERT
//    - Type: Supabase Edge Function
//    - Function: push-notification

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

    // Step 2: Fetch FCM token from profile
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: profile, error: profileError } = await supabase
      .from("profiles")
      .select("fcm_token")
      .eq("id", record.user_id)
      .single();

    if (profileError) {
      return new Response(
        JSON.stringify({ error: "Profile query failed", detail: profileError.message }),
        { status: 200 },
      );
    }

    if (!profile?.fcm_token) {
      return new Response(
        JSON.stringify({ message: "No FCM token found", user_id: record.user_id }),
        { status: 200 },
      );
    }

    // Step 3: Parse Firebase service account
    const saRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!saRaw) {
      return new Response(
        JSON.stringify({ error: "FIREBASE_SERVICE_ACCOUNT secret not set" }),
        { status: 500 },
      );
    }

    let serviceAccount: Record<string, string>;
    try {
      // Handle case where secret was stored with extra quotes
      const cleaned = saRaw.startsWith('"') ? JSON.parse(saRaw) : saRaw;
      serviceAccount = typeof cleaned === "string" ? JSON.parse(cleaned) : cleaned;
    } catch (e) {
      return new Response(
        JSON.stringify({ error: "Failed to parse FIREBASE_SERVICE_ACCOUNT", detail: e.message, preview: saRaw.substring(0, 50) }),
        { status: 500 },
      );
    }

    // Step 4: Get Firebase access token
    let accessToken: string;
    try {
      accessToken = await getFirebaseAccessToken(serviceAccount);
    } catch (e) {
      return new Response(
        JSON.stringify({ error: "Failed to get Firebase access token", detail: e.message }),
        { status: 500 },
      );
    }

    // Step 5: Send FCM message
    const fcmResponse = await fetch(
      `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: profile.fcm_token,
            notification: {
              title: record.title,
              body: record.body,
            },
            data: record.data
              ? Object.fromEntries(
                  Object.entries(record.data).map(([k, v]) => [k, String(v)])
                )
              : {},
            android: {
              priority: "high",
              notification: {
                click_action: "OPEN_ACTIVITY",
                channel_id: "default",
              },
            },
          },
        }),
      },
    );

    const result = await fcmResponse.json();
    return new Response(
      JSON.stringify({ success: fcmResponse.ok, fcmStatus: fcmResponse.status, result }),
      { status: 200 },
    );
  } catch (error) {
    return new Response(
      JSON.stringify({ error: "Unexpected error", detail: error.message }),
      { status: 500 },
    );
  }
});

// Helper: Get Firebase access token from service account credentials
async function getFirebaseAccessToken(
  serviceAccount: Record<string, string>,
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = btoa(
    JSON.stringify({
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );

  const signInput = `${header}.${payload}`;

  // Import the private key and sign
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signInput),
  );

  const jwt = `${signInput}.${arrayBufferToBase64Url(signature)}`;

  // Exchange JWT for access token
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });

  const tokenData = await tokenResponse.json();
  return tokenData.access_token;
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\n/g, "");
  const binary = atob(b64);
  const buffer = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    buffer[i] = binary.charCodeAt(i);
  }
  return buffer.buffer;
}

function arrayBufferToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
