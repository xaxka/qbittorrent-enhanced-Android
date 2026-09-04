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

    /**
     * Makes sure the engine config exists and reflects the app-managed
     * settings. On the very first start a full default config is written;
     * afterwards only the managed keys are patched in place, so settings the
     * user changed through the WebUI (speed limits, connection settings, …)
     * survive engine restarts. The WebUI password is only seeded once — if
     * the user changes it from the WebUI, the app keeps it.
     *
     * @return the effective WebUI port.
     */
    fun seed(
        context: android.content.Context,
        webUiPort: Int,
        lanAccess: Boolean,
        savePath: String,
        password: String = WEBUI_DEFAULT_PASSWORD,
    ): Int {
        val conf = configFile(context)
        // remove upgrade residue from a previously interrupted start
        File(configDir(context), "qBittorrent_new.conf").delete()
        // legacy layout from older app versions (never read by the engine)
        File(profileDir(context), "qBittorrent/qBittorrent.conf").delete()

        if (!conf.isFile) {
            writeDefaultConfig(conf, webUiPort, lanAccess, savePath, password)
        } else {
            patchConfig(conf, webUiPort, lanAccess, savePath)
        }
        return webUiPort
    }

    private fun writeDefaultConfig(conf: File, webUiPort: Int, lanAccess: Boolean, savePath: String, password: String) {
        val address = if (lanAccess) "*" else "127.0.0.1"
        conf.writeText(
            buildString {
                append("[AutoRun]\nenabled=false\n\n")
                append("[BitTorrent]\n")
                append("Session\\DefaultSavePath=").append(escapePath(savePath)).append('\n')
                append("Session\\Port=6881\n\n")
                append("[Core]\nAutoExitEnabled=false\n\n")
                append("[LegalNotice]\nAccepted=true\n\n")
                append("[Meta]\nMigrationVersion=8\n\n")
                append("[Network]\n")
                append("Cookies=\"\"\n\n")
                append("[Preferences]\n")
                append("WebUI\\Address=").append(address).append('\n')
                append("WebUI\\HostHeaderValidation=false\n")
                append("WebUI\\LocalHostAuth=false\n")
                // QSettings serializes QByteArray values as
                // `@ByteArray(<base64 of the raw bytes>)` — the raw bytes here
                // are the ASCII `salt_b64:key_b64` digest string. Writing the
                // digest directly (as an earlier version did) makes the ':'
                // an invalid base64 char: the engine then reads a garbled hash
                // and NO password ever validates, locking out LAN browsers.
                append("WebUI\\Password_PBKDF2=\"@ByteArray(")
                append(java.util.Base64.getEncoder().encodeToString(pbkdf2String(password).toByteArray(Charsets.US_ASCII)))
                append(")\"\n")
                append("WebUI\\Port=").append(webUiPort).append('\n')
                append("WebUI\\ServerDomains=*\n")
                append("WebUI\\Username=").append(WEBUI_USERNAME).append('\n')
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
    private fun patchConfig(conf: File, webUiPort: Int, lanAccess: Boolean, savePath: String) {
        val address = if (lanAccess) "*" else "127.0.0.1"
        val desired = mapOf(
            KEY_WEBUI_ADDRESS to address,
            KEY_WEBUI_PORT to webUiPort.toString(),
            KEY_WEBUI_USERNAME to WEBUI_USERNAME,
            KEY_WEBUI_LOCAL_AUTH to "false",
            KEY_WEBUI_HOST_VALIDATION to "false",
            KEY_SAVE_PATH to escapePath(savePath),
        )
        val remaining = desired.toMutableMap()
        val lines = conf.readLines().map { line ->
            var result = line
            for ((key, value) in desired) {
                if (line == "$key=" || line.startsWith("$key=")) {
                    result = "$key=$value"
                    remaining.remove(key)
                    break
                }
            }
            result
        }.toMutableList()

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

    private fun sectionOf(key: String): String = key.substringBefore('\\')

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
