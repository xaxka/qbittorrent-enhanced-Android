package io.github.xixka.qbittorrent.data

import android.content.Context
import android.content.SharedPreferences
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.api.QBApiClient
import io.github.xixka.qbittorrent.qbt.NoxConfig
import io.github.xixka.qbittorrent.util.ThemeUtils

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

    private val gson = com.google.gson.Gson()

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

    /**
     * Engine watchdog: when the app runs against the bundled engine and no
     * remote server is configured, periodically re-probe the engine and
     * restart it if the process died — the service then keeps the download
     * session alive even without the UI.
     */
    var engineWatchdog: Boolean
        get() = sp.getBoolean(KEY_ENGINE_WATCHDOG, BuildConfig.IS_ENHANCED)
        set(value) = sp.edit().putBoolean(KEY_ENGINE_WATCHDOG, value).apply()

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

    /**
     * Material You dynamic colors, ON by default on devices that support
     * them (Android 12+). Turning it off falls back to the static palette.
     */
    var dynamicColors: Boolean
        get() = sp.getBoolean(KEY_DYNAMIC_COLORS, true)
        set(value) = sp.edit().putBoolean(KEY_DYNAMIC_COLORS, value).apply()

    /** Day/night mode: system (default), light or dark. */
    var themeMode: String
        get() = sp.getString(KEY_THEME_MODE, ThemeUtils.MODE_SYSTEM) ?: ThemeUtils.MODE_SYSTEM
        set(value) = sp.edit().putString(KEY_THEME_MODE, value).apply()

    // ---- remote server profiles (qBitController-style multi-server) ----

    /**
     * All configured remote servers, qBitController style. Each entry is a
     * full connection profile; [activeServerId] selects the one in use.
     */
    var serversJson: String
        get() = sp.getString(KEY_SERVERS_JSON, "") ?: ""
        set(value) = sp.edit().putString(KEY_SERVERS_JSON, value).apply()

    var activeServerId: Long
        get() = sp.getLong(KEY_ACTIVE_SERVER, 0L)
        set(value) = sp.edit().putLong(KEY_ACTIVE_SERVER, value).apply()

    /** "Bundled engine" is modeled as a pseudo profile with this id. */
    val localEngineProfileId: Long get() = -1L

    fun serverProfiles(): List<ServerProfile> {
        val migrated = migrateLegacyServer()
        val json = serversJson
        val list = if (json.isBlank()) emptyList() else runCatching {
            gson.fromJson(json, Array<ServerProfile>::class.java).toList()
        }.getOrDefault(emptyList())
        return if (migrated && list.isNotEmpty()) list else list
    }

    fun saveServerProfile(profile: ServerProfile): List<ServerProfile> {
        val list = serverProfiles().toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        val updated = list.sortedBy { it.name.lowercase() }
        serversJson = gson.toJson(updated.toTypedArray())
        return updated
    }

    fun deleteServerProfile(id: Long): List<ServerProfile> {
        val updated = serverProfiles().filterNot { it.id == id }
        serversJson = gson.toJson(updated.toTypedArray())
        if (activeServerId == id) {
            activeServerId = updated.firstOrNull()?.id ?: 0L
        }
        return updated
    }

    fun activeServer(): ServerProfile? {
        val list = serverProfiles()
        return list.firstOrNull { it.id == activeServerId } ?: list.firstOrNull()
    }

    fun switchServer(id: Long) {
        activeServerId = id
        ServiceLocator.resetClient()
    }

    /**
     * One-time migration of the legacy single-server fields into the profile
     * list (returns true when the legacy values were just consumed).
     */
    private fun migrateLegacyServer(): Boolean {
        if (sp.getBoolean(KEY_SERVERS_MIGRATED, false)) return false
        sp.edit().putBoolean(KEY_SERVERS_MIGRATED, true).apply()
        val host = sp.getString(KEY_HOST, "") ?: ""
        if (host.isBlank() || BuildConfig.IS_ENHANCED) return false
        val profile = ServerProfile(
            id = 1L,
            name = host,
            host = host,
            port = sp.getInt(KEY_PORT, ServerConfig.DEFAULT_PORT),
            https = sp.getBoolean(KEY_HTTPS, false),
            basePath = sp.getString(KEY_BASE_PATH, "") ?: "",
            username = sp.getString(KEY_USERNAME, "admin") ?: "admin",
            password = sp.getString(KEY_PASSWORD, "") ?: "",
            trustAllCerts = sp.getBoolean(KEY_TRUST_ALL, false),
        )
        serversJson = gson.toJson(arrayOf(profile))
        if (activeServerId == 0L) activeServerId = 1L
        return true
    }

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
            val p = activeServer()
            if (p != null) {
                ServerConfig(
                    host = p.host,
                    port = p.port,
                    https = p.https,
                    basePath = p.basePath,
                    username = p.username,
                    password = p.password,
                    trustAllCerts = p.trustAllCerts,
                )
            } else {
                ServerConfig(host = "")
            }
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
        const val KEY_DYNAMIC_COLORS = "dynamic_colors"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SERVERS_JSON = "servers_json"
        const val KEY_ACTIVE_SERVER = "active_server_id"
        const val KEY_SERVERS_MIGRATED = "servers_migrated"
        const val KEY_ENGINE_WATCHDOG = "engine_watchdog"
    }
}

/** One saved remote-server connection profile (qBitController parity). */
data class ServerProfile(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int = ServerConfig.DEFAULT_PORT,
    val https: Boolean = false,
    val basePath: String = "",
    val username: String = "admin",
    val password: String = "",
    val trustAllCerts: Boolean = false,
) {
    fun toServerConfig(): ServerConfig = ServerConfig(
        host = host,
        port = port,
        https = https,
        basePath = basePath,
        username = username,
        password = password,
        trustAllCerts = trustAllCerts,
    )

    fun displayName(): String = name.ifBlank { host }
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

    /**
     * One-off repository for an ad-hoc [config] — used by the server editor's
     * "test connection" button before the profile is saved.
     */
    fun testRepository(@Suppress("UNUSED_PARAMETER") context: Context, config: ServerConfig): TorrentRepository {
        val testClient = QBApiClient { config }
        return TorrentRepository(testClient)
    }
}
