package com.app.community

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import com.app.community.di.appModules
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* System remembers the user's choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val settings = SharedPreferencesSettings(
            getSharedPreferences("agora_prefs", Context.MODE_PRIVATE),
        )
        startKoin {
            modules(appModules(settings))
        }

        requestNotificationPermission()
        handleDeepLink(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val segments = data.pathSegments
        when {
            // agora://invite/{code}
            data.scheme == "agora" && data.host == "invite" -> {
                data.pathSegments?.firstOrNull()?.let { DeepLinkHandler.setInviteCode(it) }
            }
            // agora://activity/{code}
            data.scheme == "agora" && data.host == "activity" -> {
                data.pathSegments?.firstOrNull()?.let { DeepLinkHandler.setActivityCode(it) }
            }
            data.scheme == "https" && data.host == "share-agora.app" && segments != null && segments.size >= 2 -> {
                when (segments[0]) {
                    // https://share-agora.app/c/{code}
                    "c" -> DeepLinkHandler.setInviteCode(segments[1])
                    // https://share-agora.app/a/{code}
                    "a" -> DeepLinkHandler.setActivityCode(segments[1])
                }
            }
        }
    }
}
