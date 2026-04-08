package com.app.community.core.domain.community

import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.model.Community

class GetMyCommunitiesUseCase(
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<List<Community>> {
        val userId = authRepository.currentUserId()
            ?: return AppResult.Error("Not authenticated")
        return communityRepository.getMyCommunities(userId)
    }
}
