# Pendiente para subir Agora a Google Play

Estado al commit que añade este archivo. El código ya cubre los requisitos
bloqueantes de Play (borrado de cuenta, moderación UGC, links a privacy/terms,
versionName 1.0.0, shrinkResources). Lo que queda es trabajo fuera del repo
o que requiere secretos del usuario.

---

## 🔴 Bloqueante 1 — Aplicar las migraciones SQL en Supabase

**Archivos:**
- `supabase/migrations/022_delete_user_account.sql` — RPC `delete_my_account()`
- `supabase/migrations/023_moderation.sql` — tablas `reports` + `user_blocks`

**Cómo:**
1. Abrir el panel de Supabase del proyecto Agora.
2. SQL Editor → pegar el contenido de cada archivo y ejecutar (en orden).
3. Verificar que las tablas y RPC aparecen:
   ```sql
   SELECT proname FROM pg_proc WHERE proname = 'delete_my_account';
   SELECT tablename FROM pg_tables WHERE tablename IN ('reports','user_blocks');
   ```
4. Test rápido desde la propia sesión SQL (con un user_id válido):
   ```sql
   -- desde el contexto del usuario autenticado en la app
   SELECT delete_my_account();  -- ¡ojo, borra de verdad!
   ```

**Si la app ya está conectada a Supabase de producción**, considera ejecutar
primero en un entorno de staging o snapshot.

---

## 🔴 Bloqueante 2 — Hostear Privacy Policy y Terms

**Archivos drafts:**
- `docs/legal/privacy_policy.md`
- `docs/legal/terms_of_service.md`

**Antes de publicar, rellenar placeholders:**
- `[LEGAL_NAME / TRADING NAME]` → tu nombre o entidad legal
- `[COUNTRY]` → país de operación (ej. España)
- `[JURISDICTION]` → tribunal competente (ej. Barcelona)

**Hosting** (cualquiera de estas opciones):
- Subir como páginas estáticas en `share-agora.app/privacy` y `/terms`
  (ya tienes el dominio para invites — añade dos rutas más).
- Alternativa rápida: GitHub Pages en un repo público, luego Play Console
  acepta cualquier URL pública.

**Después:** completar el **Data Safety form** en Play Console:
- Datos recogidos: Email, Display name, Avatar (opcional), Push token,
  Mensajes, Membresías.
- Compartidos con terceros: NO (Supabase y Firebase son procesadores).
- Cifrados en tránsito: SÍ (HTTPS).
- Eliminación de datos: SÍ (en-app y por email).

---

## 🔴 Bloqueante 3 — Generar keystore release

**El andamiaje gradle ya está hecho** ([composeApp/build.gradle.kts](composeApp/build.gradle.kts:96-128) lee `keystore.properties` si existe).

**Pasos:**
1. Ejecutar `keytool` (en una carpeta FUERA del repo, ej. `~/keys/`):
   ```bash
   keytool -genkey -v \
     -keystore agora-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias agora
   ```
2. Copiar `keystore.properties.example` (raíz del repo) a `keystore.properties`
   y rellenar con la ruta absoluta al `.jks` y los passwords reales.
3. Hacer backup del `.jks` cifrado (KeePass, 1Password, etc.). Sin él no
   puedes actualizar la app jamás (a menos que actives Play App Signing).
4. Verificar que `./gradlew :composeApp:bundleRelease` produce un `.aab`
   firmado con la clave de release (no con debug).
5. **Activar Play App Signing** al subir el primer .aab a Play Console.
   Google guardará la clave de firma de la app y tú solo gestionas la
   "upload key" (más seguro y permite recuperación).

---

## 🟠 Importante (pero no bloqueante para el primer release)

### `assetlinks.json` para Android App Links
El manifest ya declara `android:autoVerify="true"` para `https://share-agora.app/c/*`.
Para que el deep link abra directo (sin chooser):

1. Tras tener el keystore final (o tras Play App Signing), obtener SHA256:
   ```bash
   keytool -list -v -keystore agora-release.jks -alias agora
   # Si usas Play App Signing: copiar el SHA256 de Play Console > App signing
   ```
2. Crear `share-agora.app/.well-known/assetlinks.json`:
   ```json
   [{
     "relation": ["delegate_permission/common.handle_all_urls"],
     "target": {
       "namespace": "android_app",
       "package_name": "com.app.agora",
       "sha256_cert_fingerprints": ["AA:BB:CC:..."]
     }
   }]
   ```
3. Verificar con: `adb shell pm get-app-links com.app.agora`

### Crashlytics
30 minutos de trabajo, ROI altísimo. Añadir en `composeApp/build.gradle.kts`:
```kotlin
androidMain.dependencies {
    implementation(libs.firebase.crashlytics)  // añadir al BOM existente
}
```
Y plugin `com.google.firebase.crashlytics` en raíz.

### Quitar `google-services.json` del repo (decisión)
El archivo está commiteado pero también listado en `.gitignore` (inconsistente).
Opciones:
- **Mantenerlo en repo** y quitar del .gitignore (común — no contiene secretos).
- **Sacarlo del git history** (`git rm --cached`) y documentar cómo obtenerlo.
Ver issue de seguridad: ninguna en realidad — el archivo solo tiene IDs públicos.

---

## 🟡 Nice-to-have (post-release)

- **EncryptedSharedPreferences** wrapper para defensa en profundidad.
- **Validación email regex** client-side en login/register para feedback inmediato.
- **Permission rationale dialog** antes de pedir POST_NOTIFICATIONS (mejora aceptación).
- **`contentDescription`** en iconos hoy a `null` (accesibilidad TalkBack).
- **Splash screen API** (androidx.core:splashscreen) para Android 12+.
- **Pantalla "Bloqueados"** en Profile para gestionar/desbloquear (hoy solo
  se puede bloquear; el repo `BlockRepository.unblockUser()` está listo, falta UI).
- **Pantalla admin de moderación** para revisar `reports` (actualmente
  cualquier admin/dev tiene que mirarlos en Supabase).

---

## Verificación end-to-end (cuando todo lo anterior esté hecho)

```bash
# 1. Build firmado
./gradlew :composeApp:bundleRelease

# 2. Comprobar que está firmado con la release key (no debug)
keytool -printcert -jarfile composeApp/build/outputs/bundle/release/composeApp-release.aab

# 3. Subir a Play Console > Test interno (revisión 1-2 días)
```

En el dispositivo verificar:
- ✅ Registro pide términos (futuro: añadir checkbox aún pendiente, ver nice-to-have).
- ✅ Profile → Eliminar cuenta con doble confirmación → tras borrar no puedo loguearme.
- ✅ Long-press en mensaje del chat → Reportar → fila aparece en `reports`.
- ✅ Long-press en mensaje → Bloquear → mensajes de ese user desaparecen.
- ✅ Logout borra `fcm_token` de `profiles` (verificar en Supabase).
- ✅ Push notification abre la pantalla correcta.
- ✅ Deep link `https://share-agora.app/c/XYZ` abre la app sin chooser
  (requiere `assetlinks.json`).

---

## Cómo retomar esto

Cuando quieras continuar, dile a Claude algo como:
> "Continúa con el plan de Play Store. Lee `docs/play_store_release_pending.md`
> y dime por dónde retomamos."

Claude verificará el estado actual (qué migraciones se han aplicado,
si existe `keystore.properties`, etc.) y propondrá el siguiente paso.
