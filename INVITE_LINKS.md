# Invite Links — Setup

Agora soporta dos formatos de invite link:

- **Custom scheme** (funciona ya, sin servidor): `agora://invite/{CODE}`
- **HTTPS App Link** (recomendado para compartir): `https://share-agora.app/c/{CODE}`

El cliente está configurado para ambos. Para que el HTTPS abra la app automáticamente
(sin pasar por el navegador), Android e iOS exigen que el dominio sirva un archivo
de verificación. Estos archivos viven en la carpeta `web/` del repo y se despliegan
en `share-agora.app` vía Cloudflare Workers.

## Datos del proyecto

- **Dominio**: `share-agora.app` (Cloudflare Registrar, ~14 USD/año)
- **Hosting**: Cloudflare Workers (proyecto `share-agora`)
- **Android applicationId**: `app.shareagora.community`
- **iOS bundleID**: `app.shareagora.community` (verificar en Xcode cuando arranque iOS)
- **iOS Team ID**: pendiente — se obtiene de [Apple Developer Account → Membership](https://developer.apple.com/account/#!/membership)

## Estado actual

| Item | Estado |
|------|--------|
| Dominio registrado | ✅ |
| Cloudflare Workers configurado | ✅ |
| Custom domain `share-agora.app` enlazado | ✅ |
| `index.html` placeholder | ✅ |
| `_headers` (Content-Type para AASA) | ✅ |
| `assetlinks.json` Android (debug) | ✅ |
| `assetlinks.json` Android (release) | ⏳ pendiente keystore release |
| `apple-app-site-association` iOS | ⏳ pendiente arrancar iOS |
| Android intent-filters en Manifest | ✅ |
| iOS entitlement `applinks:` | ✅ |
| `MainActivity.handleDeepLink` parsea HTTPS | ✅ |

## Re-deploy de `web/`

Cada vez que cambien los archivos de `web/` (p.ej. al añadir el SHA release
o el AASA iOS):

1. Cloudflare dashboard → Workers & Pages → `share-agora` → "New deployment"
2. Arrastrar el **contenido** de la carpeta `web/` (no la carpeta misma)
3. Deploy

El cambio es instantáneo pero los clientes pueden cachear:
- Android: cache se invalida automáticamente al re-instalar la app o al ejecutar `adb shell pm verify-app-links --re-verify app.shareagora.community`
- iOS: AASA se cachea hasta 24h en producción; reinstalar la app fuerza refresh

## Pendiente Android — release

Cuando se prepare la primera release:

1. Generar keystore release (o usar Google Play App Signing)
2. Obtener el SHA256 del cert release:
   - **Keystore propio**:
     ```bash
     ./gradlew :composeApp:signingReport
     # Buscar: Variant: release → SHA-256: ...
     ```
   - **Play App Signing** (recomendado): Play Console → Setup → App integrity → App signing → "SHA-256 certificate fingerprint"
3. Añadir el SHA al array `sha256_cert_fingerprints` en [`web/.well-known/assetlinks.json`](web/.well-known/assetlinks.json):
   ```json
   "sha256_cert_fingerprints": [
     "8F:B9:F3:...:50",
     "AB:CD:EF:...:99"
   ]
   ```
4. Re-deploy de `web/`
5. Verificar:
   ```bash
   adb shell pm verify-app-links --re-verify app.shareagora.community
   adb shell pm get-app-links app.shareagora.community
   ```
   Estado esperado: `verified`.

## Pendiente iOS — Universal Links

Cuando arranque la implementación iOS:

1. Conseguir **Team ID**: [developer.apple.com/account](https://developer.apple.com/account) → Membership Details → "Team ID" (10 chars alfanuméricos).
2. Confirmar **Bundle ID** en Xcode: target `iosApp` → Signing & Capabilities → "Bundle Identifier".
3. Crear el archivo `web/.well-known/apple-app-site-association` (**sin extensión `.json`**):
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
4. Re-deploy `web/` en Cloudflare.
5. Verificar `Content-Type` (debe ser `application/json`):
   ```bash
   curl -I https://share-agora.app/.well-known/apple-app-site-association
   ```
   El `_headers` de `web/_headers` ya está configurado para esto.
6. Probar en simulador:
   ```bash
   xcrun simctl openurl booted https://share-agora.app/c/TESTCODE
   ```
7. En dispositivo real, instalar app → al primer arranque iOS descarga AASA y lo cachea. Si no funciona, reinstalar.

## Trade-offs y notas

- **Cache iOS de 24h en producción**: si cambias el AASA, los usuarios existentes pueden tardar hasta 24h en ver el cambio. Mientras tanto el link les abre Safari.
- **`assetlinks.json` Android cachea por instalación**: ejecutar la verificación manual fuerza el refresh.
- **Custom scheme `agora://`** sigue activo como fallback: si la verificación falla por cualquier motivo, el flujo de `Compartir invitación → Por código` sigue funcionando porque no depende de la verificación de dominio.
- **Subdominios**: si en algún momento se usa `www.share-agora.app` o similar, hay que añadirlo como custom domain en el worker y al `applinks:` del entitlement iOS.
