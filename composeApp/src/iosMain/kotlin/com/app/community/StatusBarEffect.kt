package com.app.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun StatusBarEffect(statusBarColor: Color, darkIcons: Boolean) {
    // iOS handles status bar appearance via Info.plist / UIViewController
}
