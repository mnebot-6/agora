package com.app.community

import androidx.compose.ui.window.ComposeUIViewController
import com.app.community.di.appModules
import com.russhwolf.settings.NSUserDefaultsSettings
import org.koin.core.context.startKoin
import platform.Foundation.NSUserDefaults

fun MainViewController() = ComposeUIViewController(
    configure = {
        val settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
        startKoin {
            modules(appModules(settings))
        }
    },
) {
    App()
}
