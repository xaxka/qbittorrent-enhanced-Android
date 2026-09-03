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
import kotlinx.coroutines.launch

/**
 * Foreground service keeping the local qbittorrent-enhanced-nox engine alive.
 */
class LocalEngineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LocalEngineManager.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        val prefs = ServiceLocator.prefs(this)
        scope.launch {
            runCatching {
                LocalEngineManager.start(
                    context = this@LocalEngineService,
                    port = prefs.enginePort,
                    lanAccess = prefs.engineLanAccess,
                    savePath = prefs.engineSavePath,
                )
            }
            if (LocalEngineManager.state == LocalEngineManager.State.FAILED) {
                stopSelf()
            } else {
                ensureClientPointsAtEngine(prefs)
                startForegroundCompat()
            }
        }
        return START_STICKY
    }

    /**
     * Once the local engine is up, make sure the app client connects to it
     * when no remote server has been configured (first run / blank profile).
     */
    private fun ensureClientPointsAtEngine(prefs: io.github.xixka.qbittorrent.data.Prefs) {
        if (prefs.serverConfig().isConfigured) return
        prefs.serverHost = io.github.xixka.qbittorrent.data.Prefs.LOCAL_ENGINE_HOST
        prefs.serverPort = prefs.enginePort
        prefs.username = NoxConfig.WEBUI_USERNAME
        if (prefs.password.isBlank()) {
            prefs.password = NoxConfig.WEBUI_DEFAULT_PASSWORD
        }
        ServiceLocator.resetClient()
    }

    override fun onDestroy() {
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
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LocalEngineService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.engine_notification_title))
            .setContentText(getString(R.string.engine_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, getString(R.string.engine_stop), stop)
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
        const val ACTION_STOP = "io.github.xixka.qbittorrent.engine.STOP"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, LocalEngineService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LocalEngineService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
