package io.github.xixka.qbittorrent

import android.app.Application
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.qbt.LocalEngineManager
import io.github.xixka.qbittorrent.qbt.LocalEngineService
import io.github.xixka.qbittorrent.util.ThemeUtils

class QBApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = ServiceLocator.prefs(this)
        // Day/night mode from the appearance settings (default: follow system).
        ThemeUtils.applyThemeMode(prefs.themeMode)

        // Cold-start fast path (Enhanced edition): kick off the bundled
        // engine BEFORE any activity inflates its layout, so process spawn
        // and UI construction run in parallel with the engine boot instead
        // of serially. LocalEngineService is idempotent (a STARTING/RUNNING
        // engine is never restarted by a second start request) and the
        // process is only ever created for a visible activity launch here,
        // which is an allowed foreground-service start. MainActivity keeps
        // its own call as a fallback for exotic launch paths.
        if (BuildConfig.IS_ENHANCED &&
            LocalEngineManager.isSupported(this) &&
            !prefs.useRemoteServer && // remote-control mode: the bundled engine stays down
            !LocalEngineManager.isRunning() &&
            LocalEngineManager.state != LocalEngineManager.State.STARTING
        ) {
            runCatching { LocalEngineService.start(this) }
        }
    }
}
