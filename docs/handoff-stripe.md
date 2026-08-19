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

### Ya respondidas — no las vuelvas a preguntar

El usuario respondió a estas el 2026-08-19. Son decisiones tomadas; si crees que
alguna es un error, dilo con tu razonamiento, pero no las replantees de cero.

**Modelo: Stripe Connect.** El administrador de la comunidad cobra a sus
miembros **a través de Agora**, y Agora se queda **un pequeño porcentaje para
cubrir gastos**. La opción de que Agora cobrase una suscripción a las comunidades
queda descartada por ahora.

**Dos vías de cobro que conviven, según quién ocupe la plaza:**

| Quién | Cómo paga |
|---|---|
| Usuario con cuenta | Stripe Connect |
| Invitado sin cuenta (flujo web anónimo) | El admin marca "pagado" a mano, como hoy |
| Alguien apuntado por un admin | El admin marca "pagado" a mano, como hoy |

O sea: el check manual de pagado **no desaparece**, convive con Stripe. El modelo
de datos tiene que registrar por qué vía se pagó cada plaza.

**Liberar una plaza: no se devuelve el dinero hasta que hay sustituto.** Esta es
la regla de negocio, y es firme.

**El mecanismo elegido para cumplirla: reembolso al anterior ocupante disparado
por el pago del sustituto.** No que el sustituto pague directamente al anterior.

Ese matiz importa y ya se discutió, así que no lo reabras sin motivo nuevo. El
usuario lo planteó primero como "el sustituto le paga al de la plaza anterior", y
se cambió porque el resultado económico es idéntico para los tres implicados:

| | Sustituto paga al anterior | Reembolso al anterior |
|---|---|---|
| A reserva | paga 6,50 al admin | paga 6,50 al admin |
| B sustituye | paga 6,50 **a A** | paga 6,50 al admin, y se reembolsa a A |
| Admin acaba con | 6,50 | 6,50 |
| A acaba con | 0 neto | 0 neto |
| B acaba con | −6,50 | −6,50 |

La diferencia está en lo que cuesta montarlo. Que B pague a A convierte a **cada
usuario** en receptor de fondos: cuenta conectada y KYC para cualquiera que
alguna vez libere una plaza pagada, y Agora moviendo dinero entre particulares,
que regulatoriamente es mucho más pesado. Con el reembolso, solo los
administradores necesitan cuenta conectada.

El coste de la alternativa: Stripe no devuelve su comisión en los reembolsos, así
que cada sustitución paga una comisión de más. Asumido conscientemente.

**Consecuencia de diseño:** el reembolso a A se dispara con el **pago
confirmado** de B, no con la liberación de la plaza. Si B no llega a pagar, A
sigue sin cobrar y la plaza sigue siendo suya. Piensa bien el estado intermedio.

### Pendientes de responder

### A. El modelo de negocio

1. **¿Se cobra por actividad, o por periodo** (cuota mensual del club), o ambas?

### B. El momento y las consecuencias del cobro

2. **¿Cuándo se cobra?** ¿Al reservar la plaza, o se reserva primero y se paga
   después? Hoy la reserva es inmediata y el pago es un apretón de manos.

3. **La cola de suplentes**: cuando un suplente entra a una plaza liberada, ¿se
   le cobra automáticamente, o tiene que confirmar? ¿Qué pasa si su pago falla —
   se le devuelve la plaza a la cola, y cuánto tiempo se le da?

4. **Si se cancela la actividad entera**, ¿se reembolsa a todos automáticamente?
   Ojo: eso incluye a los que pagaron por la vía manual, a los que Agora no les
   puede devolver nada.

5. **¿Quién puede iniciar un reembolso** fuera del flujo de sustitución: solo el
   admin, o también el usuario?

### C. Convivencia con lo que ya existe

6. **¿Qué pasa con `cost_description`,** el texto libre actual? Con Stripe hace
   falta un importe estructurado y una moneda. ¿El texto se conserva como nota
   añadida, o se sustituye del todo?

7. **Las actividades que ya existen** en producción tienen `cost_description` en
   texto y plazas marcadas a mano. ¿Se quedan como están, o hay que convertirlas?

### D. Dinero, cifras y papeles

8. **¿Cuánto es "un pequeño porcentaje"?** Hace falta el número, y decidir si es
   porcentaje puro o porcentaje más fijo. En Stripe Connect esto se implementa
   como `application_fee_amount`.

9. **¿Quién asume la comisión de Stripe** — el que paga, o el que cobra?

10. **¿Moneda?** ¿Solo euros?

11. **¿Hacen falta recibos o facturas?** ¿Con qué datos fiscales?

12. **¿Existe una entidad legal detrás de Agora?** Esta ya no es opcional: con el
    modelo elegido, Agora cobra en nombre de terceros y se queda una comisión.
    Eso exige una cuenta de plataforma de Stripe a nombre de alguien, con sus
    obligaciones fiscales, y condiciona el tipo de cuenta conectada que se puede
    usar para los administradores (Express, Standard o Custom).

13. **¿Qué pasa si un administrador no completa su verificación (KYC)?** Puede
    tardar días o no pasarla. ¿La comunidad simplemente no puede cobrar por
    Stripe y se queda con el check manual?

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
