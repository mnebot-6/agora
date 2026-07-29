# Agora Web (`wasmJs`) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir el target `wasmJs` (Compose Multiplatform Web) a la app KMP existente con paridad total, desplegado como PWA en `https://share-agora.app/app/` para usuarios iOS.

**Architecture:** La web es un target más de la base KMP: se añade `wasmJs { browser() }` a los 11 módulos, un entry point `main.kt` con `ComposeViewport`, 4 actuals web, y la distribución se copia a `web/app/` y se sirve con el Worker de Cloudflare existente. Deep links por query param (`/app/?c={code}`, `/app/?a={code}`) para evitar SPA fallback y problemas de rutas base.

**Tech Stack:** Kotlin 2.1.10 · Compose MP 1.7.3 · Voyager 1.1.0-beta03 · supabase-kt 3.1.1 · Ktor 3.1.1 (`ktor-client-js`) · multiplatform-settings `StorageSettings` · kotlinx-browser 0.3 · Cloudflare Workers.

**Spec:** `docs/superpowers/specs/2026-07-16-web-wasm-target-design.md`

---

## Hechos verificados durante la planificación (2026-07-16)

Todos los artefactos `-wasm-js` existen en Maven Central **en las versiones actuales del proyecto** (verificado con HTTP 200 al `.pom` de cada uno):

| Artefacto | Versión | wasm-js |
|---|---|---|
| `cafe.adriel.voyager:voyager-navigator` | 1.1.0-beta03 | ✅ |
| `io.github.jan-tennert.supabase:auth-kt` | 3.1.1 | ✅ |
| `io.ktor:ktor-client-js` | 3.1.1 | ✅ |
| `io.coil-kt.coil3:coil-compose` | 3.1.0 | ✅ |
| `com.mikepenz:multiplatform-markdown-renderer-m3` | 0.28.0 | ✅ |
| `com.russhwolf:multiplatform-settings` | 1.2.0 | ✅ |
| `io.insert-koin:koin-core` | 4.0.2 | ✅ |
| `org.jetbrains.kotlinx:kotlinx-browser` | 0.3 | ✅ |

**Conclusión: no hace falta subir ninguna versión a priori.** El escenario "subir Compose MP/Voyager" queda como contingencia, no como tarea. Esto protege la restricción "no romper Android".

### Decisiones tomadas en el plan (desviaciones menores de la spec, con motivo)

1. **Los 4 actuals `wasmJs` se implementan en el Hito 1, no en el 2.** La compilación de `core:ui` y `composeApp` para wasm **exige** que existan los actuals (error de compilación si faltan). La spec los lista en Hito 2; ahí quedan su *verificación en runtime*.
2. **Deep links por query param** (`/app/?c={code}` y `/app/?a={code}`) en vez de rutas (`/app/c/{code}`). Motivo: con rutas, el `index.html` servido en `/app/c/XYZ` rompería las referencias relativas a `composeApp.js`/`.wasm` (haría falta SPA fallback en el Worker + rutas absolutas que a su vez rompen el dev server local). Con query params: cero cambios en el Worker, funciona igual en dev y prod. Las landings `/c/{code}` y `/a/{code}` existentes enlazan a la forma con query.
3. **Requisito de navegador (¡importante para la comunidad!):** Kotlin/Wasm requiere WasmGC → **Safari/iOS 18.2+** (dic 2024), Chrome 119+, Firefox 120+. Miembros con iPhone sin actualizar a iOS 18.2 **no podrán** usar la web app. El `index.html` muestra un aviso si la carga no arranca.
4. **TDD real solo donde hay lógica:** el parser de deep links web (única lógica pura nueva) se hace con test primero e inaugura la infraestructura de tests del repo (hoy no hay ningún test). El resto es configuración de build, stubs y pegamento de APIs de navegador: se verifica con tareas de compilación y comprobación manual en navegador, no con teatro de tests.

### Estructura de archivos (mapa completo del plan)

```
gradle/libs.versions.toml                 (M) + ktor-client-js, kotlinx-browser
core/model/build.gradle.kts               (M) + wasmJs target
core/common/build.gradle.kts              (M) + wasmJs target
core/data/build.gradle.kts                (M) + wasmJs target + ktor-client-js
core/domain/build.gradle.kts              (M) + wasmJs target
core/ui/build.gradle.kts                  (M) + wasmJs target
feature/{auth,community,activity,reservation,notification}/build.gradle.kts (M) + wasmJs target
core/ui/src/wasmJsMain/kotlin/com/app/community/core/ui/locale/LocalAppLocale.wasmJs.kt   (C) actual
core/ui/src/wasmJsMain/kotlin/com/app/community/core/ui/share/InviteSharer.wasmJs.kt      (C) actual
composeApp/build.gradle.kts               (M) + wasmJs target/executable + deps + syncWebApp + kotlin-test
composeApp/src/wasmJsMain/kotlin/com/app/community/PushTokenProvider.kt   (C) actual stub
composeApp/src/wasmJsMain/kotlin/com/app/community/StatusBarEffect.kt     (C) actual no-op
composeApp/src/commonMain/kotlin/com/app/community/WebDeepLink.kt         (C) parser puro
composeApp/src/commonTest/kotlin/com/app/community/WebDeepLinkTest.kt     (C) primer test del repo
composeApp/src/wasmJsMain/kotlin/main.kt                                  (C) entry point
composeApp/src/wasmJsMain/resources/index.html                            (C) shell + loading
composeApp/src/wasmJsMain/resources/manifest.json                         (C) PWA (Hito 3)
composeApp/src/wasmJsMain/resources/sw.js                                 (C) PWA (Hito 3)
composeApp/src/wasmJsMain/resources/icon-192.png                          (C) PWA (Hito 3)
web/a/index.html                          (M) botón "Abrir en navegador" (Hito 2)
web/c/index.html                          (M) botón "Abrir en navegador" (Hito 2)
web/worker.js                             (M) solo si /app sin barra no resuelve (contingencia)
.gitignore                                (M) + /web/app/
kotlin-js-store/yarn.lock                 (C, autogenerado — commitear)
```

