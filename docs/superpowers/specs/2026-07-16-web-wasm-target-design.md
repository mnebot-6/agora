# Agora Web (target `wasmJs`) — Diseño

**Fecha:** 2026-07-16 · **Estado:** aprobado por el usuario (brainstorm en sesión previa)

## Objetivo

Que los usuarios con iPhone puedan usar Agora **con paridad total** respecto a Android, vía
navegador (Safari móvil) e instalable como PWA. Motivación: la app crece sobre la comunidad
de voleyball del usuario y varios miembros tienen iOS.

## Decisión y alternativas descartadas

**Elegido: Enfoque 1 — añadir target `wasmJs` (Compose Multiplatform Web) a la app KMP existente.**
Una sola base de código; la web es un target más, no un proyecto aparte.

Descartados:
- **iOS nativo (KMP + CI en la nube):** sin Mac, $99/año de Apple y fricción de App Store → inviable para el usuario hoy. (El plan viejo de paridad iOS nativa queda EN PAUSA, no borrado: `~/.claude/plans/glittery-snuggling-dawn.md`.)
- **Web DOM aparte (rebuild):** paridad total exigiría reconstruir todas las pantallas y mantener dos UIs para siempre.

El usuario decidió **saltar el spike previo** e ir directo a Etapa 1; a cambio, el **hito 1
incluye un checkpoint obligatorio en iPhone real** (ver Hitos) antes de cablear el resto.

## Restricciones

- Desarrollo **solo en Windows** (sin Mac).
- **No romper Android** (app publicada en Play, v1.0.1, testers activos).
- **No tocar el backend** Supabase (proyecto `ckuwetftnkhbndolcnjw`).
- Coste ~0 (infra existente: Cloudflare Workers + share-agora.app).

## Estado actual verificado (2026-07-16)

- **Versiones:** Kotlin 2.1.10 · Compose Multiplatform 1.7.3 · Voyager 1.1.0-beta03 · Koin 4.0.2 · supabase-kt 3.1.1 · Ktor 3.1.1 · Coil 3.1.0.
- **Targets hoy:** `androidTarget` + iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`). Sin web.
- **Módulos:** `composeApp`, `core:{model,data,domain,ui,common}`, `feature:{auth,community,activity,reservation,notification}`.
- **`expect/actual` que necesitan actual `wasmJs` (solo 4):**
  1. `composeApp/src/commonMain/.../PushTokenProvider.kt` → stub en web (sin FCM; ver Notificaciones).
  2. `composeApp/src/commonMain/.../StatusBarEffect.kt` → no-op en web.
  3. `core/ui/src/commonMain/.../locale/LocalAppLocale.kt` → `navigator.language`.
  4. `core/ui/src/commonMain/.../share/InviteSharer.kt` → Web Share API con fallback a clipboard.
- **Motor Ktor por plataforma:** Android=okhttp, iOS=darwin (en `composeApp/build.gradle.kts` y `core/data/build.gradle.kts`). Web → añadir el artefacto JS de Ktor en los source sets `wasmJsMain` correspondientes. `SupabaseProvider` es común y no se toca.
- **Supabase listo para web:** anon key ya pública (se usa en `web/a/index.html`); Redirect URLs ya incluyen `https://share-agora.app/**`; Site URL = `https://share-agora.app`.
- **Web existente:** `web/` se sirve con un Worker de Cloudflare (`web/wrangler.toml`, name `share-agora`) con assets estáticos. Deploy con `wrangler deploy` (funciona, verificado hoy).

## Arquitectura

1. **Nuevo target** `wasmJs { browser() }` en `composeApp` y en cada módulo `core:*` / `feature:*`
   (todos son KMP; añadir el target y un source set `wasmJsMain` donde haga falta).
2. **Entry point web:** `composeApp/src/wasmJsMain/kotlin/main.kt` con `ComposeViewport` montando
   `App()`, más un `index.html` con pantalla de carga (el `.wasm` pesa varios MB; sin loading
   screen el usuario ve blanco).
3. **Routing/deep links:** parsear `window.location` al arrancar y alimentar el
   `DeepLinkHandler` común existente (invite codes `/c/{code}`, activity `/a/{code}`).
   Decidir URL de la app: recomendado `https://share-agora.app/app/` (mismo Worker, ruta nueva)
   para no gestionar otro dominio.
4. **Sesión/auth:** supabase-kt persiste sesión en web vía localStorage (multiplatform-settings).
   Verificar que la persistencia funciona tras recargar la página.
5. **PWA:** `manifest.json` (nombre, iconos, `display: standalone`) + service worker mínimo de
   cacheo del shell para "Añadir a pantalla de inicio" en iOS.
6. **Deploy:** los artefactos de `wasmJsBrowserDistribution` se copian a `web/app/` y se
   despliegan con el mismo `wrangler deploy`. Añadir ruta en `web/worker.js`.

## Notificaciones en web (alcance v1)

Sin FCM en el target web v1. La pestaña de notificaciones **sigue funcionando** (lee de la
tabla `notifications`); lo que no hay es push del sistema. Web Push en iOS Safari existe
(16.4+, requiere PWA instalada) pero queda **fuera de v1** — anotar como mejora.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| **UX canvas en Safari móvil** (teclado, foco de inputs, scroll) — el riesgo nº1 | Checkpoint obligatorio en iPhone real al final del hito 1, antes de cablear todo. Si falla y no hay workaround: subir Compose MP (1.8/1.9 mejoró texto en web) y reevaluar; último recurso, pivotar a web DOM (Enfoque 2 del brainstorm). |
| Voyager 1.1.0-beta03 sin soporte wasm completo | Probar en hito 1; si no compila, subir a la beta más reciente. |
| Compose MP 1.7.3 quizá corto en wasm | Aceptable subir CMP/Kotlin si hace falta — verificar que Android sigue compilando y pasa smoke test. |
| Descarga inicial de varios MB | Loading screen + cacheo agresivo (service worker + headers Cloudflare). |
| Coil/imagenes o Realtime (websockets) fallan en wasm | Ambos soportan wasm en las versiones actuales; verificar en hito 2 con pantalla real. |

## Hitos

1. **Esqueleto compila y corre** — target wasmJs en todos los módulos, entry point, motor Ktor
   JS, login funcional + lista de comunidades contra Supabase real, deploy a URL de prueba.
   **Checkpoint: probar login+scroll+teclado en iPhone real (Safari). No seguir sin pasarlo.**
2. **Paridad** — todos los features cableados, los 4 actuals implementados, sesión persistente,
   deep links web, imágenes y Realtime verificados.
3. **PWA + deploy final** — manifest, service worker, ruta `/app` en el Worker, pantalla de carga.
4. **QA iPhone** — pase completo de flujos en Safari móvil con un usuario iOS real de la
   comunidad de voleyball.

## Criterios de éxito

- Un amigo con iPhone entra en `share-agora.app/app`, inicia sesión, ve sus comunidades,
  se apunta a una actividad y recibe la experiencia equivalente a Android (sin push).
- Android intacto: la app de Play sigue compilando y funcionando sin cambios de comportamiento.
