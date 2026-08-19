# Handoff: incorporar Stripe a Agora

> **Cómo usar este documento.** Pégalo como primer mensaje de una sesión nueva.
> El trabajo tiene tres fases y **no se salta ninguna**: primero preguntas,
> después plan, después código. No escribas ni una línea de implementación hasta
> que el usuario haya respondido a las preguntas de la fase 1 y aprobado el plan
> de la fase 2.

---

## Lo que hay que hacer

Incorporar pagos con Stripe a Agora. El usuario lo considera prioritario.

**Fase 1 — Preguntar.** Hacerle al usuario todas las preguntas necesarias para
definir la lógica de negocio. Están agrupadas más abajo; no son exhaustivas, y
la primera condiciona a casi todas las demás. Pregunta de forma agrupada y
digerible, no las 14 de golpe.

**Fase 2 — Diseñar y planificar.** Con las respuestas, escribir el diseño y el
plan de implementación.

**Fase 3 — Implementar.** Solo después de que el usuario apruebe el plan.

---

## El contexto que ahorra media sesión

### Qué es Agora

App de gestión de comunidades (Kotlin Multiplatform, Compose Multiplatform,
Voyager, Koin, Supabase), en producción. Se publica en **Android** y en **web vía
wasmJs** (`share-agora.app/app`, servida desde Cloudflare Workers). No hay build
nativa de iOS: iOS se atiende con la web.

El caso de uso real que la motiva es una comunidad de voleibol: se crean
actividades (entrenamientos, partidos), la gente reserva plaza, y hay cola de
suplentes. Las plazas pueden ser libres, limitadas, o por posiciones dentro de
equipos.

### Cómo se cobra HOY — léelo, no es lo que parece

**No hay sistema de pagos.** Concretamente:

- `activities.cost_description` es una columna **de texto libre**. El placeholder
  de la app es literalmente `"Ej: Bizum de 6.5 euros por persona"`. No hay
  columna de importe, ni de moneda, ni de estado de cobro.
- `slots.status` es un enum que incluye `PAID`. Un administrador lo marca **a
  mano, plaza por plaza**, desde el detalle de la actividad. Eso es todo el
  sistema.
- No existe tabla de pagos, ni registro de quién pagó qué, ni cuándo, ni
  reembolsos.

**Implicación:** Stripe no sustituye un sistema existente, **introduce el
primero**. No hay datos que migrar, pero sí hay que decidir desde cero un modelo
de negocio que hoy vive en la cabeza del administrador y en un Bizum.

### Restricciones técnicas que conviene tener sobre la mesa desde el principio

Estas no son preguntas para el usuario, son cosas que el implementador debe
verificar y explicarle si condicionan la decisión:

1. **Política de Google Play.** Vender bienes o servicios del mundo físico (una
   plaza en un entrenamiento) se puede cobrar con pasarela externa; los bienes
   digitales obligan a Play Billing. Las actividades de Agora caen del lado
   físico, pero **verifica la política vigente** antes de dar por hecho nada: la
   app está publicada y una infracción tumba la ficha.

2. **La clave secreta de Stripe no puede vivir en el cliente.** Hace falta
   servidor. Las opciones naturales en este stack son las **Edge Functions de
   Supabase** o el **Cloudflare Worker** que ya sirve `share-agora.app`. Los
   webhooks de Stripe necesitan un endpoint público y verificación de firma.

3. **No hay SDK de Stripe para Compose Multiplatform.** El SDK nativo es de
   Android, y wasmJs no tiene ninguno. La vía que funciona igual en los dos
   targets es **Stripe Checkout** (o Payment Links) abierto en navegador, con
   vuelta a la app por deep link. Agora ya tiene infraestructura de deep links
   (`DeepLinkHandler`, App Links verificados sobre `share-agora.app`), así que el
   retorno es viable.

4. **Nunca tocar datos de tarjeta en la app.** Checkout aloja el formulario en
   Stripe y evita el alcance de PCI.

5. **El flujo de invitado es anónimo y web.** Alguien sin cuenta abre
   `share-agora.app/a/{code}`, se le crea una sesión anónima de Supabase y pide
   plaza con nombre y email. Si los invitados también pagan, el cobro tiene que
   funcionar sin cuenta real.

### Cómo se trabaja en este repo

- **Migraciones**: Supabase CLI. `supabase migration new <nombre>` y
  `supabase db push`. El baseline (`20260625120019_baseline.sql`) **no se edita
  nunca**; las funciones se redefinen con `CREATE OR REPLACE` en la migración
  nueva, copiando el cuerpo real del baseline y cambiando solo lo necesario.
  Cuidado: si cambias la firma, `CREATE OR REPLACE` crea una función nueva en vez
  de reemplazar.
