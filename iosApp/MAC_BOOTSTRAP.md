# Bootstrap iOS en Mac — guía paso a paso

Todo el código Kotlin + Swift + configuración ya está preparado.
Sigue estos pasos **en orden** la primera vez que tengas acceso a un Mac.
Tiempo estimado: **2-4 horas** (incluyendo esperas de descarga/upload).

> **Pasos 1 y 2** se pueden completar desde cualquier ordenador con un navegador.
> **Paso 3 en adelante** requiere Mac con Xcode.

---

## Paso 1 — Cuenta Apple Developer (navegador, ~15 min)

1. Ve a https://developer.apple.com/programs/
2. Haz clic en **Enroll** e inicia sesión con tu Apple ID.
3. Selecciona **Individual** (o **Organization** si es para una empresa).
4. Paga los $99/año con tarjeta de crédito.
5. La cuenta suele activarse en minutos; a veces tarda hasta 24 h.

> **Por qué:** sin cuenta Apple Developer no puedes firmar la app, instalarla en iPhone ni subir a App Store/TestFlight.

---

## Paso 2 — APNs Auth Key y configuración Firebase (navegador, ~20 min)

### 2a. Crear APNs Auth Key en Apple Developer

1. Ve a https://developer.apple.com/account → **Certificates, Identifiers & Profiles** → **Keys**.
2. Pulsa **"+"**.
3. Nombre: `Agora APNs Key` (o similar). Marca la casilla **Apple Push Notifications service (APNs)**.
4. Haz clic en **Continue** → **Register** → **Download**.
5. **Guarda el archivo `.p8` en un lugar seguro** (solo se puede descargar una vez). Anota también el **Key ID** (10 caracteres que aparecen en la pantalla).
6. Anota tu **Team ID**: aparece en https://developer.apple.com/account → Membership → Team ID (10 caracteres).

### 2b. Registrar la app iOS en Firebase

1. Ve a https://console.firebase.google.com → proyecto **agora-70f4e**.
2. Rueda abajo hasta **"Your apps"** → haz clic en **"Add app"** → selecciona el icono de **iOS**.
3. **Apple bundle ID**: `com.app.agora` (mismo que el Android `applicationId`).
4. Nombre de app: `Agora iOS` (solo para identificarlo en la consola).
5. Haz clic en **"Register app"** → descarga `GoogleService-Info.plist`.
6. **Coloca el archivo** en `iosApp/iosApp/GoogleService-Info.plist` (NO lo commitees — está en `.gitignore`; cada desarrollador lo descarga manualmente).
7. Salta los pasos de "Add Firebase SDK" y "Add initialization code" — ya están hechos.

### 2c. Subir la APNs Auth Key a Firebase

1. En Firebase Console → **Project Settings** (rueda dentada arriba a la izquierda) → pestaña **Cloud Messaging**.
2. Baja hasta **"Apple app configuration"** → **APNs Authentication Key** → **Upload**.
3. Sube el archivo `.p8` del paso 2a.
4. Rellena **Key ID** y **Team ID** que anotaste.
5. Haz clic en **Upload**.

> **Por qué estos pasos:** Firebase Cloud Messaging necesita la APNs Auth Key para poder entregar push notifications a iPhones en nombre de tu app.

---

## Paso 3 — Instalar herramientas en Mac (~10 min)

Abre **Terminal** en el Mac y ejecuta:

```bash
# Homebrew (si no lo tienes)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# XcodeGen (genera iosApp.xcodeproj desde project.yml)
brew install xcodegen

# Java 17 (si no lo tienes — necesario para Gradle)
brew install --cask temurin@17
```

Instala/actualiza **Xcode** desde la App Store si no lo tienes ya (o está desactualizado). Requiere ~10 GB.

Acepta la licencia de Xcode:
```bash
sudo xcodebuild -license accept
```

---

## Paso 4 — Clonar / actualizar el repo y añadir GoogleService-Info.plist

```bash
# Si no tienes el repo clonado:
git clone <URL-del-repo> Agora
cd Agora

# Si ya lo tienes:
cd Agora && git pull

# Copia el GoogleService-Info.plist que descargaste en el paso 2b:
cp ~/Downloads/GoogleService-Info.plist iosApp/iosApp/GoogleService-Info.plist
```

