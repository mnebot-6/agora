# Pagos con Stripe — comprobacion manual

Ultima ejecucion automatica: 2026-08-19. Lo de abajo es lo que NO se puede
automatizar desde aqui, porque necesita la app instalada y dos cuentas.

## Antes de empezar

- Instalar: `./gradlew :composeApp:installDebug`
- Tarjetas de prueba de Stripe:
  - `4242 4242 4242 4242` — pago correcto
  - `4000 0000 0000 9995` — fondos insuficientes
  - `4000 0025 0000 3155` — pide autenticacion 3D Secure
- Cualquier fecha futura y cualquier CVC.

## Lo critico

- [ ] **Actividad GRATUITA: reservar sigue siendo instantaneo.** No abre el
      navegador, no toca Stripe. Es la accion mas usada de la app y no puede
      ralentizarse. Si esto falla, para todo.
- [ ] Actividad de pago: reservar abre Checkout y, al volver, la plaza queda
      pagada.
- [ ] Pagar y NO volver a la app: la plaza se confirma sola en menos de un
      minuto (webhook o barrido).
- [ ] Abandonar el Checkout: la plaza se libera a los 30 minutos.
- [ ] Tarjeta rechazada: la plaza se libera y no hay cobro.
- [ ] Dos moviles a la vez sobre la misma plaza: el segundo recibe "alguien esta
      pagando esta plaza ahora mismo".
- [ ] Doble toque en Reservar: NO da error, reabre el mismo Checkout.

## Sustituciones

- [ ] A paga; B se apunta a la cola; A libera.
      La plaza sigue a nombre de A con el chip "busca sustituto".
- [ ] B ve "Tuya hasta las HH:MM" con Confirmar y pagar / Renunciar.
- [ ] Un tercero NO puede ocuparla mientras esta apalabrada para B.
- [ ] B paga: la plaza pasa a B y A recibe su reembolso (visible en el panel de
      la CUENTA CONECTADA, no en el de plataforma).
- [ ] B renuncia: pasa al siguiente de la cola.

## Cancelacion

- [ ] Cancelar (boton Archivar) ensena el desglose ANTES de confirmar.
- [ ] Tras cancelar, la lista de deudas en efectivo sale en su propio dialogo.
- [ ] Los pagos de Stripe aparecen reembolsados en menos de un minuto.

## Bizum

Bizum solo aparece en Checkout si la capability `bizum_payments` esta activa en
la CUENTA CONECTADA del admin, no en la de plataforma. Si no sale el boton, ese
es el sitio donde mirar (Dashboard > Connect > Cuentas conectadas > Metodos de
pago), no el codigo.

- [ ] En Checkout aparece Bizum junto a la tarjeta, en una actividad de pago.
- [ ] Telefono de prueba `+34600000002`: el pago se rechaza y la plaza se libera.
- [ ] Cualquier otro telefono: el pago se confirma y la plaza queda pagada.
- [ ] Pagar con Bizum y NO volver a la app: la plaza se confirma sola. Bizum
      resuelve unos segundos DESPUES de volver, asi que el estado correcto al
      aterrizar puede ser "pendiente" un momento.
- [ ] **Reembolso de un pago Bizum** (libera la plaza y que la pague otro): el
      pago se queda en `refund_pending` hasta 5 minutos y despues pasa a
      `refunded`. Que NO salte a `refunded` al instante es lo esperado; que se
      quede horas en `refund_pending` no.
- [ ] Actividad de mas de 5.000 EUR o de menos de 0,50 EUR: Bizum no aparece
      (limites del metodo). Con tarjeta sigue funcionando.

## Comunidad sin KYC

- [ ] Con `stripe_charges_enabled = false`, reservar una actividad de pago es
      instantaneo y sin error, y el admin puede marcar "pagado" a mano.

## Ojo

En **web** los pagos SI estan publicados. Comprobado el 2026-08-24: los recursos
`payments_*` estan servidos en share-agora.app/app y el bundle desplegado es
identico byte a byte al que produce `syncWebApp`. La nota anterior decia lo
contrario y era falsa.

Para saber si la web esta al dia sin recompilar nada, compara el fichero
desplegado con el local. Si coinciden, no hay nada que desplegar:

```bash
curl -s https://share-agora.app/app/composeApp.js | md5sum && md5sum web/app/composeApp.js
```

NO busques codigos de error como `payments_not_enabled` dentro de los `.wasm`
para averiguar si una feature esta desplegada: esos codigos solo existen en las
Edge Functions, nunca en el cliente Kotlin, y los textos de la UI viven en
`composeResources/*.cvr`, no en el wasm. Buscarlos ahi da un falso negativo que
parece una feature sin desplegar.
