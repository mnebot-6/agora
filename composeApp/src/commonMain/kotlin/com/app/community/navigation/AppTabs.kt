package com.app.community.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import agora.composeapp.generated.resources.Res
import agora.composeapp.generated.resources.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.jetbrains.compose.resources.stringResource
import com.app.community.DeepLinkHandler
import com.app.community.feature.activity.presentation.ActivityFeedScreen
import com.app.community.feature.community.presentation.AutoJoinByInviteScreen
import com.app.community.feature.community.presentation.CommunityListScreen
import com.app.community.feature.community.presentation.JoinCommunityScreen
import com.app.community.feature.auth.presentation.ProfileScreen
import com.app.community.feature.notification.presentation.NotificationListScreen
import com.app.community.dashboard.DashboardScreen

object AgoraTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.AccountBalance)
            val title = stringResource(Res.string.tab_agora)
            return remember(title) { TabOptions(index = 0u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(DashboardScreen())
    }
}

object CommunitiesTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Groups)
            val title = stringResource(Res.string.tab_communities)
            return remember(title) { TabOptions(index = 1u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(CommunityListScreen()) { navigator ->
            val pendingCode by DeepLinkHandler.pendingInviteCode.collectAsState()
            LaunchedEffect(pendingCode) {
                val code = DeepLinkHandler.consumeInviteCode()
                if (code != null) {
                    // Resuelve y une silenciosamente (o pide confirmación si la
                    // comunidad requiere aprobación). El usuario nunca ve la
                    // pantalla manual de "introducir código".
                    navigator.push(AutoJoinByInviteScreen(inviteCode = code))
                }
            }
            navigator.lastItem.Content()
        }
    }
}

object ActivitiesTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Event)
            val title = stringResource(Res.string.tab_activities)
            return remember(title) { TabOptions(index = 2u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(ActivityFeedScreen())
    }
}

object NotificationsTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Campaign)
            val title = stringResource(Res.string.tab_notifications)
            return remember(title) { TabOptions(index = 3u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(NotificationListScreen())
    }
}

object ProfileTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.AccountCircle)
            val title = stringResource(Res.string.tab_profile)
            return remember(title) { TabOptions(index = 4u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(ProfileScreen())
    }
}
