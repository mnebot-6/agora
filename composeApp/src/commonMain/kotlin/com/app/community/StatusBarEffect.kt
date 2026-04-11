package com.app.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Configures the system status bar to match the app's primary color.
 * Android: sets status bar background and icon brightness.
 * iOS: no-op (handled by the system).
 */
@Composable
expect fun StatusBarEffect(statusBarColor: Color, darkIcons: Boolean)
