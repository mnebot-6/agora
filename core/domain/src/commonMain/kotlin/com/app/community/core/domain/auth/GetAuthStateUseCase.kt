package com.app.community.core.domain.auth

import com.app.community.core.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class GetAuthStateUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Flow<Boolean> = authRepository.isAuthenticated
}
