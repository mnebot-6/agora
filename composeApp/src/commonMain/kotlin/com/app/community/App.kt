package com.app.community

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.app.community.core.domain.auth.GetAuthStateUseCase
import com.app.community.core.ui.theme.AppTheme
import com.app.community.feature.auth.presentation.LoginScreen
import com.app.community.navigation.ActivitiesTab
import com.app.community.navigation.AgoraTab
import com.app.community.navigation.CommunitiesTab
import com.app.community.navigation.NotificationsTab
import com.app.community.navigation.ProfileTab
import org.koin.compose.koinInject

@Composable
fun App() {
    AppTheme {
        val getAuthState = koinInject<GetAuthStateUseCase>()
        val isAuthenticated by getAuthState().collectAsState(initial = false)

        if (isAuthenticated) {
            MainContent()
        } else {
            Navigator(LoginScreen())
        }
    }
}

@Composable
private fun MainContent() {
    TabNavigator(AgoraTab) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    TabNavigationItem(AgoraTab)
                    TabNavigationItem(CommunitiesTab)
                    TabNavigationItem(ActivitiesTab)
                    TabNavigationItem(NotificationsTab)
                    TabNavigationItem(ProfileTab)
                }
            },
        ) { paddingValues ->
            androidx.compose.foundation.layout.Box(Modifier.padding(paddingValues)) {
                CurrentTab()
            }
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current

    NavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
        icon = {
            tab.options.icon?.let { painter ->
                Icon(painter = painter, contentDescription = tab.options.title)
            }
        },
        label = { Text(tab.options.title) },
    )
}
