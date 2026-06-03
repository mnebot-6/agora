package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.GuestRepository
import com.app.community.core.model.GuestActivityPreview
import com.app.community.core.model.GuestRequestStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Flujo de invitado a una actividad. Sirve tanto a una sesión anónima (web→app
 * o usuario sin login) como a un usuario real logueado que NO es miembro de la
 * comunidad. Si el caller ya es miembro, navega directo al detalle.
 */
class GuestActivityScreenModel(
    private val code: String,
    private val guestRepository: GuestRepository,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        data object NotFound : UiState()
        data class NavigateToActivity(val activityId: String) : UiState()
        data class Form(
            val preview: GuestActivityPreview,
            val submitting: Boolean = false,
            val error: String? = null,
        ) : UiState()
        data class Pending(val activityName: String, val guestName: String) : UiState()
        data class Approved(val activityName: String) : UiState()
        data class Rejected(val activityName: String) : UiState()
        data object Full : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadPreview()
    }

    fun loadPreview() {
        _state.value = UiState.Loading
        screenModelScope.launch {
            guestRepository.getPreview(code)
                .onSuccess { preview ->
                    _state.value = mapPreview(preview)
                }
                .onError { msg, _ ->
                    _state.value = UiState.Error(msg)
                }
        }
    }

    private fun mapPreview(preview: GuestActivityPreview): UiState {
        val activity = preview.activity
        if (preview.status != "ok" || activity == null) {
            return UiState.NotFound
        }
        if (preview.isMember) {
            return UiState.NavigateToActivity(activity.id)
        }
        val req = preview.myRequest
        val activityName = activity.name
        return when (req?.status) {
            GuestRequestStatus.PENDING -> UiState.Pending(activityName, req.guestName)
            GuestRequestStatus.APPROVED -> UiState.Approved(activityName)
            GuestRequestStatus.REJECTED -> UiState.Rejected(activityName)
            else -> UiState.Form(preview)
        }
    }

    fun submit(name: String, phone: String) {
        val current = _state.value as? UiState.Form ?: return
        val activity = current.preview.activity ?: return
        if (name.isBlank() || phone.isBlank()) {
            _state.value = current.copy(error = "validation")
            return
        }
        _state.value = current.copy(submitting = true, error = null)
        screenModelScope.launch {
            guestRepository.requestSlot(code, name.trim(), phone.trim())
                .onSuccess { result ->
                    _state.value = when (result.status) {
                        "pending" -> UiState.Pending(activity.name, name.trim())
                        "approved" -> UiState.Approved(activity.name)
                        "full" -> UiState.Full
                        "already_member" -> UiState.NavigateToActivity(activity.id)
                        else -> UiState.Pending(activity.name, name.trim())
                    }
                }
                .onError { msg, _ ->
                    _state.value = current.copy(submitting = false, error = msg)
                }
        }
    }
}
