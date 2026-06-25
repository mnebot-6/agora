package com.app.community

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* System remembers the user's choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

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
        if (intent == null) return

        // FCM notification tap: data payload arrives as intent extras
        val extras = intent.extras
        val notificationType = extras?.getString("type")
        val activityId = extras?.getString("activity_id")
        if (notificationType != null && activityId != null) {
            DeepLinkHandler.setNotificationActivityId(activityId)
            return
        }

        val data = intent.data ?: return
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
