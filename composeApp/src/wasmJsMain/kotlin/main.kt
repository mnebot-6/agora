import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.app.community.App
import com.app.community.DeepLinkHandler
import com.app.community.WebDeepLink
import com.app.community.di.appModules
import com.app.community.parseWebDeepLink
import com.russhwolf.settings.StorageSettings
import kotlinx.browser.document
import kotlinx.browser.window
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

    // Deep links web: /app/?c={inviteCode} | /app/?a={activityCode}
    when (val link = parseWebDeepLink(window.location.search)) {
        is WebDeepLink.Invite -> DeepLinkHandler.setInviteCode(link.code)
        is WebDeepLink.Activity -> DeepLinkHandler.setActivityCode(link.code)
        null -> Unit
    }

    ComposeViewport(document.body!!) {
        LaunchedEffect(Unit) {
            // Compose ya montó: retirar la pantalla de carga del index.html.
            document.getElementById("agora-loading")?.remove()
        }
        App()
    }
}
