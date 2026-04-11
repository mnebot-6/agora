package com.app.community

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

@Composable
actual fun StatusBarEffect(statusBarColor: Color, darkIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val argbColor = statusBarColor.toArgb()
        SideEffect {
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            activity.enableEdgeToEdge(
                statusBarStyle = if (darkIcons) {
                    // Light background → dark icons
                    SystemBarStyle.light(argbColor, argbColor)
                } else {
                    // Dark background → light (white) icons
                    SystemBarStyle.dark(argbColor)
                },
            )
        }
    }
}
