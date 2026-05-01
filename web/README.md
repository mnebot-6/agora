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
├── _headers                                # Content-Type config para AASA
├── README.md                               # Este archivo
└── .well-known/
    ├── assetlinks.json                     # Android — listo (debug SHA256)
    └── apple-app-site-association          # iOS — PENDIENTE
```

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
           "appID": "TEAMID.com.app.agora",
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
adb shell pm verify-app-links --re-verify com.app.agora
adb shell pm get-app-links com.app.agora
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