Convenciones del repo que hay que respetar: FQN para `@OptIn` en los build files de módulos librería, comentarios en español, commits convencionales (`build:`, `feat(web):`, `test:`, `docs:`).

### Comandos de verificación recurrentes

- Compilar wasm de un módulo: `./gradlew :core:model:compileKotlinWasmJs`
- **Gate Android (tras CUALQUIER cambio de build files):** `./gradlew :composeApp:assembleDebug` → `BUILD SUCCESSFUL`
- Dev server web: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` (sirve en `http://localhost:8080`, tarea bloqueante → lanzar en background)
- Distribución + copia a web/: `./gradlew :composeApp:syncWebApp`
- Deploy: `cd web && npx wrangler deploy`

---

# HITO 1 — Esqueleto compila, corre y se despliega

### Task 1: Catálogo de versiones — artefactos web

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Añadir las dos librerías nuevas al catálogo**

En `gradle/libs.versions.toml`, tras la línea `ktor-client-darwin = ...` (línea 39):

```toml
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }
```

Y al final de la sección `# Kotlin` de `[libraries]` (tras `kotlinx-serialization-json`, línea 25):

```toml
kotlinx-browser = { module = "org.jetbrains.kotlinx:kotlinx-browser", version = "0.3" }
```

- [ ] **Step 2: Verificar que el catálogo sigue siendo válido**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL` (un error de sintaxis TOML rompería la configuración).

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build(web): add ktor-client-js and kotlinx-browser to version catalog"
```

---

### Task 2: Target wasmJs en core:model y core:common

Los dos módulos sin dependencias conflictivas — prueba mínima de que el toolchain wasm funciona en esta máquina (descarga Node/binaryen la primera vez).

**Files:**
- Modify: `core/model/build.gradle.kts`
- Modify: `core/common/build.gradle.kts`

- [ ] **Step 1: Añadir el target a ambos módulos**

En **ambos** archivos, inmediatamente después del bloque `listOf(iosX64(), ...) { ... }` (el que termina con la `}` que cierra el `forEach`), insertar:

```kotlin
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
```

(Mismo estilo FQN que el `@OptIn` de `ExperimentalKotlinGradlePluginApi` ya presente en esos archivos.)

- [ ] **Step 2: Compilar wasm de ambos**

Run: `./gradlew :core:model:compileKotlinWasmJs :core:common:compileKotlinWasmJs`
Expected: `BUILD SUCCESSFUL`. La primera ejecución descarga Node.js y puede generar `kotlin-js-store/yarn.lock` en la raíz — es un lockfile, se commitea.

- [ ] **Step 3: Gate Android**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add core/model/build.gradle.kts core/common/build.gradle.kts kotlin-js-store
git commit -m "build(web): add wasmJs target to core:model and core:common"
```

---

### Task 3: Target wasmJs en core:data (supabase-kt + Ktor JS)

La prueba de fuego de la dependencia más crítica: supabase-kt en wasm.

**Files:**
- Modify: `core/data/build.gradle.kts`

- [ ] **Step 1: Añadir target y motor Ktor**

Tras el bloque de targets iOS (igual que en Task 2):

```kotlin
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
```

Y dentro de `sourceSets { ... }`, tras el bloque `iosMain.dependencies { ... }`:

```kotlin
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :core:data:compileKotlinWasmJs`
Expected: `BUILD SUCCESSFUL`. Si falla con "Could not resolve io.github.jan-tennert.supabase:*", parar y consultar la sección Contingencias.

- [ ] **Step 3: Commit**

```bash
git add core/data/build.gradle.kts
git commit -m "build(web): add wasmJs target to core:data with Ktor JS engine"
```

---

### Task 4: core:ui — target wasmJs + actuals de locale y share

`core:ui` no compila para wasm sin los dos actuals (`LocalAppLocale`, `InviteSharer`). Se hacen aquí, completos.

**Files:**
- Modify: `core/ui/build.gradle.kts`
- Create: `core/ui/src/wasmJsMain/kotlin/com/app/community/core/ui/locale/LocalAppLocale.wasmJs.kt`
- Create: `core/ui/src/wasmJsMain/kotlin/com/app/community/core/ui/share/InviteSharer.wasmJs.kt`

- [ ] **Step 1: Añadir el target a core/ui/build.gradle.kts**

Tras el bloque de targets iOS:

```kotlin
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
```

- [ ] **Step 2: Verificar que la compilación falla por actuals ausentes**

Run: `./gradlew :core:ui:compileKotlinWasmJs`
Expected: FAIL con `Expected object 'LocalAppLocale' has no actual declaration` (y lo mismo para `InviteSharer`). Esto confirma que el source set está bien cableado.

- [ ] **Step 3: Crear el actual de LocalAppLocale**

`core/ui/src/wasmJsMain/kotlin/com/app/community/core/ui/locale/LocalAppLocale.wasmJs.kt`:

```kotlin
package com.app.community.core.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

/**
 * Patrón oficial JetBrains para web:
 * https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html
 * El script del index.html intercepta `navigator.languages` y devuelve
 * `window.__customLocale` cuando está definido; aquí solo escribimos ese valor.
 */
private fun setCustomLocale(value: String?): Unit =
    js("void (window.__customLocale = value ?? undefined)")

actual object LocalAppLocale {
    private val LocalAppLocale = staticCompositionLocalOf { Locale.current }

    actual val current: String
        @Composable get() = LocalAppLocale.current.toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        setCustomLocale(value?.replace('_', '-'))
        return LocalAppLocale.provides(Locale.current)
    }
}
```

Nota wasmJs: `js()` solo puede ser el cuerpo-expresión de una función top-level — por eso `setCustomLocale` está fuera del object.

- [ ] **Step 4: Crear el actual de InviteSharer**

`core/ui/src/wasmJsMain/kotlin/com/app/community/core/ui/share/InviteSharer.wasmJs.kt`:

```kotlin
package com.app.community.core.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** ¿Hay Web Share API? (Safari iOS y Chrome sí; Firefox escritorio no.) */
private fun hasWebShare(): Boolean = js("typeof navigator.share === 'function'")

