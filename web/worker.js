export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Serve a/index.html for any /a/* path (dynamic guest link codes)
    if (url.pathname.startsWith("/a/") && url.pathname.length > 3) {
      return env.ASSETS.fetch(new URL("/a/index.html", url));
    }

    return env.ASSETS.fetch(request);
  },
};
