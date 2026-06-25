package com.app.community

import android.app.Application
import android.content.Context
import com.app.community.di.appModules
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.context.startKoin

class AgoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = SharedPreferencesSettings(
            getSharedPreferences("agora_prefs", Context.MODE_PRIVATE),
        )
        startKoin {
            modules(appModules(settings))
        }
    }
}