---

## Paso 5 — Generar el proyecto Xcode

```bash
cd iosApp
xcodegen generate
```

Debería crear `iosApp/iosApp.xcodeproj` con:
- Target `iosApp` — iOS 15.0+, bundle ID `com.app.agora`
- Run Script que invoca `:composeApp:embedAndSignAppleFrameworkForXcode`
- Referencias a los archivos Swift, Info.plist, Assets.xcassets

```bash
# Abre el proyecto
open iosApp.xcodeproj
```

---

## Paso 6 — Añadir Firebase SDK en Xcode (una vez)

Dentro de Xcode:

1. **File → Add Package Dependencies…**
2. En el campo de búsqueda pega: `https://github.com/firebase/firebase-ios-sdk`
3. En **"Dependency Rule"** selecciona **Up to Next Major Version** y escribe `11.0.0`.
4. Haz clic en **Add Package**.
5. En la pantalla "Choose Package Products", marca:
   - ✅ **FirebaseMessaging** (obligatorio)
   - ❌ FirebaseAnalytics y el resto (no los necesitas por ahora)
6. Target: `iosApp`. Haz clic en **Add Package**.

Xcode descargará el SDK y creará `iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`.

**Importante: commitea `Package.resolved`** para que CI (GitHub Actions) y otros desarrolladores usen exactamente la misma versión:

```bash
git add iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
git commit -m "chore(ios): pin Firebase SPM Package.resolved"
git push
```

---

## Paso 7 — Firma y capabilities

En Xcode, con el proyecto abierto:

### 7a. Firma

1. Selecciona el target `iosApp` en el panel izquierdo.
2. Pestaña **Signing & Capabilities**.
3. Marca **"Automatically manage signing"**.
4. **Team**: selecciona tu equipo de Apple Developer.
5. Xcode creará un **Provisioning Profile** automáticamente.

### 7b. Push Notifications capability

1. En la misma pestaña, haz clic en **"+ Capability"**.
2. Busca y añade **Push Notifications**.

### 7c. Background Modes capability

1. Haz clic en **"+ Capability"** de nuevo.
2. Busca y añade **Background Modes**.
3. Marca la casilla **"Remote notifications"**.

Xcode actualizará `iosApp.entitlements` automáticamente. El archivo ya existe en el repo con `aps-environment = development`; Xcode podría sobreescribirlo — comprueba que quede `development` (se cambia a `production` al archivar para TestFlight).

---

## Paso 8 — Primer build en simulador

```bash
# Opcionalmente, verifica que el framework KMP compila:
cd ..   # volver a la raíz del repo
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Luego en Xcode:

1. En la barra superior, selecciona **"iPhone 16"** (o cualquier simulador iOS 15+).
2. Pulsa **⌘R** (o el botón ▶ de play).
3. La primera vez tardará unos minutos — Xcode compila el framework KMP vía el Run Script.

**Lo que debería verse:**
- Splash con fondo teal `#1A7D7A`
- Pantalla de Login
- Registro + login funciona → aparecen las 5 tabs (Ágora, Comunidades, Actividades, Notificaciones, Perfil)

**Probar deep links desde el simulador:**

```bash
xcrun simctl openurl booted "agora://invite/TESTCODE123"
```

Esto debería abrir el `JoinCommunityScreen` con el código `TESTCODE123` pre-rellenado.

---

## Paso 9 — Prueba en iPhone físico (push notifications)

> El simulador NO recibe push reales. Para probar FCM necesitas un iPhone.

1. Conecta el iPhone con cable.
2. En Xcode, selecciona el iPhone en la barra de dispositivos.
3. **⌘R** — Xcode firma e instala la app.
4. Primera vez: el iPhone pedirá permiso en **Ajustes → Gestión general → Perfiles** para confiar en el desarrollador.
5. Abre la app → loguéate → acepta el permiso de notificaciones.
6. Espera ~10 segundos → comprueba en Supabase que `profiles.fcm_token` se actualizó para tu usuario.

**Enviar test push desde Firebase Console:**
1. Firebase Console → **Cloud Messaging** → **Send test message**.
2. Pega el FCM token que ves en los logs de Xcode (aparece en `messaging(_:didReceiveRegistrationToken:)` de `AppDelegate.swift`).
3. Rellena título y cuerpo → **Test**.
4. Debería aparecer el banner en el iPhone (en foreground) y en la barra de notificaciones (en background).

