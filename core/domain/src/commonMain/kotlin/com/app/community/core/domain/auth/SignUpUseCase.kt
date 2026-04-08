package com.app.community.core.domain.auth

import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.AuthRepository

class SignUpUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, displayName: String): AppResult<Unit> =
        authRepository.signUp(email, password, displayName)
}
