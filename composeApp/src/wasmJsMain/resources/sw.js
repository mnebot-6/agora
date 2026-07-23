// Service worker de Agora web: network-first con fallback a caché.
// Cachea en runtime todo GET bajo /app/ que responda OK.
//
// Al estar online siempre se sirve de red, así que la caché es solo un fallback
// para offline y no puede devolver un shell obsoleto mientras haya conexión (la
// frescura de index.html/composeApp.js la garantizan además las cabeceras
// Cache-Control de web/_headers).
//
// Subir este número purga las cachés anteriores en el 'activate': úsalo si una
// versión desplegada pudo dejar assets inconsistentes cacheados.
const CACHE = "agora-app-v2";

self.addEventListener("install", () => self.skipWaiting());

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== "GET" || url.origin !== self.location.origin || !url.pathname.startsWith("/app")) {
    return; // API de Supabase y todo lo demás: red directa, sin tocar.
  }
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        }
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});
