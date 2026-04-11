package com.app.community

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun StatusBarEffect(statusBarColor: Color, darkIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowInsetsControllerCompat(window, view)
                .isAppearanceLightStatusBars = darkIcons
        }
    }
}
