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
 */
object NoxConfig {

    const val BINARY_LIB_NAME = "libqbittorrent-nox.so"

    /** Username of the local engine WebUI (the qBittorrent default). */
    const val WEBUI_USERNAME = "admin"

    const val WEBUI_DEFAULT_PASSWORD = "adminadmin"

    fun profileDir(context: android.content.Context): File =
        File(context.filesDir, "qbt-profile").apply { mkdirs() }

    fun configDir(context: android.content.Context): File =
        File(profileDir(context), "qBittorrent").apply { mkdirs() }

    fun configFile(context: android.content.Context): File =
        File(configDir(context), "qBittorrent.conf")

    /**
     * Writes a fresh configuration; returns the effective WebUI port.
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

        val address = if (lanAccess) "0.0.0.0" else "127.0.0.1"
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
                append("WebUI\\LocalHostAuth=false\n")
                append("WebUI\\Password_PBKDF2=\"@ByteArray(")
                append(pbkdf2String(password))
                append(")\"\n")
                append("WebUI\\Port=").append(webUiPort).append('\n')
                append("WebUI\\Username=").append(WEBUI_USERNAME).append('\n')
                append("WebUI\\ServerDomains=*\n")
                append("Connection\\PortRangeMin=6881\n")
                append("Connection\\GlobalDLLimit=-1\n")
                append("Connection\\GlobalUPLimit=-1\n")
                // keep the memory footprint moderate on Android devices
                append("Advanced\\socket_backlog_size=256\n\n")
            }
        )
        return webUiPort
    }

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
