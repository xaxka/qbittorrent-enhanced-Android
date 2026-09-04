package io.github.xixka.qbittorrent.qbt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.xixka.qbittorrent.BuildConfig

/**
 * Starts the local engine after boot. Boot autostart is an always-on
 * internal of the Enhanced edition (there is deliberately no setting for
 * it); it simply follows the app's install-and-it-works behavior.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!BuildConfig.IS_ENHANCED) return
        if (LocalEngineManager.isSupported(context)) {
            runCatching { LocalEngineService.start(context) }
        }
    }
}