- **Compilación**: `./gradlew :composeApp:compileDebugKotlinAndroid` y
  `:composeApp:compileKotlinWasmJs`. Ojo, es `compileDebugKotlinAndroid`, no
  `compileKotlinAndroid`.
- **Deploy web**: `./gradlew :composeApp:syncWebApp` y luego `wrangler deploy`
  desde `web/`. El build de wasm de producción es lento, del orden de una hora.
- **Tests**: el repo tiene muy pocos y **no hay harness de tests de UI de
  Compose**. Para lógica pura, `core/model` tiene `commonTest` configurado y es
  el sitio donde escribir tests de verdad. Para pagos, la lógica de importes,
  estados y reembolsos **sí debería ser pura y testeada**: es dinero.
- **Idioma**: código y cadenas de usuario en español con tildes; los comentarios
  del repo van sin tildes.
- Hay dos documentos de una reforma de UX reciente en `docs/superpowers/` que
  sirven de ejemplo del nivel de detalle que se espera de un spec y de un plan.

---

## Fase 1 — Las preguntas

**La primera lo condiciona todo. No sigas hasta tenerla clara.**

### A. El modelo de negocio

1. **¿Quién cobra a quién?** Hay dos mundos completamente distintos:
   - **(a) El administrador de la comunidad cobra a sus miembros**, y Agora es el
     intermediario. Esto obliga a **Stripe Connect**: cada admin necesita su
     propia cuenta conectada, con verificación de identidad (KYC), sus payouts y
     sus responsabilidades fiscales. Es bastante más trabajo y arrastra
     implicaciones legales para quien opera Agora.
   - **(b) Agora cobra a las comunidades** una suscripción, y los pagos entre
     admin y miembros siguen fuera de la app. Mucho más simple.
   - **(c) Alguna combinación.**

2. Si es (a): **¿Agora se queda comisión** de cada cobro, o solo intermedia?

3. **¿Se cobra por actividad, o por periodo** (cuota mensual del club), o ambas?

### B. El momento y las consecuencias del cobro

4. **¿Cuándo se cobra?** ¿Al reservar la plaza, o se reserva primero y se paga
   después? Hoy la reserva es inmediata y el pago es un apretón de manos.

5. **Si alguien libera su plaza, ¿qué pasa con su dinero?** ¿Reembolso
   automático, parcial según antelación, o nada?

6. **La cola de suplentes**: cuando un suplente entra a una plaza liberada,
   ¿se le cobra automáticamente? ¿Qué pasa si su pago falla?

7. **Si se cancela la actividad entera**, ¿se reembolsa a todos automáticamente?

8. **¿Quién puede iniciar un reembolso** desde la app: solo el admin, o también
   el usuario?

### C. Convivencia con lo que ya existe

9. **¿Stripe sustituye al Bizum o convive con él?** Concretamente: ¿el admin
   debe poder seguir marcando una plaza como pagada a mano, para quien pague en
   efectivo o por transferencia?

10. **¿Qué pasa con `cost_description`,** el texto libre actual? ¿Se conserva
    como nota, o se sustituye por un importe estructurado?

11. **Los invitados sin cuenta** (flujo web anónimo): ¿pagan también? Si sí, su
    plaza pendiente de aprobación y el cobro tienen que coordinarse de alguna
    manera: ¿se cobra antes de aprobar, o después?

### D. Dinero, cifras y papeles

12. **¿Moneda?** ¿Solo euros?

13. **¿Quién asume la comisión de Stripe** — el que paga, o el que cobra?

14. **¿Hacen falta recibos o facturas?** ¿Con qué datos fiscales?

15. **¿Existe una entidad legal detrás de Agora?** Si la app va a intermediar
    dinero entre terceros, esto deja de ser una pregunta técnica. Si no la hay,
    la opción (b) de la pregunta 1 puede ser la única viable a corto plazo.

---

## Fase 2 — El plan

Cuando tengas las respuestas:

1. Escribe el diseño en `docs/superpowers/specs/YYYY-MM-DD-stripe-design.md` y
   commitéalo.
2. Que el usuario lo revise.
3. Escribe el plan por fases en `docs/superpowers/plans/YYYY-MM-DD-stripe.md`.

El plan debe tratar el dinero con más cuidado que el resto del código de la app:
idempotencia de los webhooks, qué pasa si un pago llega dos veces, qué pasa si el
webhook no llega nunca, y qué estado ve el usuario mientras tanto. Esa es la
parte que hay que pensar despacio, no la integración del SDK.

## Fase 3 — Implementar

Solo con el plan aprobado.
