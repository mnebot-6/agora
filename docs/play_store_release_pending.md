# Pendiente para subir Agora a Google Play

Última actualización: **2026-06-25 fin de sesión**.

App ya creada en Play Console:
- **URL**: https://play.google.com/console/u/1/developers/6831348944483601654/app/4974549159345002985
- **Account ID**: 6831348944483601654
- **App ID interno**: 4974549159345002985
- **Package name**: `app.shareagora.community`
- **Nombre temporal**: "Agora — Comunidades" → **cambiar a "Agora"** (ver paso 1 de mañana)
- **Idioma por defecto**: Español (España) — es-ES
- **Tipo**: App, gratuita
- **Play App Signing**: activado por defecto (✅ "Versiones firmadas por Google Play")

---

## ✅ Hecho en sesión 2026-06-25

| Item | Resultado |
|---|---|
| Adoptar Supabase CLI con baseline | `supabase migration list` Local=Remote |
| Aplicar migraciones 022 (delete_my_account) + 023 (moderación) | Aplicadas a prod vía `20260625140000_account_deletion_and_moderation.sql` |
| Privacy policy + Terms drafts rellenados | sin placeholders |
| Hospedar `/privacy` y `/terms` en share-agora.app | 200 OK ambas, sirve el Worker |
| Generar keystore release `agora-release.jks` | `C:\Users\mnebo\keys\agora-release.jks` |
| Crear `keystore.properties` (no en git) | OK |
| Cambiar package name `com.app.agora` → `app.shareagora.community` | Aplicado en Android, iOS, assetlinks, docs |
| Registrar nueva app Android en Firebase Console | `google-services.json` con ambos packages |
| Generar `.aab` firmado con la nueva applicationId | `composeApp\build\outputs\bundle\release\composeApp-release.aab` 9.3 MB |
| Crear app en Play Console + subir .aab a Prueba interna | Subida iniciada al cierre de sesión |
| Fix bugs guest-links (push tap + position assignment) | Migraciones + edge function + Android |
| Fix bug push tap no navegaba estando ya en la pantalla destino | `replace` en lugar de `push` cuando coincide id |

Commits locales sin push:
- `33a9f12` chore(supabase): adopt Supabase CLI workflow with baseline
- `330655f` fix(guest-links): push tap routing + position assignment restrictiveness
- `33d39ae` feat(play-mvp): account deletion + UGC moderation + legal pages
- `7a7aa3d` chore: rename Android/iOS package id to app.shareagora.community

---

## 🟢 Para retomar mañana — Por orden

### 1. Cambiar nombre de la app en Play Console
Está como "Agora — Comunidades" pero quiere ser "Agora" pelado. Navegar:
Play Console → app → **Crecer → Presencia en la tienda → Ficha principal de Play Store** → editar nombre → guardar.

### 2. Verificar que el .aab subido pasó procesamiento
Play tarda unos minutos en analizar el .aab tras la subida. Mañana revisar:
Play Console → app → **Prueba y publicación → Pruebas → Prueba interna → pestaña Versiones**.
Si dice "Listo para revisar" o similar, seguimos. Si hay warnings, ajustarlos.

### 3. Obtener SHA-256 del Play App Signing
Play Console → app → **Prueba y publicación → Ajustes → Integridad de la app → App signing key certificate** → copiar SHA-256.

Con ese SHA, actualizar `web/.well-known/assetlinks.json` (sustituir el SHA viejo) y redeployar el Worker (`cd web && npx wrangler deploy`). Esto hace que los App Links (`https://share-agora.app/c/*` y `/a/*`) abran la app de Play directo sin chooser.

### 4. Rellenar Detalles de la ficha de Play Store

Ficha principal → editar:
- **Nombre de la app**: `Agora`
- **Descripción corta** (80 chars):
  ```
  Organiza tu comunidad: actividades, plazas y reservas en un solo sitio.
  ```
- **Descripción completa** (4000 chars max) — texto preparado, copiar de la sección al final de este doc.
- **Categoría de la app**: Social (o Eventos)
- **Etiquetas**: Comunidad, Chat, Mensajería, Eventos, Equipos deportivos
- **Email de contacto**: `mnebotchirivella@gmail.com`
- **Sitio web** (opcional): `https://share-agora.app`

### 5. Activos gráficos (Bloqueante para Play)
- **Icono 512×512 PNG** — elegir entre `docs/icon.png` y `docs/icon2.png` y subirlo
- **Gráfico destacado 1024×500 PNG** — no existe aún; opciones:
  - Diseñarlo (banner con logo + tagline)
  - Pedir ayuda a un diseñador
  - Generar con un mockup tool
- **Capturas de pantalla** (min 2, max 8, 1080px lado largo) — capturar del emulador o móvil físico:
  - Pantalla de Explorar / Comunidades públicas
  - Detalle de actividad con plazas
  - Chat de comunidad
  - Notificaciones
  - Generación de link de invitado / panel admin (opcional)
  - Modo oscuro de cualquiera de las anteriores (opcional)

### 6. Tareas de configuración en Play Console

Panel de control → **Configura tu aplicación** → marcar:

