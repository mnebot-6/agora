# Agora

App multiplataforma (Kotlin Multiplatform + Compose Multiplatform) para **organizar comunidades y sus actividades**: quién juega, cuándo, dónde y en qué plaza.

> **En uso real.** Agora organiza hoy una comunidad de vóleibol: creación de pachangas, visibilidad de los grupos y calendario compartido. No es un proyecto de demo — hay gente apuntándose a partidos con él cada semana.

<!-- CAPTURAS: insertar aquí (Explorar · Detalle de actividad con plazas · Chat · Notificaciones) -->

---

## Concepto

Organizar un partido recurrente acaba siempre en el mismo caos: un grupo de WhatsApp con 40 personas, mensajes de "yo me apunto" que se pierden, nadie sabe si hay sitio y el que se cae no avisa a tiempo.

Agora convierte eso en estructura. Una **comunidad** agrupa a la gente, cada **actividad** publica sus **plazas**, y apuntarse es un toque. El estado siempre es visible: cuántas quedan, quién está dentro, quién espera.

El caso que lo empuja es el deporte de equipo con posiciones — vóleibol, fútbol sala, pádel — donde no basta con contar cabezas: importa **qué** plaza ocupa cada uno.

---

## Cómo funciona

**Creas una comunidad** (pública o privada). Las privadas se entran por código de invitación; las públicas también se descubren desde la pantalla *Explorar*, filtrando por etiquetas. Una comunidad puede tener **subcomunidades** para separar grupos dentro del grupo.

**Publicas una actividad** con fecha, duración, lugar, coste y un modo de plazas:

| Modo | Para qué |
|---|---|
| `unlimited` | Aforo libre, solo confirmar asistencia |
| `limited` | N plazas, primero que llega se sienta |
| `limited_with_positions` | Plazas agrupadas y por posición (colocador, líbero, portero…) |

**Los miembros se apuntan** con un toque. Cuando no quedan plazas se entra en la **cola de suplentes**: si alguien libera su sitio, el siguiente entra automáticamente y recibe un aviso. Los administradores pueden además asignar plazas a dedo.

**Se comparte fuera de la app.** Cada actividad genera un enlace `share-agora.app/a/{code}`: quien lo abra sin tener la app instalada puede pedir plaza como **invitado** desde una landing web, eligiendo posición si la actividad lo permite. Un admin aprueba o rechaza, y el invitado recibe el resultado por email.

**Nadie se queda fuera del bucle.** Notificaciones push para actividad nueva, plaza liberada, promoción desde suplentes, recordatorio previo, solicitudes de acceso y cambios o cancelaciones — 16 tipos en total.

---

## Funcionalidades

- **Comunidades**: públicas o privadas, código de invitación, descubrimiento por etiquetas, subcomunidades con breadcrumb, roles admin/usuario, solicitudes de acceso con aprobación
- **Actividades y plazas**: tres modos de aforo, grupos y posiciones, plantillas de plazas reutilizables, marcado de pago, archivado
- **Suplentes**: cola FIFO por actividad (y por posición), promoción automática al liberarse una plaza
- **Enlaces de invitado**: landing web autocontenida, sesión anónima por dispositivo, aprobación por admin, avisos por email vía edge function
- **Chat por comunidad**: mensajería para coordinarse sin salir de la app
- **Notificaciones**: push con FCM (Android) y deep links que abren la pantalla exacta; 16 tipos con enum tolerante a valores desconocidos del servidor
- **Moderación**: reportes, bloqueo y expulsión de miembros, borrado de cuenta end-to-end
- **Multiplataforma real**: Android, iOS y Web (Wasm) desde el mismo código Compose
- **App Links / Universal Links**: `share-agora.app/c/{code}` y `/a/{code}` abren la app si está instalada
- **i18n y tema**: español e inglés con preferencia persistida por usuario, modo claro/oscuro
- **Identidad visual propia**: sistema de diseño de inspiración clásica (frisos, columnas acanaladas, volutas jónicas, tarjetas de mármol, tipografía Cinzel)

---

## Stack técnico

| Capa | Tecnología |
|---|---|
| Multiplataforma | Kotlin Multiplatform 2.1.10 (Android · iOS · Wasm/JS) |
| UI | Compose Multiplatform 1.7.3 + Material 3 |
| Navegación | Voyager 1.1.0 (tabs + screen models) |
| Inyección de dependencias | Koin 4.0.2 |
| Backend | Supabase 3.1.1 (Postgres + Auth + Storage) |
| Autenticación | Supabase Auth — email/contraseña para miembros, sesión anónima para invitados |
| Base de datos | PostgreSQL con RLS y funciones RPC (`SECURITY DEFINER`) |
| Cliente HTTP | Ktor 3.1.1 (OkHttp · Darwin · JS) |
| Serialización | kotlinx-serialization 1.7.3 · kotlinx-datetime 0.6.2 |
| Push | Firebase Cloud Messaging + edge function `push-notification` |
| Email | Edge function `notify-guest-email` (Deno + Resend) |
| Imágenes | Coil 3.1.0 |
| Persistencia local | multiplatform-settings 1.2.0 |
| Web | Distribución Wasm servida por Cloudflare Workers en `share-agora.app` |
| CI | GitHub Actions — build del framework iOS en macOS |

**Arquitectura**: multi-módulo Gradle. `core/model` (modelos de dominio), `core/data` (repositorios contra Supabase), `core/domain` (casos de uso), `core/ui` (sistema de diseño), `core/common` (`AppResult`, bus de refresco) y cinco módulos de feature (`auth`, `community`, `activity`, `reservation`, `notification`). La lógica de negocio sensible vive en la base de datos: RPCs con RLS para invitados, promoción de suplentes y asignación de plazas.

---

## Roadmap

| Fase | Estado |
|---|---|
| MVP — comunidades, actividades, plazas, suplentes | Hecho |
| Notificaciones push + deep links | Hecho |
| Comunidades públicas, etiquetas y subcomunidades | Hecho |
| Chat por comunidad | Hecho |
| Enlaces de invitado + landing web + avisos por email | Hecho |
| Moderación, borrado de cuenta y páginas legales | Hecho |
| Target Web (Wasm) desplegado en Cloudflare | Hecho |
| Asignación de plazas por admin | Hecho |
| Google Play — prueba interna y cerrada | En curso |
| Google Play — producción | Pendiente |
| iOS — build en Xcode y App Store | Pendiente |

---

## Documentación

| Archivo | Qué cubre |
|---|---|
| `docs/superpowers/specs/` | Diseños de las features grandes (comunidades públicas, invitados, target web, asignación de plazas) |
| `docs/superpowers/plans/` | Planes de implementación de esas mismas features |
| `docs/play_store_release_pending.md` | Estado de la publicación en Google Play |
| `docs/legal/` | Política de privacidad y términos de servicio |
| `web/README.md` | Landing de invitado, App Links, despliegue del Worker |
| `INVITE_LINKS.md` | Cómo funcionan los enlaces de invitación |

---

## Repositorio

Desarrollado por [@mnebot-6](https://github.com/mnebot-6)
