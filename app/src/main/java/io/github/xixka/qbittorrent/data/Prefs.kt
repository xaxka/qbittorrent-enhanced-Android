package io.github.xixka.qbittorrent.data

import android.content.Context
import android.content.SharedPreferences
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.api.QBApiClient
import io.github.xixka.qbittorrent.qbt.NoxConfig

/**
 * Application preferences: server connection profile + local engine settings.
 *
 * Never stores anything else than what the user explicitly typed;
 * no tokens or secrets beyond the WebUI password the user chose to store.
 *
 * In the Enhanced edition the default client endpoint is derived from the
 * bundled engine (see [serverConfig]) — the app is usable out of the box and
 * never asks for server configuration unless the user opts in to a remote
 * server. In the standard edition the remote profile is always used.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var serverHost: String
        get() = sp.getString(KEY_HOST, if (BuildConfig.IS_ENHANCED) LOCAL_ENGINE_HOST else "") ?: ""
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

    // Remote-server credentials (used when useRemoteServer is on).
    // The bundled engine keeps its own credentials (engineUsername/…Password).
    var password: String
        get() = sp.getString(KEY_PASSWORD, "") ?: ""
        set(value) = sp.edit().putString(KEY_PASSWORD, value).apply()

    var pollIntervalSec: Int
        get() = sp.getInt(KEY_POLL, 2).coerceIn(1, 60)
        set(value) = sp.edit().putInt(KEY_POLL, value).apply()

    /**
     * Whether the app talks to a remote server instead of the bundled
     * engine. The standard (remote-control) edition always answers true;
     * the Enhanced edition defaults to the bundled engine and only asks
     * for server configuration when the user explicitly opts in.
     */
    var useRemoteServer: Boolean
        get() = if (BuildConfig.IS_ENHANCED) sp.getBoolean(KEY_USE_REMOTE, false) else true
        set(value) = sp.edit().putBoolean(KEY_USE_REMOTE, value).apply()

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

    // LAN access is on by default: the WebUI listens on every interface (with
    // authentication), so other devices in the network can reach it without
    // digging through settings. Setting it off binds to loopback only.
    var engineLanAccess: Boolean
        get() = sp.getBoolean(KEY_ENGINE_LAN, true)
        set(value) = sp.edit().putBoolean(KEY_ENGINE_LAN, value).apply()

    var engineAutoStart: Boolean
        get() = sp.getBoolean(KEY_ENGINE_AUTOSTART, BuildConfig.IS_ENHANCED)
        set(value) = sp.edit().putBoolean(KEY_ENGINE_AUTOSTART, value).apply()

    // Credentials of the bundled engine's WebUI. Kept separate from the
    // remote-server credentials so switching between engine and remote
    // never overwrites either profile.
    var engineUsername: String
        get() = sp.getString(KEY_ENGINE_USERNAME, NoxConfig.WEBUI_USERNAME) ?: NoxConfig.WEBUI_USERNAME
        set(value) = sp.edit().putString(KEY_ENGINE_USERNAME, value).apply()

    var enginePassword: String
        get() = sp.getString(KEY_ENGINE_PASSWORD, NoxConfig.WEBUI_DEFAULT_PASSWORD) ?: NoxConfig.WEBUI_DEFAULT_PASSWORD
        set(value) = sp.edit().putString(KEY_ENGINE_PASSWORD, value).apply()

    /** True when the app is currently driven by the bundled engine. */
    val usingLocalEngine: Boolean
        get() = BuildConfig.IS_ENHANCED && !useRemoteServer

    /** Epoch millis of the last automatic update check (GitHub Releases). */
    var lastUpdateCheck: Long
        get() = sp.getLong(KEY_UPDATE_CHECK_LAST, 0L)
        set(value) = sp.edit().putLong(KEY_UPDATE_CHECK_LAST, value).apply()

    fun serverConfig(): ServerConfig =
        if (usingLocalEngine) {
            // Derived endpoint: always points at the bundled engine, so the
            // client follows engine port/credential changes automatically.
            ServerConfig(
                host = LOCAL_ENGINE_HOST,
                port = enginePort,
                https = false,
                basePath = "",
                username = engineUsername,
                password = enginePassword,
                trustAllCerts = false,
            )
        } else {
            ServerConfig(
                host = serverHost,
                port = serverPort,
                https = serverHttps,
                basePath = serverBasePath,
                username = username,
                password = password,
                trustAllCerts = serverTrustAll,
            )
        }

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val FILE_NAME = "app_prefs"

        /** Default endpoint of the bundled local engine (Enhanced edition). */
        const val LOCAL_ENGINE_HOST = "127.0.0.1"

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
        const val KEY_USE_REMOTE = "use_remote_server"
        const val KEY_ENGINE_USERNAME = "engine_username"
        const val KEY_ENGINE_PASSWORD = "engine_password"
        const val KEY_UPDATE_CHECK_LAST = "update_check_last"
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