/** Abre el share-sheet nativo del navegador. */
private fun webShare(text: String): Unit = js("navigator.share({ text: text })")

/** Fallback: copiar al portapapeles y avisar. */
private fun copyToClipboard(text: String): Unit =
    js("navigator.clipboard.writeText(text).then(function () { alert('Enlace copiado al portapapeles') })")

actual class InviteSharer {
    actual fun share(text: String) {
        if (hasWebShare()) webShare(text) else copyToClipboard(text)
    }
}

@Composable
actual fun rememberInviteSharer(): InviteSharer = remember { InviteSharer() }
```

- [ ] **Step 5: Compilar (ahora sí) y gate Android**

Run: `./gradlew :core:ui:compileKotlinWasmJs :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL` en ambos. Esto también valida Coil 3.1.0 en wasm (dependencia de core:ui).

- [ ] **Step 6: Commit**

```bash
git add core/ui/build.gradle.kts core/ui/src/wasmJsMain
git commit -m "feat(web): wasmJs target for core:ui with locale and share actuals"
```

---

### Task 5: Target wasmJs en core:domain y los 5 módulos feature

Cambio mecánico e idéntico; valida Voyager y markdown-renderer en wasm.

**Files:**
- Modify: `core/domain/build.gradle.kts`
- Modify: `feature/auth/build.gradle.kts`
- Modify: `feature/community/build.gradle.kts`
- Modify: `feature/activity/build.gradle.kts`
- Modify: `feature/reservation/build.gradle.kts`
- Modify: `feature/notification/build.gradle.kts`

- [ ] **Step 1: Añadir el target a los 6 módulos**

En cada uno de los 6 archivos, tras el bloque de targets iOS, el mismo snippet exacto:

```kotlin
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
```

- [ ] **Step 2: Compilar todos**

Run: `./gradlew :core:domain:compileKotlinWasmJs :feature:auth:compileKotlinWasmJs :feature:community:compileKotlinWasmJs :feature:activity:compileKotlinWasmJs :feature:reservation:compileKotlinWasmJs :feature:notification:compileKotlinWasmJs`
Expected: `BUILD SUCCESSFUL`. Aquí se resuelven por primera vez Voyager (`:feature:auth`) y markdown-renderer (`:feature:activity`) para wasm.

- [ ] **Step 3: Gate Android**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add core/domain/build.gradle.kts feature/*/build.gradle.kts
git commit -m "build(web): add wasmJs target to core:domain and all feature modules"
```

---

### Task 6: composeApp — target ejecutable wasmJs + actuals de push y status bar

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/wasmJsMain/kotlin/com/app/community/PushTokenProvider.kt`
- Create: `composeApp/src/wasmJsMain/kotlin/com/app/community/StatusBarEffect.kt`

- [ ] **Step 1: Añadir import, target y dependencias en composeApp/build.gradle.kts**

Añadir a los imports del principio del archivo:

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
```

Tras el bloque `listOf(iosX64(), ...) { ... }` de targets iOS:

```kotlin
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }
```

Y en `sourceSets { ... }`, tras `iosMain.dependencies { ... }`:

```kotlin
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }
```

- [ ] **Step 2: Crear el actual de PushTokenProvider (stub — sin FCM en web v1)**

`composeApp/src/wasmJsMain/kotlin/com/app/community/PushTokenProvider.kt`:

```kotlin
package com.app.community

// Sin push en web v1 (Web Push en iOS Safari requiere PWA instalada y 16.4+;
// anotado como mejora futura en la spec). App.kt ignora el token cuando es null.
actual suspend fun fetchPushToken(): String? = null
```

- [ ] **Step 3: Crear el actual de StatusBarEffect (no-op)**

`composeApp/src/wasmJsMain/kotlin/com/app/community/StatusBarEffect.kt`:

```kotlin
package com.app.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun StatusBarEffect(statusBarColor: Color, darkIcons: Boolean) {
    // En web no hay status bar del sistema que pintar. El color de la barra
    // del navegador/PWA se controla con <meta name="theme-color"> en index.html.
}
```

- [ ] **Step 4: Compilar wasm y gate Android**

