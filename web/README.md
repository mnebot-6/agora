# web/

Sitio estático servido en `https://share-agora.app` desde Cloudflare Workers (con static assets).

## Propósito

Servir los archivos de verificación de dominio que requieren los App Links (Android) y Universal Links (iOS):

- `/.well-known/assetlinks.json` — Android
- `/.well-known/apple-app-site-association` — iOS (pendiente)

Sin estos archivos los enlaces `https://share-agora.app/c/{CODE}` abrirían el navegador en lugar de la app.

## Estructura actual

```
web/
├── index.html                              # Landing page placeholder
├── a/index.html                            # Landing de invitado a actividad (SPA)
├── _headers                                # Content-Type config para AASA
├── README.md                               # Este archivo
└── .well-known/
    ├── assetlinks.json                     # Android — listo (debug SHA256)
    └── apple-app-site-association          # iOS — PENDIENTE
```

## Landing de invitado a actividad (`/a/{code}`)

`a/index.html` es una SPA autocontenida (HTML+CSS+JS inline, usa `@supabase/supabase-js`
desde CDN) que permite a alguien SIN la app solicitar asistir a una actividad como
invitado:

1. Inicia una sesión **anónima** de Supabase (persistida por navegador) → cada
   dispositivo tiene su propia identidad y su estado persistente al reabrir el link.
2. Llama a `get_activity_guest_preview(p_code)` y muestra los datos de la actividad.
3. Pide **nombre + teléfono** y llama a `request_guest_slot(p_code, p_name, p_phone)`,
   que retiene un slot pendiente de aprobación por un admin.
4. Muestra el estado (pendiente / aprobado / rechazado / lleno) y un botón
   "Descargar la app" (`agora://activity/{code}`).

### Requisitos de despliegue

- **Auth anónima**: habilitar el provider *Anonymous* en el dashboard de Supabase
  (Authentication → Providers). Sin esto, el `signInAnonymously()` falla.
  Recomendado activar protección anti-abuso (CAPTCHA / rate limit) por ser un
  endpoint público.
- **Ruta dinámica `/a/*`**: `/a/{code}` lleva un segmento variable. El Worker de
  Cloudflare debe servir `a/index.html` para CUALQUIER ruta `/a/*` (no basta con
  subir el fichero estático, que solo respondería a `/a/`). La SPA lee el `{code}`
  de `location.pathname`.
- El intent-filter Android `pathPattern="/a/.*"` ya está en el manifest, así que
  si la app está instalada el link abre la app en vez de esta landing.

## Estado por plataforma

### Android — ✅ funcional con cert debug

`assetlinks.json` actual contiene el SHA256 del **keystore debug** local. Esto significa:

- ✅ App instalada desde Android Studio (debug build) → links HTTPS abren la app
- ❌ App instalada desde Google Play (release build) → links HTTPS NO abrirán la app hasta añadir el SHA256 del cert release al array

Cuando se cree el keystore release y/o se publique en Play:

```bash
./gradlew :composeApp:signingReport
# Buscar Variant: release → SHA-256: ...
```

Añadir ese SHA256 al array `sha256_cert_fingerprints` de `assetlinks.json` (puede tener múltiples fingerprints simultáneamente). Re-deploy.

Si se usa **Google Play App Signing** (Google gestiona el keystore), Play Console muestra el SHA256 a usar en:
> Setup → App integrity → App signing → SHA-256 certificate fingerprint

### iOS — ⏳ pendiente

Cuando arranque la implementación iOS:

1. Conseguir Team ID (Apple Developer Account → Membership Details)
2. Confirmar Bundle ID (Xcode → target iosApp → Signing & Capabilities)
3. Crear `web/.well-known/apple-app-site-association` (sin extensión `.json`):
   ```json
   {
     "applinks": {
       "apps": [],
       "details": [
         {
           "appID": "TEAMID.app.shareagora.community",
           "paths": ["/c/*"]
         }
       ]
     }
   }
   ```
4. Re-deploy de la carpeta `web/`
5. El `_headers` de la raíz ya está configurado para servirlo como `application/json`

## Deploy

Cloudflare Workers → `share-agora` → "Upload new version" → arrastrar contenido de `web/`.

Cada cambio en los archivos requiere re-deploy.

## Verificación

### Android
```bash
adb shell pm verify-app-links --re-verify app.shareagora.community
adb shell pm get-app-links app.shareagora.community
# Estado esperado: verified
```

Probar:
```bash
adb shell am start -W -a android.intent.action.VIEW -d "https://share-agora.app/c/TESTCODE"
# Debe abrir la app, no Chrome
```

### iOS (cuando esté el AASA)
```bash
xcrun simctl openurl booted https://share-agora.app/c/TESTCODE
```

Apple cachea AASA hasta 24h en producción. Para desarrollo, reinstalar la app fuerza el refresh.
