package io.github.xixka.qbittorrent

import android.app.Application
import io.github.xixka.qbittorrent.data.ServiceLocator

class QBApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.prefs(this)
    }
}