Run: `./gradlew :composeApp:compileKotlinWasmJs :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL` en ambos. (Aún no hay `main.kt`; compilar el ejecutable completo llega en Task 8.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/wasmJsMain
git commit -m "feat(web): wasmJs executable target for composeApp with push/statusbar actuals"
```

---

### Task 7: Parser de deep links web (TDD — primer test del repo)

Única lógica pura nueva. Vive en `commonMain` para poder testearla en JVM (los tests wasm necesitarían un navegador).

**Files:**
- Modify: `composeApp/build.gradle.kts` (dependencia de test)
- Create: `composeApp/src/commonTest/kotlin/com/app/community/WebDeepLinkTest.kt`
- Create: `composeApp/src/commonMain/kotlin/com/app/community/WebDeepLink.kt`

- [ ] **Step 1: Añadir kotlin-test a commonTest**

En `composeApp/build.gradle.kts`, dentro de `sourceSets { ... }`, tras el bloque `wasmJsMain.dependencies { ... }` añadido en Task 6:

```kotlin
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
```

- [ ] **Step 2: Escribir el test que falla**

`composeApp/src/commonTest/kotlin/com/app/community/WebDeepLinkTest.kt`:

```kotlin
package com.app.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebDeepLinkTest {

    @Test
    fun parsesInviteCodeFromQuery() {
        assertEquals(WebDeepLink.Invite("ABC123"), parseWebDeepLink("?c=ABC123"))
    }

    @Test
    fun parsesActivityCodeFromQuery() {
        assertEquals(WebDeepLink.Activity("XYZ789"), parseWebDeepLink("?a=XYZ789"))
    }

    @Test
    fun parsesCodeAmongOtherParams() {
        assertEquals(WebDeepLink.Activity("XYZ"), parseWebDeepLink("?utm_source=share&a=XYZ"))
    }

    @Test
    fun inviteWinsWhenBothParamsPresent() {
        assertEquals(WebDeepLink.Invite("AAA"), parseWebDeepLink("?c=AAA&a=BBB"))
    }

    @Test
    fun decodesPercentEncodedCode() {
        assertEquals(WebDeepLink.Invite("A B"), parseWebDeepLink("?c=A%20B"))
    }

    @Test
    fun returnsNullForEmptyOrIrrelevantQuery() {
        assertNull(parseWebDeepLink(""))
        assertNull(parseWebDeepLink("?"))
        assertNull(parseWebDeepLink("?c="))
        assertNull(parseWebDeepLink("?foo=bar"))
    }
}
```

- [ ] **Step 3: Verificar que falla**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: FAIL — `Unresolved reference: WebDeepLink` (error de compilación; equivale al "test rojo").

- [ ] **Step 4: Implementar el parser**

`composeApp/src/commonMain/kotlin/com/app/community/WebDeepLink.kt`:

```kotlin
package com.app.community

/** Deep link llegado por URL en el target web: /app/?c={invite} | /app/?a={activity}. */
sealed interface WebDeepLink {
    data class Invite(val code: String) : WebDeepLink
    data class Activity(val code: String) : WebDeepLink
}

/**
 * Parsea el query string de la URL (p. ej. "?c=ABC123") a un deep link.
 * Si vienen ambos parámetros, gana la invitación a comunidad.
 */
fun parseWebDeepLink(search: String): WebDeepLink? {
    val params = search.removePrefix("?")
        .split('&')
        .mapNotNull { param ->
            val separator = param.indexOf('=')
            if (separator <= 0) null
            else param.take(separator) to percentDecode(param.substring(separator + 1))
        }
        .toMap()
    params["c"]?.takeIf { it.isNotEmpty() }?.let { return WebDeepLink.Invite(it) }
    params["a"]?.takeIf { it.isNotEmpty() }?.let { return WebDeepLink.Activity(it) }
    return null
}

/** Decodificación percent-encoding mínima (los códigos son alfanuméricos; esto cubre el caso raro). */
private fun percentDecode(value: String): String {
    val sb = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        if (ch == '%' && i + 2 < value.length) {
            val code = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (code != null) {
                sb.append(code.toChar())
                i += 3
                continue
            }
        }
        sb.append(if (ch == '+') ' ' else ch)
        i++
    }
    return sb.toString()
}
```

- [ ] **Step 5: Verificar que pasa**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonTest composeApp/src/commonMain/kotlin/com/app/community/WebDeepLink.kt composeApp/build.gradle.kts
git commit -m "feat(web): web deep link parser with first unit tests"
```

---

### Task 8: Entry point web + index.html con pantalla de carga

**Files:**
- Create: `composeApp/src/wasmJsMain/kotlin/main.kt`
- Create: `composeApp/src/wasmJsMain/resources/index.html`

- [ ] **Step 1: Crear main.kt**

`composeApp/src/wasmJsMain/kotlin/main.kt`:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.app.community.App
import com.app.community.DeepLinkHandler
import com.app.community.WebDeepLink
import com.app.community.di.appModules
import com.app.community.parseWebDeepLink
import com.russhwolf.settings.StorageSettings
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.configureWebResources
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    // En producción la app vive bajo /app/; en el dev server local, en la raíz.
    val basePath = if (window.location.pathname.startsWith("/app")) "/app/" else "/"
    configureWebResources {
        resourcePathMapping { path -> "$basePath$path" }
    }

    // StorageSettings = localStorage. Mismo rol que SharedPreferences en Android.
    startKoin {
        modules(appModules(StorageSettings()))
    }

    // Deep links web: /app/?c={inviteCode} | /app/?a={activityCode}
    when (val link = parseWebDeepLink(window.location.search)) {
        is WebDeepLink.Invite -> DeepLinkHandler.setInviteCode(link.code)
        is WebDeepLink.Activity -> DeepLinkHandler.setActivityCode(link.code)
        null -> Unit
    }

    ComposeViewport(document.body!!) {
        LaunchedEffect(Unit) {
            // Compose ya montó: retirar la pantalla de carga del index.html.
            document.getElementById("agora-loading")?.remove()
        }
        App()
    }
}
```

- [ ] **Step 2: Crear index.html**

`composeApp/src/wasmJsMain/resources/index.html` (colores de marca de `web/index.html`: fondo `#F6F2EA`, verde `#2E6B3A`):

```html
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover, user-scalable=no">
    <meta name="theme-color" content="#2E6B3A">
    <title>Agora</title>
    <script>
        // Permite a LocalAppLocale (wasmJs) cambiar el idioma en runtime:
        // Compose lee navigator.languages; lo interceptamos con window.__customLocale.
        var currentLanguagesImplementation = Object.getOwnPropertyDescriptor(Navigator.prototype, "languages");
        var newLanguagesImplementation = Object.assign({}, currentLanguagesImplementation, {
            get: function () {
                if (window.__customLocale) {
                    return [window.__customLocale];
                } else {
                    return currentLanguagesImplementation.get.apply(this);
                }
            }
        });
        Object.defineProperty(Navigator.prototype, "languages", newLanguagesImplementation);
    </script>
    <style>
        html, body { margin: 0; padding: 0; height: 100%; overflow: hidden; background: #F6F2EA; }
        #agora-loading {
            position: fixed; inset: 0; display: flex; flex-direction: column;
            align-items: center; justify-content: center; gap: 1rem;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
            color: #2E6B3A; background: #F6F2EA; text-align: center; padding: 0 1.5rem;
        }
        #agora-loading .spinner {
            width: 40px; height: 40px; border: 4px solid rgba(46, 107, 58, 0.2);
            border-top-color: #2E6B3A; border-radius: 50%;
            animation: agora-spin 0.8s linear infinite;
        }
        @keyframes agora-spin { to { transform: rotate(360deg); } }
        #agora-loading-hint { display: none; color: #555; font-size: 0.9rem; max-width: 320px; }
    </style>
    <script type="application/javascript" src="composeApp.js"></script>
</head>
<body>
<div id="agora-loading">
    <div class="spinner"></div>
    <div>Cargando Agora…</div>
    <div id="agora-loading-hint">
        ¿Sigue cargando? Agora web necesita un navegador reciente:
        iOS/Safari 18.2+, Chrome 119+ o Firefox 120+.
    </div>
</div>
<script>
    // Si tras 20s Compose no ha retirado el loader, probablemente el navegador
    // no soporta WasmGC → mostrar pista de versión mínima.
    setTimeout(function () {
        var hint = document.getElementById("agora-loading-hint");
        if (hint) hint.style.display = "block";
    }, 20000);
</script>
</body>
</html>
```

