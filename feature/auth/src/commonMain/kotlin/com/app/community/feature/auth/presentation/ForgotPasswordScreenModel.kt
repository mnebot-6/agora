package com.app.community.feature.auth.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val status: ForgotPasswordStatus = ForgotPasswordStatus.Idle,
)

sealed class ForgotPasswordStatus {
    data object Idle : ForgotPasswordStatus()
    data object Loading : ForgotPasswordStatus()
    data object Success : ForgotPasswordStatus()
    data class Error(val message: String) : ForgotPasswordStatus()
}

class ForgotPasswordScreenModel(
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, status = ForgotPasswordStatus.Idle) }
    }

    fun onSendResetLink() {
        val state = _uiState.value

        if (state.email.isBlank()) {
            _uiState.update { it.copy(status = ForgotPasswordStatus.Error("Email is required")) }
            return
        }

        screenModelScope.launch {
            _uiState.update { it.copy(status = ForgotPasswordStatus.Loading) }
            val result = authRepository.resetPassword(state.email.trim())
            when (result) {
                is AppResult.Success -> _uiState.update {
                    it.copy(status = ForgotPasswordStatus.Success)
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(status = ForgotPasswordStatus.Error(result.message))
                }
            }
        }
    }
}
