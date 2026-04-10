package com.app.community.feature.auth.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.ProfileRepository
import com.app.community.core.model.Profile
import com.app.community.core.ui.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isEditing: Boolean = false,
    val editDisplayName: String = "",
    val isSaving: Boolean = false,
    val actionMessage: String? = null,
    val isDarkMode: Boolean = false,
)

class ProfileScreenModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val themeManager: ThemeManager,
) : ScreenModel {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val userId = authRepository.currentUserId() ?: run {
                _state.value = _state.value.copy(isLoading = false, error = "No autenticado")
                return@launch
            }
            profileRepository.getProfile(userId)
                .onSuccess { profile ->
                    val darkMode = profile.darkMode == true
                    themeManager.setDarkMode(darkMode)
                    _state.value = _state.value.copy(
                        profile = profile,
                        isLoading = false,
                        editDisplayName = profile.displayName,
                        isDarkMode = darkMode,
                    )
                }
                .onError { msg, _ ->
                    _state.value = _state.value.copy(isLoading = false, error = msg)
                }
        }
    }

    fun startEditing() {
        val current = _state.value.profile ?: return
        _state.value = _state.value.copy(
            isEditing = true,
            editDisplayName = current.displayName,
        )
    }

    fun cancelEditing() {
        _state.value = _state.value.copy(isEditing = false)
    }

    fun onDisplayNameChange(value: String) {
        _state.value = _state.value.copy(editDisplayName = value)
    }

    fun saveProfile() {
        val userId = authRepository.currentUserId() ?: return
        val name = _state.value.editDisplayName.trim()
        if (name.isBlank()) return

        screenModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            profileRepository.updateProfile(userId, name)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        isEditing = false,
                        actionMessage = "Perfil actualizado",
                    )
                    load()
                }
                .onError { msg, _ ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        actionMessage = "Error: $msg",
                    )
                }
        }
    }

    fun signOut() {
        screenModelScope.launch {
            authRepository.signOut()
        }
    }

    fun toggleDarkMode() {
        val newValue = !_state.value.isDarkMode
        _state.value = _state.value.copy(isDarkMode = newValue)
        themeManager.setDarkMode(newValue)
        val userId = authRepository.currentUserId() ?: return
        screenModelScope.launch {
            profileRepository.updateDarkMode(userId, newValue)
        }
    }

    fun clearActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }
}
