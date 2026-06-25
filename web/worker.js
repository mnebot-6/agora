export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Serve a/index.html for any /a/* path (dynamic guest link codes)
    if (url.pathname.startsWith("/a/") && url.pathname.length > 3) {
      return env.ASSETS.fetch(new URL("/a/index.html", url));
    }

    // Static legal pages (Google Play requirement). Map /privacy and /terms
    // (with or without trailing slash) to their index.html.
    if (url.pathname === "/privacy" || url.pathname === "/privacy/") {
      return env.ASSETS.fetch(new URL("/privacy/index.html", url));
    }
    if (url.pathname === "/terms" || url.pathname === "/terms/") {
      return env.ASSETS.fetch(new URL("/terms/index.html", url));
    }

    return env.ASSETS.fetch(request);
  },
};
