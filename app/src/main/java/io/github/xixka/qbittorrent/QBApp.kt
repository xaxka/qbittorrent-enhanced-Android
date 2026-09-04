package io.github.xixka.qbittorrent

import android.app.Application
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.util.ThemeUtils

class QBApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = ServiceLocator.prefs(this)
        // Day/night mode from the appearance settings (default: follow system).
        ThemeUtils.applyThemeMode(prefs.themeMode)
    }
}
