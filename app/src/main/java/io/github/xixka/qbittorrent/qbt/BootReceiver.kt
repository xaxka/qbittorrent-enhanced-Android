package io.github.xixka.qbittorrent.qbt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.data.ServiceLocator

/** Starts the local engine after boot when the user enabled auto-start. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!BuildConfig.IS_ENHANCED) return
        val prefs = ServiceLocator.prefs(context)
        if (prefs.engineAutoStart && LocalEngineManager.isSupported(context)) {
            runCatching { LocalEngineService.start(context) }
        }
    }
}
