export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Link de actividad sin la app instalada: a la web app, que pide iniciar sesion
    // y luego abre la actividad (miembro) o la comunidad (no miembro).
    if (url.pathname.startsWith("/a/") && url.pathname.length > 3) {
      const code = url.pathname.slice(3).split("/")[0]; // ya viene codificado
      return Response.redirect(new URL(`/app/?a=${code}`, url), 302);
    }

    // Serve c/index.html for any /c/* path (community invite codes):
    // landing for recipients without the app installed.
    if (url.pathname.startsWith("/c/") && url.pathname.length > 3) {
      return env.ASSETS.fetch(new URL("/c/index.html", url));
    }

    // Static legal pages (Google Play requirement). Map /privacy and /terms
    // (with or without trailing slash) to their index.html.
    if (url.pathname === "/privacy" || url.pathname === "/privacy/") {
      return env.ASSETS.fetch(new URL("/privacy/index.html", url));
    }
    if (url.pathname === "/terms" || url.pathname === "/terms/") {
      return env.ASSETS.fetch(new URL("/terms/index.html", url));
    }
    if (url.pathname === "/child-safety" || url.pathname === "/child-safety/") {
      return env.ASSETS.fetch(new URL("/child-safety/index.html", url));
    }

    // Supabase password recovery landing (email template points here)
    if (url.pathname === "/reset" || url.pathname === "/reset/") {
      return env.ASSETS.fetch(new URL("/reset/index.html", url));
    }

    // Vuelta de Stripe Checkout. En Android el App Link intercepta antes de que el
    // navegador llegue aqui; en web esta redireccion mete el id en la PWA. Una sola
    // success_url sirve para los dos targets.
    // Vuelta de Stripe. En release el App Link intercepta antes de llegar aqui, pero en
    // debug la verificacion de dominio no existe y el navegador se queda con la URL, que
    // es como el usuario acababa mirando Chrome despues de pagar. Esta pagina puente
    // intenta primero el esquema propio (agora://, que NO necesita verificacion) y cae a
    // la PWA si la app no esta instalada.
    if (url.pathname.startsWith("/pay/")) {
      const isConnect = url.pathname.replace(/\/$/, "") === "/pay/connect";
      // Ojo: la app lee "p" y la web lee "pay". No es el mismo nombre.
      const appParam = isConnect ? "connect" : "p";
      const webParam = isConnect ? "connect" : "pay";
      const value = url.searchParams.get(isConnect ? "community" : "p") || "";
      const appUrl = `agora://pay?${appParam}=${encodeURIComponent(value)}`;
      const webUrl = `/app/${value ? `?${webParam}=${encodeURIComponent(value)}` : ""}`;
      const title = isConnect ? "Volviendo a Agora" : "Confirmando tu pago";

      return new Response(
        `<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title>
<style>body{font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;
margin:0;text-align:center;padding:1.5rem;color:#1c1917;background:#faf8f5}
a{display:inline-block;margin-top:1rem;padding:.75rem 1.25rem;background:#1c1917;color:#fff;
border-radius:.5rem;text-decoration:none}</style></head><body><div>
<p>${title}…</p><a href="${webUrl}">Continuar</a></div>
<script>
  // Se intenta abrir la app. Si esta instalada, el sistema se lleva el foco y el
  // temporizador no llega a saltar; si no, en un segundo cae a la version web.
  location.href = ${JSON.stringify(appUrl)};
  setTimeout(function () { location.replace(${JSON.stringify(webUrl)}); }, 1200);
</script></body></html>`,
        { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } },
      );
    }

    return env.ASSETS.fetch(request);
  },
};