- **Acceso a la app**: toda la funcionalidad accesible tras registro normal
- **Anuncios**: No
- **Clasificación del contenido**: hacer cuestionario IARC
  - Categoría: Comunicación / Redes sociales
  - Permite interacción usuario-usuario (chat): Sí
  - Comparte ubicación: No
  - Contenido UGC visible: Sí
  - Herramientas de moderación: Sí
- **Público objetivo y contenido**: 13+
- **¿App de noticias?**: No
- **¿Apps Covid?**: No
- **¿Apps gubernamentales?**: No
- **¿Funciones financieras?**: No
- **¿Apps de salud?**: No
- **Seguridad de los datos** (Data Safety) — usar la tabla detallada al final de este doc
- **Política de privacidad URL**: `https://share-agora.app/privacy`

### 7. Testers para Prueba interna
Play Console → app → Prueba interna → pestaña **Testers** → crear lista de email Google (mínimo el tuyo).

### 8. Revisar y publicar la versión en Prueba interna
Play Console → app → Prueba interna → pestaña **Versiones** → "Revisar versión" → si todo OK → "Iniciar lanzamiento en prueba interna".

Play tarda **horas o 1-2 días** en aprobar la primera versión (luego es minutos). Cuando esté listo, el link de testers te lo dará Play Console.

### 9. Tras instalar la app desde Play y verificar
- Push notifications llegan ✅
- Deep link `https://share-agora.app/a/<code>` abre directo (App Links con assetlinks.json actualizado)
- Eliminar cuenta funciona end-to-end
- Reportar y bloquear desde el chat

### 10. Si todo correcto en Prueba interna → promocionar a Producción
Play Console → app → Producción → Crear versión → Promocionar desde Prueba interna.
Esto manda a revisión humana (días a una semana la primera vez).

---

## Apéndice A — Descripción completa de la app

```
Agora es la forma más sencilla de organizar la actividad de tu comunidad:
clubes deportivos, grupos de amigos, peñas, asociaciones, equipos.

Crea una comunidad, invita a tus miembros y empieza a publicar
actividades con plazas. Cada actividad puede tener aforo libre,
plazas limitadas, o plazas por posiciones (ideal para deportes
como voleibol, fútbol sala o pádel).

Funciones principales:

• Comunidades públicas o privadas, con código de invitación o
  por descubrimiento en la pantalla "Explorar".
• Subcomunidades para organizar grupos dentro de grupos.
• Actividades con fecha, lugar y aforo. Los miembros se apuntan
  con un toque y reciben recordatorios automáticos.
• Lista de suplentes con promoción automática: si alguien deja
  el hueco, el siguiente entra y le llega un aviso.
• Chat por comunidad para coordinarse.
• Comparte una actividad por link y deja que invitados externos
  pidan plaza sin instalar la app — útil para abrir tu club a
  conocidos sin fricción.
• Notificaciones push para que nadie se quede fuera.
• Modo oscuro y soporte multiidioma (español e inglés).

Sin anuncios, sin trackers de publicidad, sin venta de datos.

Privacidad: https://share-agora.app/privacy
Términos: https://share-agora.app/terms

¿Sugerencias o problemas? mnebotchirivella@gmail.com
```

---

## Apéndice B — Data Safety form

**Datos recopilados**: marca **Sí** y selecciona:

| Categoría | Tipo | Recopilado | Compartido | Obligatorio | Por qué |
|---|---|---|---|---|---|
| Datos personales | Email | Sí | No | Sí | Autenticación de cuenta |
| Datos personales | Nombre (display name) | Sí | No | Sí | Identificación en comunidad |
| Fotos y vídeos | Avatar (URL) | Sí | No | No | Personalización |
| Mensajes | Mensajes en chat | Sí | No | No | Funcionalidad principal |
| Información de la app y rendimiento | Tokens de FCM | Sí | No | Sí | Notificaciones push |
| Identificadores | ID de usuario (UUID interno) | Sí | No | Sí | Funcionalidad principal |

- **¿Datos cifrados en tránsito?** Sí
- **¿Usuarios pueden solicitar eliminación?** Sí → URL: `https://share-agora.app/privacy` (sección 5)
- **¿Hay revisión externa de seguridad?** No

---

## Notas y warnings

- **Notas de versión** (`Detalles de la versión` en el formulario de Prueba interna):
  ```
  Primera versión:
  • Crea o únete a comunidades (públicas o privadas)
  • Organiza actividades con aforo o por posiciones
  • Lista de suplentes con promoción automática
  • Chat por comunidad
  • Comparte actividades por link e invita sin app
  • Modo oscuro y multiidioma
  ```
- **Política de privacidad URL** y todas las URLs de Play Console deben ser exactamente `https://share-agora.app/privacy` (con `s` final).
- El SHA del **upload key** (`FC:40:B5:75:F6:AC:89:4A:E2:4D:34:F4:21:30:81:F7:48:B6:E2:7C:C7:B2:9B:F3:FC:C8:5D:72:78:D9:6B:06`) NO es el que va en `assetlinks.json` — ahí va el del **Play app signing key**, que se obtiene en Play Console tras Play App Signing.
- Si Play rechaza el .aab por algún motivo, recompila con `.\gradlew.bat :composeApp:bundleRelease` y sube de nuevo. La build cachea agresivamente, las recompilaciones suelen tardar < 1 min.
