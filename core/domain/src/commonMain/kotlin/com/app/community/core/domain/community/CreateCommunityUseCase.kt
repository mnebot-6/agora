package com.app.community.core.domain.community

import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.model.Community
import com.app.community.core.model.CommunityVisibility

class CreateCommunityUseCase(
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        name: String,
        description: String?,
        visibility: CommunityVisibility = CommunityVisibility.PRIVATE,
        tagIds: List<String> = emptyList(),
        parentId: String? = null,
    ): AppResult<Community> {
        val userId = authRepository.currentUserId()
            ?: return AppResult.Error("Not authenticated")
        return communityRepository.createCommunity(name, description, userId, visibility, tagIds, parentId)
    }
}
