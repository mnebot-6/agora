package com.app.community

import androidx.compose.ui.window.ComposeUIViewController
import com.app.community.di.appModules
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin {
            modules(appModules)
        }
    },
) {
    App()
}
