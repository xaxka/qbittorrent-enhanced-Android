package io.github.xixka.qbittorrent.qbt

import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Generates the qBittorrent profile used by the bundled qbittorrent-enhanced-nox
 * binary (the OpenListAndroid bionic build). The file format is the upstream
 * qBittorrent INI dialect; the password digest follows the WebUI PBKDF2 scheme
 * (PBKDF2-HMAC-SHA512, 100k iterations, 16-byte salt, 64-byte key,
 * base64(salt):base64(key)).
 *
 * IMPORTANT: since qBittorrent 4.x, running with `--profile=<dir>` makes the
 * engine read its config from `<dir>/qBittorrent/config/qBittorrent.conf`
 * (and store session state under `<dir>/qBittorrent/data/`). A previous
 * version of this class wrote the file to `<dir>/qBittorrent/qBittorrent.conf`
 * — a path the engine never reads — so the engine silently fell back to its
 * defaults (WebUI port 8080, random admin password, localhost auth enabled)
 * and the startup probe failed. The config path below matches the upstream
 * Profile::Profile() layout (profile.cpp: root + "/qBittorrent" + "/config").
 */
object NoxConfig {

    const val BINARY_LIB_NAME = "libqbittorrent-nox.so"

    /** Username of the local engine WebUI (the qBittorrent default). */
    const val WEBUI_USERNAME = "admin"

    const val WEBUI_DEFAULT_PASSWORD = "adminadmin"

    fun profileDir(context: android.content.Context): File =
        File(context.filesDir, "qbt-profile").apply { mkdirs() }

    /**
     * Effective config directory of a `--profile` run:
     * `<profile>/qBittorrent/config`.
     */
    fun configDir(context: android.content.Context): File =
        File(File(profileDir(context), "qBittorrent"), "config").apply { mkdirs() }

    fun configFile(context: android.content.Context): File =
        File(configDir(context), "qBittorrent.conf")

    // Managed settings: values the app owns and re-applies on every start.
    private const val KEY_WEBUI_ADDRESS = "WebUI\\Address"
    private const val KEY_WEBUI_PORT = "WebUI\\Port"
    private const val KEY_WEBUI_USERNAME = "WebUI\\Username"
    private const val KEY_WEBUI_LOCAL_AUTH = "WebUI\\LocalHostAuth"
    private const val KEY_WEBUI_HOST_VALIDATION = "WebUI\\HostHeaderValidation"
    private const val KEY_WEBUI_PASSWORD = "WebUI\\Password_PBKDF2"
    private const val KEY_SAVE_PATH = "Session\\DefaultSavePath"

    /*
     * DHT bootstrap assist for networks with polluted DNS (China Mobile and
     * friends):
     *
     * qBittorrent-Enhanced (unlike upstream qBittorrent) exposes the libtorrent
     * `dht_bootstrap_nodes` setting through the config key
     * `BitTorrent\Session\DHTBootstrapNodes`. Its built-in default is
     * "dht.libtorrent.org:25401, dht.transmissionbt.com:6881,
     * router.bittorrent.com:6881" — and on Chinese carrier networks the GFW
     * poisons the plaintext DNS answers for dht.libtorrent.org and
     * router.bittorrent.com (they resolve to facebook/twitter/ntt addresses),
     * so 2 of the 3 default bootstrap contacts are dead and DHT never
     * bootstraps -> "DHT nodes: 0".
     *
     * The seed therefore writes DHT_BOOTSTRAP_CN_FRIENDLY below: HOSTNAMES
     * ONLY, no IP literals. Names are the stable contract — the official
     * routers have kept theirs for a decade while the addresses behind them
     * rotate (dht.libtorrent.org changed IP between app rounds); baked IPs
     * rot silently, so none are shipped. Users on hostile carriers route
     * the official hostnames through their own proxy; the ouinet router
     * (qBittorrent 5.x's censorship-friendly default) resolves cleanly on
     * China Mobile even without one. The engine resolves the hostnames
     * itself at startup.
     *
     * Once ANY contact answers, libtorrent learns real nodes from the swarm,
     * persists them in its session state, and the bootstrap list stops
     * mattering.
     *
     * A list the user saved through the in-app qB settings editor
     * (dht_bootstrap_nodes) or the WebUI is never overwritten: only values
     * the app itself wrote in PAST rounds (the entry-set comparisons against
     * DHT_BOOTSTRAP_LEGACY / DHT_BOOTSTRAP_ROUND46, both carrying dead
     * contacts) are migrated to the current list; anything else is left
     * alone (see [sameBootstrapValue]).
     */
    private const val KEY_DHT_BOOTSTRAP = "Session\\DHTBootstrapNodes"

