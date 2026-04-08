package com.app.community.feature.auth.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.AppResult
import com.app.community.core.domain.auth.SignInUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val status: LoginStatus = LoginStatus.Idle,
)

sealed class LoginStatus {
    data object Idle : LoginStatus()
    data object Loading : LoginStatus()
    data object Success : LoginStatus()
    data class Error(val message: String) : LoginStatus()
}

class LoginScreenModel(
    private val signInUseCase: SignInUseCase,
) : ScreenModel {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, status = LoginStatus.Idle) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, status = LoginStatus.Idle) }
    }

    fun onSignIn() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(status = LoginStatus.Error("Email and password are required")) }
            return
        }

        screenModelScope.launch {
            _uiState.update { it.copy(status = LoginStatus.Loading) }
            val result = signInUseCase(state.email.trim(), state.password)
            when (result) {
                is AppResult.Success -> _uiState.update { it.copy(status = LoginStatus.Success) }
                is AppResult.Error -> _uiState.update {
                    it.copy(status = LoginStatus.Error(result.message))
                }
            }
        }
    }
}
