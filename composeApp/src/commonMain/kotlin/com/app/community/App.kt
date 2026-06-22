package com.app.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Modifier
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.ProfileRepository
import com.app.community.core.ui.locale.AppLanguage
import com.app.community.core.ui.locale.AppLocaleProvider
import com.app.community.core.ui.locale.LanguagePreferenceManager
import com.app.community.core.ui.theme.ThemeManager
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.app.community.core.domain.auth.GetAuthStateUseCase
import com.app.community.core.ui.components.AgoraNavigationBar
import com.app.community.core.ui.components.AgoraNavigationBarItem
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.AppTheme
import com.app.community.feature.activity.presentation.GuestActivityScreen
import com.app.community.feature.auth.presentation.LoginScreen
import com.app.community.navigation.ActivitiesTab
import com.app.community.navigation.AgoraTab
import com.app.community.navigation.CommunitiesTab
import com.app.community.navigation.NotificationsTab
import com.app.community.navigation.ProfileTab
import org.koin.compose.koinInject

@Composable
fun App() {
    val themeManager = koinInject<ThemeManager>()
    val isDarkMode by themeManager.isDarkMode.collectAsState()
    val languageManager = koinInject<LanguagePreferenceManager>()
    val language by languageManager.language.collectAsState()
    val getAuthState = koinInject<GetAuthStateUseCase>()
    val isAuthenticated: Boolean? by remember { getAuthState().map { it as Boolean? } }
        .collectAsState(initial = null)

    // Load theme + language preferences from profile when authenticated
    val authRepository = koinInject<AuthRepository>()
    val profileRepository = koinInject<ProfileRepository>()
    val isGuest by authRepository.isGuestSession.collectAsState(initial = false)
    val guestStore = koinInject<GuestSessionStore>()
    val pendingActivityCode by DeepLinkHandler.pendingActivityCode.collectAsState()
    LaunchedEffect(isAuthenticated, isGuest) {
        if (isAuthenticated == true && !isGuest) {
            val userId = authRepository.currentUserId() ?: return@LaunchedEffect
            profileRepository.getProfile(userId).onSuccess { profile ->
                themeManager.setDarkMode(profile.darkMode == true)
                val lang = when (profile.languagePreference) {
                    "es" -> AppLanguage.ES
                    "en" -> AppLanguage.EN
                    else -> AppLanguage.AUTO
                }
                languageManager.setLanguage(lang)
            }
            fetchPushToken()?.let { token ->
                profileRepository.updateFcmToken(userId, token)
            }
        }
    }

    AppLocaleProvider(locale = language.toLocaleCode()) {
        AppTheme(darkTheme = isDarkMode) {
            // key(isDarkMode) fuerza la recomposición completa del subárbol cuando
            // cambia el tema, garantizando que la pantalla actual se re-pinte sin
            // tener que navegar fuera y volver. Workaround robusto frente a stale
            // colors capturados en remember{} o cache de Voyager Tab.
            key(isDarkMode) {
                // Status bar matches TopBar's primary color
                StatusBarEffect(
                    statusBarColor = MaterialTheme.colorScheme.primary,
                    darkIcons = isDarkMode, // dark mode primary is light → dark icons
                )
                when {
                    // Supabase todavía restaurando sesión desde storage.
                    isAuthenticated == null -> LoadingScreen()
                    // Sesión de invitado anónimo: UI confinada a la actividad.
                    isAuthenticated == true && isGuest -> {
                        LaunchedEffect(pendingActivityCode) {
                            if (pendingActivityCode != null) {
                                guestStore.setActivityCode(pendingActivityCode)
                                DeepLinkHandler.consumeActivityCode()
                            }
                        }
                        val guestCode = guestStore.activityCode()
                        if (guestCode != null) {
                            key(guestCode) { Navigator(GuestActivityScreen(guestCode)) }
                        } else {
                            // Sesión anónima sin actividad objetivo → cerrar sesión.
                            LaunchedEffect(Unit) { authRepository.signOut() }
                            LoadingScreen()
                        }
                    }
                    // Usuario real: UI de miembro. El deep link de actividad se
                    // resuelve en ActivitiesTab (miembro → detalle; no miembro →
                    // flujo de invitado con su identidad real).
                    isAuthenticated == true -> {
                        LaunchedEffect(Unit) { guestStore.setActivityCode(null) }
                        MainContent()
                    }
                    // No autenticado: si llega un link de actividad, entrar como
                    // invitado anónimo; si no, login.
                    pendingActivityCode != null -> {
                        LaunchedEffect(pendingActivityCode) {
                            guestStore.setActivityCode(pendingActivityCode)
                            DeepLinkHandler.consumeActivityCode()
                            authRepository.signInAnonymously()
                        }
                        LoadingScreen()
                    }
                    else -> {
                        LaunchedEffect(Unit) { guestStore.setActivityCode(null) }
                        Navigator(LoginScreen())
                    }
                }
            }
        }
    }
}

@Composable
private fun MainContent() {
    TabNavigator(AgoraTab) {
        DeepLinkTabSwitcher()
        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                AgoraNavigationBar {
                    TabNavigationItem(AgoraTab)
                    TabNavigationItem(CommunitiesTab)
                    TabNavigationItem(ActivitiesTab)
                    TabNavigationItem(NotificationsTab)
                    TabNavigationItem(ProfileTab)
                }
            },
        ) { paddingValues ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                CurrentTab()
            }
        }
    }
}

/**
 * Si llega un deep link de invitación mientras estamos en otro tab, saltamos
 * automáticamente a Comunidades para que el AutoJoinByInviteScreen se monte.
 */
@Composable
private fun DeepLinkTabSwitcher() {
    val tabNavigator = LocalTabNavigator.current
    val pendingInviteCode by DeepLinkHandler.pendingInviteCode.collectAsState()
    val pendingActivityCode by DeepLinkHandler.pendingActivityCode.collectAsState()
    val pendingNotificationActivityId by DeepLinkHandler.pendingNotificationActivityId.collectAsState()
    LaunchedEffect(pendingInviteCode) {
        if (pendingInviteCode != null && tabNavigator.current != CommunitiesTab) {
            tabNavigator.current = CommunitiesTab
        }
    }
    LaunchedEffect(pendingActivityCode) {
        if (pendingActivityCode != null && tabNavigator.current != ActivitiesTab) {
            tabNavigator.current = ActivitiesTab
        }
    }
    LaunchedEffect(pendingNotificationActivityId) {
        if (pendingNotificationActivityId != null && tabNavigator.current != ActivitiesTab) {
            tabNavigator.current = ActivitiesTab
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current

    AgoraNavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
        icon = {
            tab.options.icon?.let { painter ->
                Icon(painter = painter, contentDescription = tab.options.title)
            }
        },
    )
}
