package io.github.xixka.qbittorrent.data

import android.content.Context
import android.content.SharedPreferences
import io.github.xixka.qbittorrent.api.QBApiClient

/**
 * Application preferences: remote server profile + local engine settings.
 *
 * Never stores anything else than what the user explicitly typed;
 * no tokens or secrets beyond the WebUI password the user chose to store.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var serverHost: String
        get() = sp.getString(KEY_HOST, "") ?: ""
        set(value) = sp.edit().putString(KEY_HOST, value.trim()).apply()

    var serverPort: Int
        get() = sp.getInt(KEY_PORT, ServerConfig.DEFAULT_PORT)
        set(value) = sp.edit().putInt(KEY_PORT, value).apply()

    var serverHttps: Boolean
        get() = sp.getBoolean(KEY_HTTPS, false)
        set(value) = sp.edit().putBoolean(KEY_HTTPS, value).apply()

    var serverTrustAll: Boolean
        get() = sp.getBoolean(KEY_TRUST_ALL, false)
        set(value) = sp.edit().putBoolean(KEY_TRUST_ALL, value).apply()

    var serverBasePath: String
        get() = sp.getString(KEY_BASE_PATH, "") ?: ""
        set(value) = sp.edit().putString(KEY_BASE_PATH, value.trim().trim('/')).apply()

    var username: String
        get() = sp.getString(KEY_USERNAME, "admin") ?: "admin"
        set(value) = sp.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = sp.getString(KEY_PASSWORD, "") ?: ""
        set(value) = sp.edit().putString(KEY_PASSWORD, value).apply()

    var pollIntervalSec: Int
        get() = sp.getInt(KEY_POLL, 2).coerceIn(1, 60)
        set(value) = sp.edit().putInt(KEY_POLL, value).apply()

    // ---- local engine (qBittorrent Enhanced flavor only) ----

    var engineEnabled: Boolean
        get() = sp.getBoolean(KEY_ENGINE_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENGINE_ENABLED, value).apply()

    var enginePort: Int
        get() = sp.getInt(KEY_ENGINE_PORT, ServerConfig.DEFAULT_PORT)
        set(value) = sp.edit().putInt(KEY_ENGINE_PORT, value).apply()

    var engineSavePath: String
        get() = sp.getString(KEY_ENGINE_SAVE_PATH, "") ?: ""
        set(value) = sp.edit().putString(KEY_ENGINE_SAVE_PATH, value).apply()

    var engineLanAccess: Boolean
        get() = sp.getBoolean(KEY_ENGINE_LAN, false)
        set(value) = sp.edit().putBoolean(KEY_ENGINE_LAN, value).apply()

    var engineAutoStart: Boolean
        get() = sp.getBoolean(KEY_ENGINE_AUTOSTART, false)
        set(value) = sp.edit().putBoolean(KEY_ENGINE_AUTOSTART, value).apply()

    fun serverConfig(): ServerConfig = ServerConfig(
        host = serverHost,
        port = serverPort,
        https = serverHttps,
        basePath = serverBasePath,
        username = username,
        password = password,
        trustAllCerts = serverTrustAll,
    )

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val FILE_NAME = "app_prefs"

        const val KEY_HOST = "server_host"
        const val KEY_PORT = "server_port"
        const val KEY_HTTPS = "server_https"
        const val KEY_TRUST_ALL = "server_trust_all"
        const val KEY_BASE_PATH = "server_base_path"
        const val KEY_USERNAME = "server_username"
        const val KEY_PASSWORD = "server_password"
        const val KEY_POLL = "poll_interval"
        const val KEY_ENGINE_ENABLED = "engine_enabled"
        const val KEY_ENGINE_PORT = "engine_port"
        const val KEY_ENGINE_SAVE_PATH = "engine_save_path"
        const val KEY_ENGINE_LAN = "engine_lan"
        const val KEY_ENGINE_AUTOSTART = "engine_autostart"
    }
}

/**
 * Tiny service locator: builds and caches the API client / repository for the
 * current preference snapshot.
 */
object ServiceLocator {

    @Volatile
    private var prefs: Prefs? = null

    @Volatile
    private var client: QBApiClient? = null

    @Volatile
    private var repository: TorrentRepository? = null

    @Synchronized
    fun prefs(context: Context): Prefs =
        prefs ?: Prefs(context.applicationContext).also { prefs = it }

    @Synchronized
    fun client(context: Context): QBApiClient {
        val p = prefs(context)
        return client ?: QBApiClient { p.serverConfig() }.also { client = it }
    }

    @Synchronized
    fun repository(context: Context): TorrentRepository {
        return repository ?: TorrentRepository(client(context)).also { repository = it }
    }

    /** Called when server preferences change: drop caches, keep singletons. */
    @Synchronized
    fun resetClient() {
        client?.reset()
    }
}
