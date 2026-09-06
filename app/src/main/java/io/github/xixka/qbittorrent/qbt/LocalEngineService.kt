package io.github.xixka.qbittorrent.qbt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service keeping the local qbittorrent-enhanced-nox engine alive.
 *
 * Includes a watchdog loop (enabled by default in the Enhanced edition, see
 * Settings): as long as no remote server is configured the service
 * re-probes the engine WebUI every 30 seconds and restarts the engine
 * process when it died — the download session keeps running without the UI.
 */
class LocalEngineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var watchdogJob: kotlinx.coroutines.Job? = null

    /** In-flight engine start (closes the race between two rapid start requests). */
    @Volatile
    private var engineStartJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val prefs = ServiceLocator.prefs(this)
        // Idempotency: the app now requests an engine start from BOTH the
        // Application class (cold-start fast path) and MainActivity
        // (fallback). A second request arriving while an earlier one is
        // still booting must NOT restart the engine — LocalEngineManager
        // always tears down before spawning, so a naive double start would
        // kill the engine the first request just brought up.
        val state = LocalEngineManager.state
        if (prefs.useRemoteServer ||
            state == LocalEngineManager.State.STARTING ||
            LocalEngineManager.isRunning() ||
            engineStartJob?.isActive == true
        ) {
            startWatchdog()
            return START_STICKY
        }
        engineStartJob = scope.launch {
            runCatching {
                LocalEngineManager.start(
                    context = this@LocalEngineService,
                    port = prefs.enginePort,
                    lanAccess = prefs.engineLanAccess,
                    savePath = prefs.engineSavePath,
                )
            }
            if (LocalEngineManager.state == LocalEngineManager.State.FAILED) {
                // keep the service alive: the watchdog retries periodically,
                // and the UI surfaces the failure via the state machine
                startWatchdog()
            } else {
                // the client endpoint is derived from the engine settings
                // (see Prefs.serverConfig); drop cached connections so the
                // client binds to the freshly started engine
                ServiceLocator.resetClient()
                startForegroundCompat()
                startWatchdog()
            }
        }
        return START_STICKY
    }

    /**
     * Engine watchdog: probes the engine every WATCHDOG_INTERVAL_MS and
     * restarts it when the process is gone. Always-on internal (no setting):
     * only idles while the app drives a remote server instead.
     */
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val prefs = ServiceLocator.prefs(this@LocalEngineService)
                if (prefs.useRemoteServer) continue
                // a start in flight (boot path / another watchdog tick) owns
                // the process: poking start() here would race it. The manager
                // itself is mutex-guarded, but skipping early keeps the
                // watchdog from queueing behind a slow 25 s readiness probe.
                if (LocalEngineManager.state == LocalEngineManager.State.STARTING) continue
                if (!LocalEngineManager.isRunning()) {
                    runCatching {
                        LocalEngineManager.start(
                            context = this@LocalEngineService,
                            port = prefs.enginePort,
                            lanAccess = prefs.engineLanAccess,
                            savePath = prefs.engineSavePath,
                        )
                        ServiceLocator.resetClient()
                    }
                }
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    override fun onDestroy() {
        stopWatchdog()
        LocalEngineManager.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.engine_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
    }

    private fun startForegroundCompat() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Purely informational notification: the engine lifecycle is
        // automatic (boot autostart + watchdog), so there is deliberately no
        // stop action.
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.engine_notification_title))
            .setContentText(getString(R.string.engine_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "local_engine"
        const val NOTIFICATION_ID = 42

        /** Watchdog probe cadence (30 s). */
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, LocalEngineService::class.java)
            )
        }
    }
}