    /**
     * The official bootstrap routers, hostname-only (dht.libtorrent.org,
     * dht.transmissionbt.com and router.bittorrent.com are the libtorrent /
     * Transmission / BEP-5 defaults and have kept their names for a decade;
     * router.utorrent.com is just a CNAME of the last one). router.bt.ouinet.work
     * is qBittorrent 5.x's censorship-friendly router, which resolves cleanly
     * on China Mobile's plaintext DNS even without a proxy. No IP literals:
     * the addresses behind these names rotate, and the user points their own
     * proxy at the hostnames where the carrier kills the direct route.
     */
    const val DHT_BOOTSTRAP_CN_FRIENDLY =
        "dht.libtorrent.org:25401, dht.transmissionbt.com:6881, " +
            "router.bittorrent.com:6881, router.bt.ouinet.work:6881"

    /** Pre-round-41 seed list: existing installs carry this value in
     *  qBittorrent.conf, so the startup migration must still treat it as
     *  app-managed (entry-set comparison against BOTH lists). */
    const val DHT_BOOTSTRAP_LEGACY =
        "dht.transmissionbt.com:6881, 212.129.33.59:6881, " +
            "87.98.162.88:6881, 185.157.221.247:25401, 67.215.246.10:6881"

    /** The round-55/56 seed list: hostname + measured-answering IP
     *  literals. The literals have exactly the rotting problem this list
     *  was introduced to fix, so installs carrying it migrate to the
     *  hostname-only list above. */
    const val DHT_BOOTSTRAP_ROUND56 =
        "dht.libtorrent.org:25401, dht.transmissionbt.com:6881, " +
            "router.bittorrent.com:6881, router.bt.ouinet.work:6881, " +
            "212.129.33.59:6881, 87.98.162.88:6881, " +
            "185.126.239.132:6881, 168.222.245.126:6881, 103.75.116.208:6881"

    /** The round-54 seed list: all-hostname, but dht.libtorrent.org and
     *  router.utorrent.com resolve to garbage on China Mobile's plaintext
     *  DNS — installs carrying it migrate to the current list above. */
    const val DHT_BOOTSTRAP_ROUND54 =
        "dht.transmissionbt.com:6881, dht.libtorrent.org:25401, " +
            "router.utorrent.com:6881, router.bt.ouinet.work:6881"

    /** The round-46 seed list (dead bitcomet router + now-stale literals):
     *  installs carrying it must also count as app-managed so the startup
     *  migration replaces them with the measured list above. */
    const val DHT_BOOTSTRAP_ROUND46 =
        "dht.transmissionbt.com:6881, dht.libtorrent.org:25401, " +
            "router.utorrent.com:6881, router.bitcomet.org:6881, " +
            "router.bt.ouinet.work:6881, " +
            "212.129.33.59:6881, 87.98.162.88:6881, " +
            "185.157.221.247:25401, 67.215.246.10:6881"

    // qB's own "bypass authentication for clients in whitelisted subnets"
    // feature. The app keeps this switch OFF unconditionally: LAN clients
    // must log in with the WebUI credentials (user's explicit requirement —
    // a whitelist would silently turn the LAN login screen off again). The
    // key is still patched so configs written by the round-16 builds (which
    // enabled the bypass) self-heal back to password-only on the next start.
    private const val KEY_WEBUI_WL_ENABLED = "WebUI\\AuthSubnetWhitelistEnabled"

    /**
     * Makes sure the engine config exists and reflects the app-managed
     * settings. On the very first start a full default config is written;
     * afterwards only the managed keys are patched in place, so settings the
     * user changed through the WebUI (speed limits, connection settings, …)
     * survive engine restarts.
     *
     * The WebUI username and password are app-managed and re-applied on
     * EVERY start: they are the credentials the app itself logs in with
     * (Prefs.engineUsername/enginePassword) and the ones shown in Settings →
     * Server connection. Forcing them back on restart is what keeps the
     * app, the LAN browser login and the config file from drifting apart
     * (a username-only patch previously desynchronized them → every LAN
     * login answered "Invalid username or password").
     *
     * @return the effective WebUI port.
     */
    fun seed(
        context: android.content.Context,
        webUiPort: Int,
        lanAccess: Boolean,
        savePath: String,
        username: String = WEBUI_USERNAME,
        password: String = WEBUI_DEFAULT_PASSWORD,
    ): Int {
        val conf = configFile(context)
        // remove upgrade residue from a previously interrupted start
        File(configDir(context), "qBittorrent_new.conf").delete()
        // legacy layout from older app versions (never read by the engine)
        File(profileDir(context), "qBittorrent/qBittorrent.conf").delete()

        if (!conf.isFile) {
            writeDefaultConfig(conf, webUiPort, lanAccess, savePath, username, password)
        } else {
            patchConfig(conf, webUiPort, lanAccess, savePath, username, password)
        }
        return webUiPort
    }

