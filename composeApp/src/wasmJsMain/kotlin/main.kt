import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.app.community.App
import com.app.community.DeepLinkHandler
import com.app.community.WebDeepLink
import com.app.community.di.appModules
import com.app.community.core.ui.theme.preloadAgoraFonts
import com.app.community.parseWebDeepLink
import com.russhwolf.settings.StorageSettings
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.configureWebResources
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    // En producción la app vive bajo /app/; en el dev server local, en la raíz.
    val basePath = if (window.location.pathname.startsWith("/app")) "/app/" else "/"
    configureWebResources {
        resourcePathMapping { path -> "$basePath$path" }
    }

    // StorageSettings = localStorage. Mismo rol que SharedPreferences en Android.
    startKoin {
        modules(appModules(StorageSettings()))
    }

    // Deep links web: /app/?pay={paymentId} | /app/?c={inviteCode} | /app/?a={activityCode}
    when (val link = parseWebDeepLink(window.location.search)) {
        is WebDeepLink.Invite -> DeepLinkHandler.setInviteCode(link.code)
        is WebDeepLink.Activity -> DeepLinkHandler.setActivityCode(link.code)
        is WebDeepLink.Payment -> DeepLinkHandler.setPaymentId(link.paymentId)
        is WebDeepLink.ConnectReturn -> DeepLinkHandler.setConnectCommunityId(link.communityId)
        null -> Unit
    }

    // Cinzel se precarga ANTES de montar la UI. Si se dejara a la carga asíncrona
    // de compose-resources, el primer render usaría su fuente vacía de relleno
    // (sin glifos → texto en cuadraditos) y esa carga, al vivir en una corrutina
    // atada a la composición, puede cancelarse y no completarse nunca.
    // La pantalla de carga del index.html cubre esta espera.
    MainScope().launch {
        preloadAgoraFonts()

        ComposeViewport(document.body!!) {
            LaunchedEffect(Unit) {
                // Compose ya montó: retirar la pantalla de carga del index.html.
                document.getElementById("agora-loading")?.remove()
            }
            App()
        }
    }
}