- [ ] **Step 3: Compilar el ejecutable completo**

Run: `./gradlew :composeApp:wasmJsBrowserDistribution`
Expected: `BUILD SUCCESSFUL`. Artefactos en `composeApp/build/dist/wasmJs/productionExecutable/` (index.html, composeApp.js, *.wasm, composeResources/).

- [ ] **Step 4: Arrancar el dev server y probar login real**

Run (en background): `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
Abrir `http://localhost:8080` en el navegador y verificar:
1. La pantalla de carga aparece y desaparece.
2. Se ve la pantalla de login (LoginScreen).
3. **Login contra Supabase real** (pedir al usuario que introduzca sus credenciales de prueba, o que lo haga él mismo en el navegador).
4. Tras login se ve el dashboard y la lista de comunidades con datos reales.
5. Consola del navegador sin errores fatales (warnings tolerables).

Expected: flujo completo funcional. Si algo falla → skill superpowers:systematic-debugging antes de tocar nada.

- [ ] **Step 5: Gate Android + tests**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/wasmJsMain
git commit -m "feat(web): wasm entry point with loading screen and web deep links"
```

---

### Task 9: Deploy a URL de prueba (share-agora.app/app)

**Files:**
- Modify: `composeApp/build.gradle.kts` (tarea syncWebApp)
- Modify: `.gitignore`
- Modify: `web/worker.js` (solo si hace falta — ver Step 4)

- [ ] **Step 1: Registrar la tarea de copia en composeApp/build.gradle.kts**

Al final del archivo (tras el bloque `if (file("google-services.json")...)`):

```kotlin
// Copia la distribución web (wasm) a web/app para desplegarla con `wrangler deploy`.
tasks.register<Sync>("syncWebApp") {
    dependsOn("wasmJsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(rootProject.layout.projectDirectory.dir("web/app"))
}
```

- [ ] **Step 2: Ignorar los artefactos en git**

En `.gitignore`, tras la línea `/feature/*/build`:

```
# Distribución web wasm (se regenera con :composeApp:syncWebApp)
/web/app/
```

- [ ] **Step 3: Generar y desplegar**

```bash
./gradlew :composeApp:syncWebApp
cd web && npx wrangler deploy
```

Expected: deploy OK con los assets nuevos bajo `app/` (el Worker ya sirve assets estáticos; los archivos `.wasm` de varios MB están dentro del límite de 25 MiB/archivo de Cloudflare).

- [ ] **Step 4: Verificar la URL y decidir si el Worker necesita cambio**

Abrir `https://share-agora.app/app/` → debe cargar la app (asset handling de Cloudflare sirve `app/index.html` automáticamente). Probar también `https://share-agora.app/app` (sin barra): con `html_handling` por defecto (`auto-trailing-slash`) redirige solo. **Solo si** el `/app` sin barra devolviera 404, añadir al principio del `fetch` de `web/worker.js`:

```js
    // La web app (Compose wasm) vive bajo /app/ — normalizar /app → /app/.
    if (url.pathname === "/app") {
      return Response.redirect(url.origin + "/app/", 301);
    }
```

y redesplegar.

- [ ] **Step 5: Smoke test en producción**

En `https://share-agora.app/app/`: login + dashboard + lista de comunidades, igual que en Task 8 Step 4. Verificar en las herramientas de red que `composeApp.wasm` y `composeResources/` cargan desde `/app/`.

- [ ] **Step 6: Commit**

```bash
git add composeApp/build.gradle.kts .gitignore web/worker.js
git commit -m "feat(web): deploy wasm app to share-agora.app/app via syncWebApp task"
```

---

### Task 10: 🛑 CHECKPOINT OBLIGATORIO — iPhone real (Safari)

**GATE: no se avanza al Hito 2 sin pasar esto.** (Condición de la spec al saltarse el spike.)

- [ ] **Step 1: Protocolo de prueba para el usuario en su iPhone**

Pedir al usuario que en Safari de su iPhone (⚠️ requiere iOS 18.2+; comprobar versión en Ajustes → General → Información):

1. Abrir `https://share-agora.app/app/` — ¿carga? ¿cuánto tarda la primera vez? ¿y la segunda?
2. **Teclado:** tocar el campo email del login → ¿aparece el teclado? ¿se puede escribir con `@`, acentos y autocorrección? ¿el campo queda visible (no tapado por el teclado)? Ídem contraseña (¿oculta caracteres?). ¿Funciona pegar desde el portapapeles?
3. **Login:** completar login real → ¿entra al dashboard?
4. **Scroll:** en dashboard y lista de comunidades → ¿scroll táctil fluido con inercia? ¿sin "gomas" raras ni saltos?
5. **Ciclo de vida:** ir a otra app y volver → ¿sigue viva la sesión y la pantalla? Recargar la página → ¿sigue logueado?

- [ ] **Step 2: Registrar el resultado**

Anotar el resultado (incluida la versión de iOS y el tiempo de carga) al final de este plan en una sección `## Resultado checkpoint Hito 1`, y commitear:

```bash
git add docs/superpowers/plans/2026-07-16-web-wasm-target.md
git commit -m "docs(web): record milestone 1 iPhone checkpoint result"
```

- [ ] **Step 3: Decidir**

- ✅ **Pasa (login + scroll + teclado usables):** continuar al Hito 2.
- ❌ **Falla:** parar. Aplicar la escalera de mitigación de la spec, en orden: (1) buscar workaround puntual; (2) subir Compose MP (1.8.x mejoró el texto en web — ver Contingencias) y re-testear; (3) último recurso: sesión de brainstorming para pivotar a web DOM. No cablear más features sobre una base que no funciona en el dispositivo objetivo.

## Resultado checkpoint Hito 1

**2026-07-22 — ✅ PASA (preliminar).** El usuario probó `https://share-agora.app/app/` en su iPhone real (Safari) y reporta que "parece que va bien": login + scroll + teclado usables. Gate superado → se continúa al Hito 2. El pase exhaustivo (con un usuario iOS de la comunidad y todos los flujos) queda para la Task 16 (QA). Además se adelantó la Task 12 (botón "Abrir en el navegador" en las landings `/c` y `/a`) a petición del usuario, ya desplegada.

---

# HITO 2 — Paridad

### Task 11: Persistencia de sesión web

> **RESUELTA POR ANÁLISIS (2026-07-22): persiste por defecto, sin cambios de código.** En
> supabase-kt 3.1.1, `Auth.createDefaultSessionManager()` (source set `settingsMain`, que incluye
> wasmJs) devuelve `SettingsSessionManager` (localStorage) salvo cuando `IS_NODE` → el navegador
> wasmJs usa localStorage. El log de consola confirma un backend de storage activo
> ("Trying to load latest session from storage"). NO hace falta el Step 2 (no se toca
> `SupabaseProvider`, con lo que Android queda intacto). Solo falta confirmación empírica del
> usuario: login → recargar → sigue dentro.

- [ ] **Step 1: Verificar el comportamiento actual**

Con el dev server corriendo (`http://localhost:8080`), hacer login y recargar la página.
Expected: tras el loader, entra directo al dashboard sin pedir login (supabase-kt guarda la sesión en localStorage en los targets web). Verificar en DevTools → Application → Local Storage que existe una entrada de sesión de supabase.

- [ ] **Step 2 (solo si NO persiste): configurar el session manager explícitamente**

Si al recargar vuelve al login: en `core/data/src/commonMain/.../SupabaseProvider.kt` no hay acceso a `Settings`; la solución es inyectarlo — añadir en `install(Auth) { ... }` un `sessionManager = SettingsSessionManager(settings)` pasando el `Settings` de Koin. Eso exige convertir `SupabaseProvider` de `object` a clase inicializada con `Settings` (o un `lateinit` seteado desde `main.kt`/`AgoraApplication`). Hacerlo con TDD manual: reproducir → cambiar → verificar recarga en web **y** que Android sigue persistiendo sesión (smoke en emulador o dispositivo).

- [ ] **Step 3: Commit (si hubo cambios)**

```bash
git add -A && git commit -m "fix(web): persist supabase session across page reloads"
```

---

### Task 12: Deep links e2e + botón "Abrir en navegador" en las landings

**Files:**
- Modify: `web/a/index.html`
- Modify: `web/c/index.html`

- [ ] **Step 1: Añadir el enlace a la web app en ambas landings**

En `web/a/index.html` y `web/c/index.html`, justo antes de `</body>`, añadir (mismo snippet en ambos — detecta el código del path y arma la URL con query param):

```html
  <p style="text-align:center; margin-top:2rem;">
    <a id="agora-open-web" href="/app/"
       style="display:inline-block; padding:0.75rem 1.5rem; background:#2E6B3A; color:#fff; border-radius:8px; text-decoration:none;">
      Abrir Agora en el navegador
    </a>
  </p>
  <script>
    (function () {
      // El path es /a/{code} o /c/{code}; la web app espera /app/?a={code} | /app/?c={code}.
      var seg = location.pathname.split("/").filter(Boolean);
      var link = document.getElementById("agora-open-web");
      if (seg.length >= 2 && link) {
        link.href = "/app/?" + seg[0] + "=" + encodeURIComponent(seg[1]);
      }
    })();
  </script>
```

(Si la página ya tiene estructura/estilos propios que chocan, integrar el enlace respetando su diseño — lo intocable es el `href` resultante.)

- [ ] **Step 2: Verificar e2e los dos flujos en local**

Con el dev server local no hay landing; probar directo con query params:
1. `http://localhost:8080/?c={código de invitación válido}` logueado → debe saltar al tab Comunidades y auto-unirse (AutoJoinByInviteScreen).
2. `http://localhost:8080/?a={código de actividad válido}` **sin** sesión → debe entrar como invitado anónimo a la actividad (GuestActivityScreen).

(Pedir al usuario códigos válidos de su comunidad de prueba, o crearlos desde la app Android.)

- [ ] **Step 3: Desplegar y verificar en producción**

```bash
./gradlew :composeApp:syncWebApp
cd web && npx wrangler deploy
```

Abrir `https://share-agora.app/c/{code}` → botón visible → clic → la web app arranca y auto-une. Ídem `/a/{code}`.

- [ ] **Step 4: Commit**

```bash
git add web/a/index.html web/c/index.html
git commit -m "feat(web): landing pages link to web app with deep link codes"
```

---

### Task 13: Barrido de paridad — todos los features en navegador

Verificación exploratoria guiada por checklist; los bugs que salgan se arreglan con superpowers:systematic-debugging, cada fix con su commit.

- [ ] **Step 1: Pasar el checklist completo en desktop (localhost o /app)**

| Área | Qué verificar |
|---|---|
| Dashboard (tab Agora) | actividades con slots, saludo, datos reales |
| Comunidades | lista, detalle, crear comunidad, explorar, unirse, preview |
| Chat comunidad | **Realtime**: enviar/recibir mensajes en vivo (abrir 2 pestañas) |
| Miembros | gestión, solicitudes de ingreso, bloquear/reportar |
| Actividades | feed, detalle, crear, editar, apuntarse/borrarse de slots |
| Invitados | flujo guest por deep link (Task 12) + solicitud por email |
| Notificaciones | tab lee la tabla `notifications` (sin push, correcto en v1) |
| Perfil | editar, **tema oscuro** (recomposición), **cambio de idioma** (LocalAppLocale web), logout |
| Imágenes | **Coil**: avatares/imágenes de comunidad cargan desde Supabase Storage |
| Compartir | botón compartir invitación → Web Share (Chrome) o copia al portapapeles (Firefox) |
| ⚠️ Compartir link de invitado | **En Safari iOS específicamente** (hallazgo de code review): el flujo hace una llamada de red (generar link) ANTES de `navigator.share()`, y la *activación de usuario transitoria* puede caducar → share rechaza en silencio. Verificar en Safari real; si falla, reestructurar el flujo web para que `share()` corra dentro del gesto (pre-generar link o UI en dos pasos "generar → compartir"). |
| Reservas | pantallas del feature reservation |

- [ ] **Step 2: Registrar los hallazgos**

Añadir sección `## Hallazgos barrido de paridad` a este plan con una línea por bug (o "sin hallazgos"), para que quede rastro de qué se probó.

- [ ] **Step 3: Arreglar los bugs encontrados**

Uno a uno: reproducir → systematic-debugging → fix mínimo → re-verificar → commit individual (`fix(web): ...`). Si un fix toca código común, gate Android obligatorio: `./gradlew :composeApp:assembleDebug`.

- [ ] **Step 4: Gate Android final del hito**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Además, smoke manual en dispositivo/emulador Android: login + dashboard + un chat (10 min), porque los testers de Play siguen activos.

---

## Hallazgos barrido de paridad

**2026-07-29 — ✅ SIN HALLAZGOS ABIERTOS.** El usuario reporta el barrido completo pasado y
correcto. Los bugs que salieron durante la ejecución se arreglaron sobre la marcha, cada uno con
su commit y su gate Android:

| Bug | Fix |
|---|---|
| Tema e idioma se perdían al recrear el tema; se perdía la pestaña seleccionada | `374762e` |
| Links de actividad no abrían en la app teniendo sesión | `a75debe` |
| Compartir en desktop no hacía nada (sin Web Share API) | `ba2deed` (copia al portapapeles) |
| Tofu (cuadraditos) en Cinzel por carrera de carga asíncrona de fuente | `db53729` (preload antes de montar la UI) |

---

# HITO 3 — PWA + deploy final

### Task 14: Manifest, iconos y meta tags de PWA

**Files:**
- Create: `composeApp/src/wasmJsMain/resources/manifest.json`
- Create: `composeApp/src/wasmJsMain/resources/icon-192.png`
- Modify: `composeApp/src/wasmJsMain/resources/index.html`

- [ ] **Step 1: Copiar el icono**

Copiar `composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png` (192×192) a `composeApp/src/wasmJsMain/resources/icon-192.png`. (Si existe un arte de 512×512 —el icono de Play Store—, pedírselo al usuario y añadirlo como `icon-512.png` + su entrada en el manifest; si no, 192 basta: iOS usa `apple-touch-icon`, no el manifest.)

- [ ] **Step 2: Crear manifest.json**

```json
{
  "name": "Agora",
  "short_name": "Agora",
  "description": "Gestión de comunidades y actividades",
  "lang": "es",
  "start_url": "/app/",
  "scope": "/app/",
  "display": "standalone",
  "background_color": "#F6F2EA",
  "theme_color": "#2E6B3A",
  "icons": [
    { "src": "/app/icon-192.png", "sizes": "192x192", "type": "image/png" }
  ]
}
```

- [ ] **Step 3: Añadir los meta tags al index.html**

En el `<head>`, tras la línea `<meta name="theme-color" ...>`:

```html
    <link rel="manifest" href="/app/manifest.json">
    <link rel="apple-touch-icon" href="/app/icon-192.png">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="default">
    <meta name="apple-mobile-web-app-title" content="Agora">
```

(Rutas absolutas `/app/...`: en el dev server local darán 404 — inofensivo, la PWA solo aplica en producción.)

- [ ] **Step 4: Verificar en local que nada se rompió**

Run: `./gradlew :composeApp:wasmJsBrowserDistribution`
Expected: `BUILD SUCCESSFUL` y `manifest.json`, `icon-192.png` presentes en `composeApp/build/dist/wasmJs/productionExecutable/`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/wasmJsMain/resources
git commit -m "feat(web): PWA manifest, icon and iOS meta tags"
```

---

### Task 15: Service worker de cacheo del shell

**Files:**
- Create: `composeApp/src/wasmJsMain/resources/sw.js`
- Modify: `composeApp/src/wasmJsMain/resources/index.html`

- [ ] **Step 1: Crear sw.js**

Estrategia **network-first con fallback a caché**: nunca sirve una versión vieja estando online (evita el problema de .wasm/.js desincronizados tras un deploy) y deja la app utilizable offline tras la primera visita.

```js
// Service worker de Agora web: network-first con fallback a caché.
// Cachea en runtime todo GET bajo /app/ que responda OK.
const CACHE = "agora-app-v1";

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
```

- [ ] **Step 2: Registrarlo en index.html**

Antes de `</body>`, tras el script del timeout:

```html
<script>
    // Solo en producción (bajo /app/): cacheo del shell para PWA/offline.
    if ("serviceWorker" in navigator && location.pathname.startsWith("/app")) {
        window.addEventListener("load", function () {
            navigator.serviceWorker.register("/app/sw.js");
        });
    }
</script>
```

- [ ] **Step 3: Desplegar y verificar**

```bash
./gradlew :composeApp:syncWebApp
cd web && npx wrangler deploy
```

En `https://share-agora.app/app/` con DevTools: Application → Service Workers → activo; recargar → assets servidos ok; Network offline → la app shell aún carga (los datos de Supabase no, correcto).

- [ ] **Step 4: Verificar instalación PWA en iPhone**

Pedir al usuario: Safari → `share-agora.app/app/` → Compartir → **Añadir a pantalla de inicio** → abrir desde el icono. Expected: abre a pantalla completa (standalone, sin barra de Safari), icono correcto, login persiste.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/wasmJsMain/resources
git commit -m "feat(web): service worker with network-first shell caching"
```

---

# HITO 4 — QA en iPhone con usuario real

### Task 16: Pase completo de flujos en Safari móvil

- [ ] **Step 1: Sesión de QA con un usuario iOS de la comunidad de voleyball**

Guion (el criterio de éxito de la spec, ampliado):
1. Recibir un link de invitación `share-agora.app/c/{code}` (p. ej. por WhatsApp) → abrir → "Abrir Agora en el navegador" → registrarse/login → unirse a la comunidad.
2. Ver actividades, apuntarse a una, borrarse. **Incluir: compartir el link de invitado de una actividad (gap de activación de usuario en Safari — ver tabla del barrido de paridad).**
3. Chat de comunidad: enviar y recibir en vivo.
4. Perfil: cambiar tema e idioma.
5. Tab de notificaciones muestra los eventos.
6. Instalar como PWA y repetir 2-3 desde el icono.
7. Volver al día siguiente: ¿sesión viva?

- [ ] **Step 2: Registrar resultados y triaje**

Sección `## Resultado QA Hito 4` en este plan: qué pasó, qué falló, decisión por ítem (fix ahora / anotar para v2). Fixes con systematic-debugging + commit individual + gate Android.

- [ ] **Step 3: Cierre de la etapa**

- Actualizar memoria del proyecto (estado del target web: COMPLETADO/estado real).
- Skill superpowers:finishing-a-development-branch si se trabajó en rama.
- Anotar mejoras diferidas conocidas: Web Push (iOS 16.4+ con PWA), icono 512, cache-headers de Cloudflare para `.wasm`.

---

## Resultado QA Hito 4

**2026-07-29 — ✅ PASA.** El usuario da el testeo por bueno y cierra la etapa. La rama
`feature/web-wasm-target` (28 commits) se mergea a `main`.

**Mejoras diferidas conocidas** (no bloquean, para v2):
- Web Push en iOS (requiere PWA instalada, iOS 16.4+); hoy el actual de `PushTokenProvider` en web es un stub.
- Icono 512×512 en el manifest (hoy solo 192).
- Cache-headers de Cloudflare afinados para `.wasm`.

---

## Desviaciones aplicadas durante la ejecución (Task 8)

Al compilar la **distribución de producción** por primera vez surgieron dos problemas de infra que el plan no anticipó del todo. Ambos resueltos y commiteados:

1. **Descarga del toolchain wasm bloqueada por `PREFER_SETTINGS`** (commit `c6cc8e2`). `wasmJsBrowserDistribution` necesita Node.js, Yarn y binaryen, que los plugins de Kotlin descargan de `nodejs.org` / GitHub releases. Con `repositoriesMode = PREFER_SETTINGS` en `settings.gradle.kts`, Gradle ignora esos repos y busca los binarios como artefactos Maven → falla (`Could not find org.nodejs:node`, luego `com.github.webassembly:binaryen`). Fix: 3 repos Ivy scopeados por contenido (`org.nodejs:node`, `com.yarnpkg:yarn`, `com.github.webassembly:binaryen`) en `settings.gradle.kts`. No afecta a Android (gate verificado). Los `compileKotlinWasmJs` de Tasks 2–7 no lo detectaron porque no usan Node.
2. **OOM del daemon de Kotlin** (commit `c6cc8e2`). El compile de producción (con binaryen) petaba a `-Xmx2048M`. Fix aplicado según contingencia, pero sobre `kotlin.daemon.jvmargs` (no `org.gradle.jvmargs`): daemon de Kotlin a 4096M, Gradle a 3072M. RAM del equipo: 15.7 GB.

**Resultado Task 8:** distribución de producción `BUILD SUCCESSFUL` (wasm ~15 MiB combinado). Smoke test en navegador desktop (sirviendo la dist de producción con `python -m http.server`, porque el 8080 lo ocupa Adminer): la app monta (loader retirado, canvas 1280×720), `SupabaseClient created!`, Auth lee localStorage, sin errores fatales. Login con credenciales reales pendiente → se cubre en el checkpoint de iPhone (Task 10). Screenshots del canvas Compose se cuelgan (bucle de render); verificación hecha vía DOM+consola.

## Contingencias (no ejecutar salvo que se dispare la condición)

| Condición | Acción |
|---|---|
| Algún módulo no compila para wasm por una dependencia (error de resolución o de linkage) | Identificar el artefacto exacto en el error. Voyager: subir SOLO voyager a la beta más reciente (`1.1.0-beta03` → última) y recompilar Android. Si es Compose MP: subir CMP a 1.8.x (requiere Kotlin 2.1.20+: subir ambos en el mismo commit), luego `./gradlew :composeApp:assembleDebug` + smoke Android completo antes de seguir con web. |
| Checkpoint iPhone falla por teclado/scroll/foco | Escalera de la spec: workaround → CMP 1.8/1.9 (mejoras de texto en web) → re-test → si sigue fallando, brainstorming de pivote a web DOM. |
| `wasmJsBrowserDistribution` falla por memoria (Gradle OOM) | ✅ APLICADO en Task 8: `kotlin.daemon.jvmargs=-Xmx4096M` en `gradle.properties`. |
| La sesión no persiste tras recargar | Task 11 Step 2 (SettingsSessionManager explícito). |
| `outputFileName` no existe en `commonWebpackConfig` (API distinta en esta versión) | Quitar el bloque `commonWebpackConfig` — el nombre por defecto ya es `composeApp.js` (nombre del módulo Gradle). |
| `js()` con `?? undefined` no compila en `setCustomLocale` | Variante: dos funciones — `fun clearCustomLocale(): Unit = js("delete window.__customLocale")` y `fun setCustomLocaleValue(value: String): Unit = js("window.__customLocale = value")` — y elegir en Kotlin según `value == null`. |

## Qué NO hace este plan (a propósito)

- No toca el backend Supabase (ni migraciones, ni Redirect URLs — ya incluyen `https://share-agora.app/**`).
- No activa captcha/Turnstile (memoria del proyecto: diferido a antes del lanzamiento público).
- No implementa Web Push en web (fuera de v1 por spec).
- No sube ninguna versión de librería salvo contingencia disparada.
- No añade CI; el deploy sigue siendo `wrangler deploy` manual (igual que la web actual).