    private fun writeDefaultConfig(
        conf: File,
        webUiPort: Int,
        lanAccess: Boolean,
        savePath: String,
        username: String,
        password: String,
    ) {
        val address = if (lanAccess) "*" else "127.0.0.1"
        conf.writeText(
            buildString {
                append("[AutoRun]\nenabled=false\n\n")
                append("[BitTorrent]\n")
                append("Session\\DefaultSavePath=").append(escapePath(savePath)).append('\n')
                // DHT + LSD + PeX explicit: magnets stuck at "fetching
                // metadata" otherwise rely on migrated defaults; keep the
                // discovery stack unconditionally on in the seed config.
                append("Session\\DHTEnabled=true\n")
                append("Session\\LSDEnabled=true\n")
                append("Session\\PeXEnabled=true\n")
                append("Session\\Port=6881\n")
                // DHT bootstrap contacts: the static CN-friendly hostname set
                // (see the KEY_DHT_BOOTSTRAP comment above).
                append("Session\\DHTBootstrapNodes=")
                append(DHT_BOOTSTRAP_CN_FRIENDLY).append('\n')
                append("\n[Core]\nAutoExitEnabled=false\n\n")
                append("[LegalNotice]\nAccepted=true\n\n")
                append("[Meta]\nMigrationVersion=8\n\n")
                append("[Network]\n")
                append("Cookies=\"\"\n\n")
                append("[Preferences]\n")
                append("WebUI\\Address=").append(address).append('\n')
                append("WebUI\\HostHeaderValidation=false\n")
                append("WebUI\\LocalHostAuth=false\n")
                // QSettings serializes a QByteArray as `@ByteArray(<raw bytes
                // as latin1 text>)` — NO base64 layer (qsettings.cpp:
                // stringToVariant takes the chars between the parens
                // literally). The engine's password QByteArray therefore
                // IS the ASCII digest string `salt_b64:key_b64`, so the
                // correct INI line is the digest itself inside the marker:
                //   WebUI\Password_PBKDF2="@ByteArray(salt:key)"
                // The quotes mirror what QSettings itself writes (the
                // base64 padding '=' triggers iniEscapedString's
                // needsQuotes) and are stripped transparently on read. An
                // earlier version base64-encoded the digest a second time:
                // the engine then read base64 text without the ':'
                // separator, PBKDF2::verify() could never split it, and NO
                // password (LAN browser login, app re-login after the
                // hourly SID expiry) ever validated.
                append("WebUI\\Password_PBKDF2=\"@ByteArray(")
                append(pbkdf2String(password))
                append(")\"\n")
                append("WebUI\\Port=").append(webUiPort).append('\n')
                append("WebUI\\ServerDomains=*\n")
                append("WebUI\\Username=").append(username).append('\n')
                append("Connection\\PortRangeMin=6881\n")
                append("Connection\\GlobalDLLimit=-1\n")
                append("Connection\\GlobalUPLimit=-1\n")
                // keep the memory footprint moderate on Android devices
                append("Advanced\\socket_backlog_size=256\n\n")
            }
        )
    }

