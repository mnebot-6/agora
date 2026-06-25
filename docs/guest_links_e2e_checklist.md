# Guest activity links — checklist end-to-end

Marca cada caja a medida que validas. Si algo falla, anota el paso y para — no sigas.

## 0. Pre-vuelo (una vez)

- [x] Supabase → Auth → Providers → **Anonymous** activado *(verificado 2026-06-25)*
- [x] Migraciones 024–027 aplicadas *(lanzadas manualmente; smoke check ejecutó sin errores)*
- [x] Las 6 RPCs existen (query 2) *(implícito: el script habría errado si faltase)*
- [x] `slots.is_guest` existe + status admite `'pending'` (query 3) *(implícito)*
- [x] Tabla `activity_guest_requests` existe (query 4) *(implícito)*
- [x] Trigger `handle_new_user` con default `'Invitado'` (query 6) *(implícito)*
- [x] RLS de profiles permite a admin leer nombre de invitado (query 7) *(verificado 2026-06-25, 4 policies incluida "Community members can view guest profiles")*
- [x] Worker desplegado en `share-agora.app` *(curl 200 + HIT cache 2026-06-25)*
- [x] `curl -I https://share-agora.app/a/X` → **200** con HTML *(verificado)*
- [x] APK con commits `5ba3bcf` → `cbbe3e2` instalado en dispositivo admin *(2026-06-25)*
- [x] Existe una **comunidad pública** con una actividad futura con aforo > 0 para probar *("Test 26/06 n1", 12 plazas)*

## Resultado prueba E2E 2026-06-25

**Camino feliz (aforo libre):** ✅ generar link, abrir en incógnito, rellenar, push al admin, aceptar, slot reservado.
**Rechazo:** ✅ slot liberado correctamente.
**Tap del push (todos los estados):** ✅ tras fixes:
  - Migración `20260625130000` (no afecta a este bug, pero acompaña)
  - Edge function `push-notification` — incluye `type` en data payload
  - `AgoraApplication` — Koin movido fuera de `MainActivity.onCreate` (evitaba crash en background)
  - `MainActivity` con `launchMode=singleTop`
  - `AppTabs.kt` — si ya en `ActivityDetailScreen` con misma id, `replace` para forzar refresh
**Actividades por posiciones:** ✅ tras migración `20260625130000` — `approve_guest_request` ahora ordena por nº total de posiciones del slot ASC (más restrictivo primero). Decisión de NO pre-bloquear slots en ningún modo se mantiene como diseño.

> **Nota sobre el smoke check**: el SQL editor de Supabase solo muestra el resultado de la última `SELECT` cuando lanzas varias seguidas. Si alguna de las queries 2–6 hubiera fallado por estructura faltante (función inexistente, columna inexistente, etc.), el script habría parado con error — por eso se marcan como verificadas implícitamente al no haber error. Si quieres ver cada resultado, ejecuta las queries individualmente.

---

## 1. Camino feliz — desde 0 hasta confirmado

**Actor A**: admin, en el móvil con la app.
**Actor B**: invitado, navegador en incógnito en PC (sin app).

### A — Generar link
- [ ] A abre la actividad → ve botón "Compartir como invitado" (o equivalente)
- [ ] A copia la URL → tiene forma `https://<dominio>/a/<code>` con code no vacío

### B — Abrir landing
- [ ] B pega el link en navegador incógnito → carga la página (no 404, no error JS)
- [ ] Muestra: nombre actividad, comunidad, fecha/hora, aforo (X/Y), posiciones disponibles
- [ ] Network tab: la llamada a `get_activity_guest_preview` devuelve 200

### B — Solicitar plaza
- [ ] B rellena nombre + teléfono + selecciona posición → "Solicitar plaza"
- [ ] La UI muestra estado "Pendiente de aprobación"
- [ ] Verifica en BD con query 8 del SQL: hay 1 fila en `activity_guest_requests` y 1 slot con `status='pending'`, `is_guest=true`
- [ ] El contador de aforo en la app de A baja en 1 al refrescar

### A — Recibir push y aprobar
- [ ] A recibe push "Nueva solicitud de invitado" (commit `cbbe3e2`)
- [ ] Tap del push abre directamente la pantalla de pending requests de **esa** actividad
- [ ] A ve el nombre + teléfono + posición del invitado
- [ ] A toca "Aprobar"
- [ ] Query 9 del SQL: slot pasa a confirmado y `profiles.display_name` del anon es el nombre real (no "Invitado")

### B — Ver confirmación
- [ ] B refresca la landing (o el polling lo hace solo) → estado pasa a "Confirmado"
- [ ] Aforo en la landing y en la app reflejan el cambio

---

## 2. Rechazo

- [ ] Repite pasos B→A hasta tener una solicitud pending
- [ ] A toca "Rechazar"
- [ ] Query 10: slot liberado, aforo restaurado al valor previo
- [ ] B ve estado "Rechazado" en la landing tras refresco

---

## 3. Casos límite

- [ ] **Aforo lleno**: deja la actividad sin slots libres → B intenta solicitar → mensaje claro "Sin plazas", no 500
- [ ] **Reintento del mismo invitado**: B en el mismo navegador intenta una segunda solicitud para la misma actividad → no crea duplicado (verifica con query 8)
- [ ] **Comunidad privada**: en una actividad de comunidad privada el botón "Compartir como invitado" **no aparece** (o aparece deshabilitado)
- [ ] **Tap del link con la app instalada** (Actor C en otro Android con la app): el link abre **nativo** en `GuestActivityScreen`, no el navegador
- [ ] **Cold start**: mata la app, toca el link → tras el splash aterriza en `GuestActivityScreen` con la actividad correcta (no en home)
- [ ] **Sesión anónima persistente**: cierra el navegador y vuelve a abrir el link → la landing reconoce al mismo invitado (no pide datos otra vez si ya hay solicitud)
- [ ] **Multi-dispositivo**: abre el link en otro navegador/PC → es un anon distinto, puede solicitar también

---

## 4. Limpieza / housekeeping

- [ ] Anon users de prueba en Supabase Auth (opcional borrarlos al terminar)
- [ ] Filas de prueba en `activity_guest_requests` borradas
- [ ] Slots `is_guest=true` de prueba borrados

---

## Si algo falla — qué mirar primero

| Síntoma | Mirar |
|---|---|
| Landing 404 | Worker no desplegado o ruta `/a/*` no apunta a `a/index.html` |
| Landing carga pero `get_activity_guest_preview` 401/403 | Anonymous provider OFF o RLS de la RPC |
| `signInAnonymously` falla | Anonymous provider OFF |
| Invitado se queda en "Invitado" tras aprobar | Migración 025 no aplicada |
| Push llega pero no navega | Logs FCM → revisar payload `data.activity_id` |
| Admin no ve el nombre del invitado | Falta RLS policy del commit `c31ed46` (query 7) |
