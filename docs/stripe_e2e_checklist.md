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

## Comunidad sin KYC

- [ ] Con `stripe_charges_enabled = false`, reservar una actividad de pago es
      instantaneo y sin error, y el admin puede marcar "pagado" a mano.

## Ojo

En **web** el cobro NO funciona todavia: la PWA publicada es anterior a los
pagos. Hace falta `./gradlew :composeApp:syncWebApp` (cerca de una hora) y
`npx wrangler deploy` desde `web/`. En Android si funciona.
