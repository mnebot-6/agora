package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.ConnectStatus
import com.app.community.core.data.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityPaymentsUiState(
    val isLoading: Boolean = true,
    /** true mientras se pide el enlace de alta a Stripe. */
    val isStartingOnboarding: Boolean = false,
    val status: ConnectStatus? = null,
    val errorMessage: String? = null,
    /** URL de alta pendiente de abrir en el navegador. Se consume una sola vez. */
    val onboardingUrl: String? = null,
)

class CommunityPaymentsScreenModel(
    private val communityId: String,
    private val paymentRepository: PaymentRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(CommunityPaymentsUiState())
    val state: StateFlow<CommunityPaymentsUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        screenModelScope.launch {
            paymentRepository.connectStatus(communityId)
                .onSuccess { status -> _state.update { it.copy(isLoading = false, status = status) } }
                .onError { msg, _ -> _state.update { it.copy(isLoading = false, errorMessage = msg) } }
        }
    }

    /**
     * Pide el enlace de alta. Sirve igual para empezar de cero y para continuar un alta a
     * medias: Stripe reanuda donde se quedo, asi que no hace falta distinguir los dos casos.
     */
    fun startOnboarding() {
        if (_state.value.isStartingOnboarding) return
        _state.update { it.copy(isStartingOnboarding = true, errorMessage = null) }
        screenModelScope.launch {
            paymentRepository.connectOnboard(communityId)
                .onSuccess { link ->
                    _state.update { it.copy(isStartingOnboarding = false, onboardingUrl = link.url) }
                }
                .onError { msg, _ ->
                    _state.update { it.copy(isStartingOnboarding = false, errorMessage = msg) }
                }
        }
    }

    fun consumeOnboardingUrl() = _state.update { it.copy(onboardingUrl = null) }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
