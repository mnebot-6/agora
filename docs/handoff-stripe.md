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
digerible, no todas de golpe. Parte de las decisiones ya estan tomadas: mira la
seccion "Ya respondidas" antes de preguntar nada.

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

## Fase 1 — Decisiones de negocio: CERRADA

El usuario respondió a todo el 2026-08-19. **No vuelvas a preguntar esto.** Si
crees que alguna decision es un error, dilo con tu razonamiento antes de
implementarla, pero no la replantees de cero.

Queda **una sola pregunta abierta**, al final de esta sección.

### Modelo

**Stripe Connect.** El administrador de la comunidad cobra a sus miembros a
traves de Agora. Se descarta que Agora cobre suscripción a las comunidades.

**Comisión de Agora: configurable, y arranca a 0%.** El razonamiento importa
para que no lo deshagas: no existe entidad legal detrás de Agora, y una comisión
convierte a la persona que tenga la cuenta de plataforma en perceptora de
ingresos, con la obligación fiscal que eso arrastra en España. Quitar la comisión
**no** elimina la necesidad de cuenta de plataforma, solo la de declarar
ingresos. Como en Connect la comisión es un parámetro (`application_fee_amount`),
el trabajo técnico es idéntico con 0% que con 2%. Se implementa configurable y se
lanza a 0; cuando haya volumen que justifique el papeleo, se sube el valor.
El 2% era la cifra que el usuario tenia en mente para cuando llegue ese momento.

**Se cobra por actividad**, con precio por actividad. No hay cuotas por periodo.

**Moneda: solo euros.** Sin facturas ni recibos fiscales.

**La comisión de Stripe la asume el que cobra**, es decir el administrador.

### Cuándo y cómo se cobra

**Reservar implica pagar.** Ya no hay reservas sin cobro. Ojo con la consecuencia
de diseño: hoy "Reservar" es instantáneo y pasa a ser salir a Checkout, pagar,
volver por deep link y confirmar con el webhook. Es la acción más usada de la
app. **En actividades gratuitas tiene que seguir siendo instantánea.**

**Cola de suplentes: ~~al suplente se le cobra directamente~~.** CORREGIDO el
2026-08-19, durante la fase 2: **no se le cobra solo**. Se le ofrece la plaza, le
queda reservada **6 h o hasta que actúe**, y entra, confirma y paga en Checkout
como cualquier otro. Si su pago falla, no reserva nada **y sale de la cola**. El
mecanismo completo esta en el diseño, sección "Cola de suplentes".

### Liberar una plaza

**No se devuelve el dinero hasta que hay sustituto.** Regla firme.

**Mecanismo: reembolso al ocupante anterior, disparado por el pago confirmado del
sustituto.** No que el sustituto pague al anterior.

Ya se discutió y se descartó la otra vía, no la reabras sin motivo nuevo. El
resultado económico es idéntico para los tres implicados:

| | Sustituto paga al anterior | Reembolso al anterior |
|---|---|---|
| A reserva | paga 6,50 al admin | paga 6,50 al admin |
| B sustituye | paga 6,50 **a A** | paga 6,50 al admin, y se reembolsa a A |
| Admin acaba con | 6,50 | 6,50 |
| A acaba con | 0 neto | 0 neto |
| B acaba con | -6,50 | -6,50 |

La diferencia es el coste de montarlo: que B pague a A convierte a **cada
usuario** en receptor de fondos, con cuenta conectada y KYC para cualquiera que
pueda liberar una plaza pagada, y pone a Agora a mover dinero entre particulares.
Con el reembolso, solo los administradores necesitan cuenta conectada. El precio
es que Stripe no devuelve su comisión en los reembolsos, así que cada sustitucion
paga una comisión de más. Asumido conscientemente.

**El estado intermedio es donde se van a concentrar los bugs:** plaza liberada,
todavía pagada, todavía de A, esperando a que alguien pague. Si B no paga nunca,
A no cobra y la plaza sigue siendo suya. Piénsalo antes de escribir código.

**Un usuario no puede pedir un reembolso por su cuenta.** La única vía es que
otra persona ocupe su plaza.

### Cancelar una actividad

**Se devuelve el dinero a todos**, pero la app solo puede hacerlo con los pagos
de Stripe: los invitados y los apuntados por el admin pagaron por Bizum o en
efectivo, y ese dinero nunca pasó por Agora.

Comportamiento decidido: **reembolsar automáticamente los de Stripe y mostrarle
al admin la lista de a quién le debe dinero a mano**, para que lo resuelva por su
cuenta.

### Convivencia con el cobro manual

El check manual de pagado **no desaparece**:

| Quién ocupa la plaza | Como paga |
|---|---|
| Usuario con cuenta | Stripe Connect |
| Invitado sin cuenta (flujo web anónimo) | El admin marca "pagado" a mano |
| Alguien apuntado por un admin | El admin marca "pagado" a mano |

El modelo de datos tiene que registrar **por que vía se pago cada plaza**, porque
de eso depende si se puede reembolsar automáticamente.

### Datos existentes

**`cost_description` (texto libre) se sustituye del todo** por importe
estructurado y moneda.

**Las actividades que ya existen en producción se quedan sin precio** y el admin
lo introduce si quiere cobrar. Nada de parsear el texto viejo: sacar "6,50" de
"Bizum de 6.5 euros por persona" con un parser es una lotería, y equivocarse en
importes es de lo peor que puede hacer una migración. Se puede mostrar el texto
antiguo una vez como ayuda para que el admin lo reescriba.

### La única pregunta que queda

**Qué puede hacer un administrador que todavía no ha completado su verificación
de Stripe (KYC).**

El alta no es instantánea: Stripe pide identidad, cuenta bancaria y NIF, y puede
tardar de minutos a días. Hay gente que se atasca o abandona. Así que existe un
estado real de "esta comunidad todavía no puede cobrar".

Hay que decidir si en ese estado el admin puede crear actividades de pago que
queden bloqueadas hasta completar el alta, o si directamente no puede crearlas
hasta terminar. Pregúntaselo al usuario **con el diseño delante**, porque depende
de como quede la pantalla de crear actividad.

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
