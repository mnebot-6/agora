package com.app.community.core.domain.auth

import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.AuthRepository

class SignInUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AppResult<Unit> =
        authRepository.signIn(email, password)
}
