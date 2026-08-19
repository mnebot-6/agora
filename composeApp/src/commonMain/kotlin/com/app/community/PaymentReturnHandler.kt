package com.app.community

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.app.community.core.common.DeepLinkHandler
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.PaymentRepository
import org.koin.compose.koinInject

/**
 * Vuelta de Stripe Checkout, manejada EN EL ARMAZON de la app y no en la pantalla de la
 * actividad.
 *
 * Estuvo primero en ActivityDetailScreen y era un error: el deep link solo trae el id del
 * pago, asi que solo se consumia si el usuario volvia justo a esa pantalla. Si Android
 * habia matado el proceso mientras pagaba, o si aterrizaba en cualquier otro sitio, nadie
 * lo recogia y la plaza se quedaba en "Reservandose" para siempre pese a estar pagada.
 *
 * Aqui se sincroniza pase lo que pase, se avisa por el bus para que cualquier pantalla
 * abierta se refresque, y se navega a la actividad reutilizando el mismo camino que ya usan
 * las notificaciones.
 */
@Composable
fun PaymentReturnHandler() {
    val paymentRepository = koinInject<PaymentRepository>()
    val pendingPaymentId by DeepLinkHandler.pendingPaymentId.collectAsState()

    LaunchedEffect(pendingPaymentId) {
        val paymentId = DeepLinkHandler.consumePaymentId() ?: return@LaunchedEffect

        paymentRepository.syncPayment(paymentId).onSuccess { result ->
            RefreshBus.emit(RefreshBus.ACTIVITY_DETAIL, RefreshBus.ACTIVITIES)
            // Reutiliza la navegacion de las notificaciones: AppTabs ya sabe abrir el
            // detalle de una actividad a partir de este id.
            result.activityId?.let { DeepLinkHandler.setNotificationActivityId(it) }
        }
    }
}
