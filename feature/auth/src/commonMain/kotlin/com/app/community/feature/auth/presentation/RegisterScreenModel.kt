package com.app.community.feature.auth.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.AppResult
import com.app.community.core.domain.auth.SignUpUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val status: RegisterStatus = RegisterStatus.Idle,
)

sealed class RegisterStatus {
    data object Idle : RegisterStatus()
    data object Loading : RegisterStatus()
    data object Success : RegisterStatus()
    data class Error(val message: String) : RegisterStatus()
}

class RegisterScreenModel(
    private val signUpUseCase: SignUpUseCase,
) : ScreenModel {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onDisplayNameChange(displayName: String) {
        _uiState.update { it.copy(displayName = displayName, status = RegisterStatus.Idle) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, status = RegisterStatus.Idle) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, status = RegisterStatus.Idle) }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, status = RegisterStatus.Idle) }
    }

    fun onRegister() {
        val state = _uiState.value

        if (state.displayName.isBlank() || state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(status = RegisterStatus.Error("All fields are required")) }
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(status = RegisterStatus.Error("Passwords do not match")) }
            return
        }

        if (state.password.length < 6) {
            _uiState.update {
                it.copy(status = RegisterStatus.Error("Password must be at least 6 characters"))
            }
            return
        }

        screenModelScope.launch {
            _uiState.update { it.copy(status = RegisterStatus.Loading) }
            val result = signUpUseCase(
                email = state.email.trim(),
                password = state.password,
                displayName = state.displayName.trim(),
            )
            when (result) {
                is AppResult.Success -> _uiState.update { it.copy(status = RegisterStatus.Success) }
                is AppResult.Error -> _uiState.update {
                    it.copy(status = RegisterStatus.Error(result.message))
                }
            }
        }
    }
}
