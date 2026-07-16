package com.app.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun StatusBarEffect(statusBarColor: Color, darkIcons: Boolean) {
    // En web no hay status bar del sistema que pintar. El color de la barra
    // del navegador/PWA se controla con <meta name="theme-color"> en index.html.
}