    /**
     * Replaces the values of the app-managed keys in an existing config,
     * preserving every other line (and their order) as-is. INI sections
     * in qBittorrent.conf are flat: `<Section>\<Key>=value`, so a simple
     * line-level rewrite is sufficient. Missing keys/sections are appended.
     */
    private fun patchConfig(
        conf: File,
        webUiPort: Int,
        lanAccess: Boolean,
        savePath: String,
        username: String,
        password: String,
    ) {
        val address = if (lanAccess) "*" else "127.0.0.1"
        val desired = mutableMapOf(
            KEY_WEBUI_ADDRESS to address,
            KEY_WEBUI_PORT to webUiPort.toString(),
            KEY_WEBUI_USERNAME to username,
            KEY_WEBUI_LOCAL_AUTH to "false",
            KEY_WEBUI_HOST_VALIDATION to "false",
            // LAN clients must always authenticate: qB's whitelisted-subnet
            // login bypass stays OFF (patched back off here so round-16-era
            // configs with the bypass enabled are migrated on next start).
            KEY_WEBUI_WL_ENABLED to "false",
            // fresh PBKDF2 digest of the app-tracked password: keeps the
            // engine in sync with what the app logs in with (and what the
            // Settings screen shows) after every restart. The digest string
            // goes inside `@ByteArray(...)` VERBATIM — QSettings performs no
            // base64 decoding of the marker's content (see writeDefaultConfig).
            KEY_WEBUI_PASSWORD to "\"@ByteArray(" + pbkdf2String(password) + ")\"",
            KEY_SAVE_PATH to escapePath(savePath),
        )
        val remaining = desired.toMutableMap()
        var bootstrapMigrated = false
        val lines = conf.readLines().map { line ->
            var result = line
            for ((key, value) in desired) {
                if (line == "$key=" || line.startsWith("$key=")) {
                    result = "$key=$value"
                    remaining.remove(key)
                    break
                }
            }
            // One-time migration of dead app-managed bootstrap lists (the
            // pre-round-41 and round-46 values carry a bitcomet router that
            // is NXDOMAIN and IP literals that no longer answer). Lists the
            // user wrote themselves don't match either set and survive.
            if (!bootstrapMigrated && line.startsWith("$KEY_DHT_BOOTSTRAP=")) {
                val current = line.removePrefix("$KEY_DHT_BOOTSTRAP=")
                if (sameBootstrapValue(current, DHT_BOOTSTRAP_LEGACY) ||
                    sameBootstrapValue(current, DHT_BOOTSTRAP_ROUND46) ||
                    sameBootstrapValue(current, DHT_BOOTSTRAP_ROUND54) ||
                    sameBootstrapValue(current, DHT_BOOTSTRAP_ROUND56)
                ) {
                    result = "$KEY_DHT_BOOTSTRAP=$DHT_BOOTSTRAP_CN_FRIENDLY"
                    bootstrapMigrated = true
                }
            }
            result
        }.toMutableList()

        // Append-if-absent keys: seeded only when the engine has no value of
        // its own (fresh installs upgrading from an older app). A key the
        // user set through the WebUI / in-app settings editor survives
        // untouched, so app-managed replacement never fights user intent.
        val ifAbsent = mapOf(KEY_DHT_BOOTSTRAP to DHT_BOOTSTRAP_CN_FRIENDLY)
        for ((key, value) in ifAbsent) {
            val present = lines.any { it == "$key=" || it.startsWith("$key=") }
            if (!present) remaining[key] = value
        }

        if (remaining.isNotEmpty()) {
            // append missing keys under their sections (create section if absent)
            val bySection = remaining.entries.groupBy({ sectionOf(it.key) }, { it.key })
            val sections = mutableSetOf<String>()
            lines.forEach { l -> if (l.startsWith("[") && l.endsWith("]")) sections.add(l) }
            for ((section, keys) in bySection) {
                if ("[$section]" !in sections) {
                    lines.add("")
                    lines.add("[$section]")
                }
                for (k in keys) lines.add("$k=${remaining.getValue(k)}")
            }
        }
        conf.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    /**
     * QSettings layout of qBittorrent.conf: the INI section is the PARENT
     * group of the key's first path component — `Session\…` keys live under
     * [BitTorrent] and `WebUI\…` keys live under [Preferences], mirroring
     * exactly how writeDefaultConfig lays them out. Deriving the section
     * from the first component itself would append `Session\…` under a
     * [Session] header, where the engine (reading the full key
     * `Session\Session\…`) would never find it.
     */
    private fun sectionOf(key: String): String = when (key.substringBefore('\\')) {
        "Session" -> "BitTorrent"
        "WebUI" -> "Preferences"
        else -> key.substringBefore('\\')
    }

    /**
     * Bootstrap-list equality that tolerates the engine's own reserialization
     * of the INI line (comma vs comma-space, entry order, trailing blanks):
     * two lists are the same when their entry SETS match.
     */
    fun sameBootstrapValue(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() && b.isNullOrBlank()) return true
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return normalizeEntries(a) == normalizeEntries(b)
    }

    private fun normalizeEntries(value: String): Set<String> =
        value.split(',', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    /**
     * PBKDF2 digest in the qBittorrent WebUI wire format:
     * base64(salt) + ":" + base64(key).
     */
    fun pbkdf2String(password: String): String {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val key = pbkdf2(password, salt)
        return java.util.Base64.getEncoder().encodeToString(salt) +
            ":" + java.util.Base64.getEncoder().encodeToString(key)
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(spec).encoded
    }

    /** INI path values: keep single-line, no escaping of backslashes (Linux paths). */
    private fun escapePath(value: String): String =
        value.replace("\r", " ").replace("\n", " ").trim()

    private const val ITERATIONS = 100_000
    private const val SALT_LEN = 16
    private const val KEY_LEN = 64
}