---

## Paso 10 — Habilitar el build completo en CI (GitHub Actions)

Una vez que `Package.resolved` esté commiteado (paso 6), activa el `xcodebuild` en `.github/workflows/ios.yml` descomentando el step:

```yaml
      - name: Build iOS app
        run: |
          xcodebuild \
            -project iosApp/iosApp.xcodeproj \
            -scheme iosApp \
            -sdk iphonesimulator \
            -configuration Debug \
            -destination 'generic/platform=iOS Simulator' \
            CODE_SIGN_IDENTITY="" \
            CODE_SIGNING_REQUIRED=NO \
            CODE_SIGNING_ALLOWED=NO \
            build
```

Haz un push y verifica que el workflow pasa verde en GitHub → Actions.

---

## Paso 11 — Preparar AppIcon PNGs

El catálogo de assets (`Assets.xcassets/AppIcon.appiconset/`) ya tiene el `Contents.json` listo. Solo faltan los PNGs.

**Fuente:** mismo diseño que Android — capitel jónico dorado (`#C49008`) sobre teal (`#1A7D7A`) con fondo crema (`#FAF6EE`). Ver `composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml`.

**Desde Mac (una vez que tengas el master 1024×1024 sin canal alpha):**

```bash
# Con ImageMagick (brew install imagemagick):
cd iosApp/iosApp/Assets.xcassets/AppIcon.appiconset

magick convert icon-master-1024.png -resize 20x20   Icon-20.png
magick convert icon-master-1024.png -resize 29x29   Icon-29.png
magick convert icon-master-1024.png -resize 40x40   Icon-40.png
magick convert icon-master-1024.png -resize 58x58   Icon-58.png
magick convert icon-master-1024.png -resize 60x60   Icon-60.png
magick convert icon-master-1024.png -resize 80x80   Icon-80.png
magick convert icon-master-1024.png -resize 87x87   Icon-87.png
magick convert icon-master-1024.png -resize 120x120 Icon-120.png
magick convert icon-master-1024.png -resize 152x152 Icon-152.png
magick convert icon-master-1024.png -resize 167x167 Icon-167.png
magick convert icon-master-1024.png -resize 180x180 Icon-180.png
cp icon-master-1024.png Icon-1024.png
```

O usa https://appicon.co (drag-and-drop → descarga el set completo → copia los PNGs aquí).

Luego borra el `README.md` de esta carpeta y haz commit de los PNGs.

---

## Paso 12 — Publicar en TestFlight (cuando estés listo)

1. En `iosApp/iosApp/iosApp.entitlements` cambia `development` a `production`:
   ```xml
   <key>aps-environment</key>
   <string>production</string>
   ```
2. En Xcode: **Product → Archive**.
3. En el Organizer que se abre: **Distribute App → App Store Connect → Upload**.
4. En https://appstoreconnect.apple.com → tu app → TestFlight → el build aparecerá en ~5 min.
5. Activa TestFlight Beta Testing → añade testers (email) → reciben una invitación.

---

## Checklist rápido

- [ ] Cuenta Apple Developer activa ($99)
- [ ] APNs Auth Key `.p8` descargada y subida a Firebase Console
- [ ] `GoogleService-Info.plist` colocado en `iosApp/iosApp/` (NO commiteado)
- [ ] `xcodegen generate` ejecutado → `iosApp.xcodeproj` creado
- [ ] Firebase SDK (FirebaseMessaging) añadido via SPM en Xcode
- [ ] `Package.resolved` commiteado
- [ ] Signing & Capabilities: Push Notifications + Background Modes (Remote notifications)
- [ ] Build en simulador: Login + 5 tabs funcionando
- [ ] Deep link `agora://invite/TEST` desde simulador: abre JoinCommunityScreen
- [ ] Build en iPhone físico: permiso push concedido, FCM token en `profiles.fcm_token`
- [ ] Test push desde Firebase Console: banner en foreground + background
- [ ] Session persistence: cerrar y reabrir → usuario sigue logueado
- [ ] AppIcon PNGs añadidos al catálogo
- [ ] CI (GitHub Actions) paso `xcodebuild` activado y verde
