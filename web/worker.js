export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Serve a/index.html for any /a/* path (dynamic guest link codes)
    if (url.pathname.startsWith("/a/") && url.pathname.length > 3) {
      return env.ASSETS.fetch(new URL("/a/index.html", url));
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
    if (url.pathname.startsWith("/pay/")) {
      const target = new URL("/app/", url);
      const paymentId = url.searchParams.get("p");
      if (paymentId) target.searchParams.set("pay", paymentId);
      return Response.redirect(target.toString(), 302);
    }

    return env.ASSETS.fetch(request);
  },
};
