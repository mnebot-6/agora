package com.app.community.core.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual class InviteSharer {
    actual fun share(text: String) {
        val activity = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        val rootController = UIApplication.sharedApplication
            .keyWindow
            ?.rootViewController
        rootController?.presentViewController(
            activity,
            animated = true,
            completion = null,
        )
    }
}

@Composable
actual fun rememberInviteSharer(): InviteSharer {
    return remember { InviteSharer() }
}
