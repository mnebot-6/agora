package com.app.community

import com.app.community.core.common.DeepLinkHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.app.community.core.ui.theme.AgoraTypography
import com.app.community.core.ui.theme.AppTheme
import com.app.community.feature.activity.presentation.GuestActivityScreen
import com.app.community.feature.auth.presentation.LoginScreen
import com.app.community.navigation.AgoraTab
import com.app.community.navigation.CommunitiesTab
import com.app.community.navigation.NotificationsTab
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

    // La pestaña seleccionada vive por encima de key(locale) y key(isDarkMode):
    // esos key() descartan el subárbol al cambiar tema/idioma y con él el
    // Navigator de Voyager, que volvería siempre a su tab inicial (dashboard).
    // Al elevarla aquí, la selección sobrevive a la recomposición forzada.
    var selectedTab by remember { mutableStateOf<Tab>(AgoraTab) }

    // La tipografía se resuelve AQUÍ, por encima de los key(), por el mismo motivo.
    // En web las fuentes cargan en una corrutina atada al rememberCoroutineScope()
    // del composable que invoca Font(...): si se resolviera dentro de AppTheme, el
    // key() la destruiría a media carga (tarda ~1,4 s) al aplicarse el perfil, la
    // corrutina se cancelaría y la fuente quedaría en la vacía de relleno, pintando
    // todo el texto de Cinzel como cuadraditos. Aquí su scope no muere nunca.
    val typography = AgoraTypography

    AppLocaleProvider(locale = language.toLocaleCode()) {
        AppTheme(darkTheme = isDarkMode, typography = typography) {
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
                    // resuelve en AgoraTab (miembro → detalle; no miembro →
                    // flujo de invitado con su identidad real).
                    isAuthenticated == true -> {
                        LaunchedEffect(Unit) { guestStore.setActivityCode(null) }
                        MainContent(
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it },
                        )
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
private fun MainContent(selectedTab: Tab, onSelectTab: (Tab) -> Unit) {
    // El tab inicial viene del estado elevado en App(), no de una constante: así
    // al reconstruirse el TabNavigator (key(isDarkMode)/key(locale)) se restaura
    // la pestaña en la que estaba el usuario.
    TabNavigator(selectedTab) {
        DeepLinkTabSwitcher(onSelectTab)
        // Vive aqui y no en la pantalla de la actividad: si estuviera alli, un pago
        // solo se confirmaria cuando el usuario aterrizase justo en esa pantalla.
        PaymentReturnHandler()
        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                AgoraNavigationBar {
                    TabNavigationItem(AgoraTab, onSelectTab)
                    TabNavigationItem(CommunitiesTab, onSelectTab)
                    TabNavigationItem(NotificationsTab, onSelectTab)
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
private fun DeepLinkTabSwitcher(onSelectTab: (Tab) -> Unit) {
    val tabNavigator = LocalTabNavigator.current
    val pendingInviteCode by DeepLinkHandler.pendingInviteCode.collectAsState()
    val pendingActivityCode by DeepLinkHandler.pendingActivityCode.collectAsState()
    val pendingNotificationActivityId by DeepLinkHandler.pendingNotificationActivityId.collectAsState()
    val pendingConnectCommunityId by DeepLinkHandler.pendingConnectCommunityId.collectAsState()
    LaunchedEffect(pendingConnectCommunityId) {
        if (pendingConnectCommunityId != null && tabNavigator.current != CommunitiesTab) {
            tabNavigator.current = CommunitiesTab
            onSelectTab(CommunitiesTab)
        }
    }
    LaunchedEffect(pendingInviteCode) {
        if (pendingInviteCode != null && tabNavigator.current != CommunitiesTab) {
            tabNavigator.current = CommunitiesTab
            onSelectTab(CommunitiesTab)
        }
    }
    LaunchedEffect(pendingActivityCode) {
        if (pendingActivityCode != null && tabNavigator.current != AgoraTab) {
            tabNavigator.current = AgoraTab
            onSelectTab(AgoraTab)
        }
    }
    LaunchedEffect(pendingNotificationActivityId) {
        if (pendingNotificationActivityId != null && tabNavigator.current != AgoraTab) {
            tabNavigator.current = AgoraTab
            onSelectTab(AgoraTab)
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab, onSelectTab: (Tab) -> Unit) {
    val tabNavigator = LocalTabNavigator.current

    AgoraNavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = {
            tabNavigator.current = tab
            onSelectTab(tab)
        },
        icon = {
            tab.options.icon?.let { painter ->
                Icon(painter = painter, contentDescription = tab.options.title)
            }
        },
    )
}
