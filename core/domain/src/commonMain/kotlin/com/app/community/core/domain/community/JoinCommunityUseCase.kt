package com.app.community.core.domain.community

import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository

class JoinCommunityUseCase(
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(inviteCode: String): AppResult<CommunityRepository.JoinByInviteResult> {
        authRepository.currentUserId()
            ?: return AppResult.Error("Not authenticated")
        // Server-side RPC uses auth.uid() from JWT — no userId needed
        return communityRepository.joinByInviteCodeV2(inviteCode)
    }
}
