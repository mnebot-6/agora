package com.app.community.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.app.community.DeepLinkHandler
import com.app.community.feature.activity.presentation.ActivityFeedScreen
import com.app.community.feature.community.presentation.CommunityListScreen
import com.app.community.feature.community.presentation.JoinCommunityScreen
import com.app.community.feature.auth.presentation.ProfileScreen
import com.app.community.feature.notification.presentation.NotificationListScreen
import com.app.community.dashboard.DashboardScreen

object AgoraTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Home)
            return remember { TabOptions(index = 0u, title = "\u00c1gora", icon = icon) }
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
            val icon = rememberVectorPainter(Icons.AutoMirrored.Filled.List)
            return remember { TabOptions(index = 1u, title = "Comunidades", icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(CommunityListScreen()) { navigator ->
            val pendingCode by DeepLinkHandler.pendingInviteCode.collectAsState()
            LaunchedEffect(pendingCode) {
                val code = DeepLinkHandler.consumeInviteCode()
                if (code != null) {
                    navigator.push(JoinCommunityScreen(initialCode = code))
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
            val icon = rememberVectorPainter(Icons.Default.DateRange)
            return remember { TabOptions(index = 2u, title = "Actividades", icon = icon) }
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
            val icon = rememberVectorPainter(Icons.Default.Notifications)
            return remember { TabOptions(index = 3u, title = "Notificaciones", icon = icon) }
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
            val icon = rememberVectorPainter(Icons.Default.Person)
            return remember { TabOptions(index = 4u, title = "Perfil", icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(ProfileScreen())
    }
}
