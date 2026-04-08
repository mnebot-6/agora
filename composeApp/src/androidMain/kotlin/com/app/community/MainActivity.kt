package com.app.community

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.app.community.di.appModules
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        startKoin {
            modules(appModules)
        }

        handleDeepLink(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        // agora://invite/ABCD1234
        val data = intent?.data ?: return
        if (data.scheme == "agora" && data.host == "invite") {
            val code = data.pathSegments?.firstOrNull() ?: return
            DeepLinkHandler.setInviteCode(code)
        }
    }
}
