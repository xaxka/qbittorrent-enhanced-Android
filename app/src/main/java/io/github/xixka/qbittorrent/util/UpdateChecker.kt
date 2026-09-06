package io.github.xixka.qbittorrent.util

import android.os.Build
import io.github.xixka.qbittorrent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks for app updates via the GitHub Releases published by CI.
 *
 * The Android CI workflow builds every push with versionName = bundled
 * qBittorrent-Enhanced engine version (e.g. "5.2.3.10") and versionCode =
 * Unix epoch seconds (unique, strictly increasing per build), publishing the
 * APKs to the rolling `dev` release under stable ABI-named assets
 * ("qBittorrent-Enhanced-arm64-v8a.apk") and recording the build info in the
 * release body ("Version: 5.2.3.10 (versionCode 1789944235)"). This checker
 * downloads the release list, picks the newest published build and compares
 * it against BuildConfig.VERSION_CODE (the versionName no longer changes
 * per build, so the code is the authoritative signal).
 */
object UpdateChecker {

    private const val REPO_API = "https://api.github.com/repos/xixka/qbittorrentAndroid"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class Update(
        /** versionName of the newest build, e.g. "5.2.3.10". */
        val version: String,
        /** versionCode of the newest build (Unix epoch seconds). */
        val versionCode: Long,
        /** Human release page URL. */
        val htmlUrl: String,
        /** Release notes (markdown body, may be empty). */
        val notes: String,
        /** Direct APK download URL matching the current edition + device ABI. */
        val apkUrl: String?,
        /** Size of the matched APK in bytes (0 when unknown). */
        val apkSize: Long,
    )

    /**
     * Fetches the newest published release and returns an [Update] when it is
     * newer than the running build, `null` when up-to-date.
     * @throws java.io.IOException on network / API failures
     */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$REPO_API/releases?per_page=20")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("GitHub API HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw java.io.IOException("Empty response")
            val releases = org.json.JSONArray(body)
            var best: Update? = null
            for (i in 0 until releases.length()) {
                val rel = releases.optJSONObject(i) ?: continue
                if (rel.optBoolean("draft")) continue
                val candidate = parseRelease(rel) ?: continue
                if (best == null || candidate.versionCode > best.versionCode) best = candidate
            }
            // only report when strictly newer than the running build:
            // versionCode (epoch seconds, unique per CI build) is the
            // authoritative signal — the engine versionName is stable across
            // builds, so comparing names would never surface an update.
            // The name comparison is only a fallback for releases whose body
            // carried no versionCode.
            if (best != null && isNewer(best)) best else null
        }
    }

    private fun isNewer(candidate: Update): Boolean {
        if (candidate.versionCode > 0) {
            return candidate.versionCode > BuildConfig.VERSION_CODE.toLong()
        }
        // release without a versionCode in its body: compare version names
        // (the packed "5.2.3" -> 50203 value must NOT be used as a versionCode,
        // it would always read as older than an epoch-based build code)
        val remote = parseVersion(candidate.version) ?: return false
        val local = parseVersion(BuildConfig.VERSION_NAME) ?: return false
        return remote > local
    }

    /** "5.2.3.10" (or "5.2.3.10-debug") -> 50203, component-packed. */
    private fun parseVersion(version: String): Long? {
        val m = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(version) ?: return null
        val (a, b, c) = m.destructured
        return (a.toLong() * 100 + b.toLong()) * 100 + c.toLong()
    }

    private fun parseRelease(rel: JSONObject): Update? {
        val versionCodeFromBody = Regex(
            "versionCode\\s+(\\d+)", RegexOption.IGNORE_CASE
        ).find(rel.optString("body"))?.groupValues?.get(1)?.toLongOrNull()

        val assets = rel.optJSONArray("assets") ?: org.json.JSONArray()
        var versionFromBody: String? = Regex(
            "Version:\\s*([0-9]+\\.[0-9]+\\.[0-9]+)", RegexOption.IGNORE_CASE
        ).find(rel.optString("body"))?.groupValues?.get(1)

        // Fallback: newest version embedded in the asset names
        if (versionFromBody == null) {
            for (i in 0 until assets.length()) {
                val m = Regex("([0-9]+\\.[0-9]+\\.[0-9]+)").find(assets.optJSONObject(i)?.optString("name") ?: "")
                if (m != null) versionFromBody = m.groupValues[1]
            }
        }
        val version = versionFromBody ?: return null
        // versionCode is the Unix-epoch build code from the release body; a
        // missing one stays 0 so isNewer() falls back to version-name compare
        // (never stuff a packed version number into it)
        val versionCode = versionCodeFromBody ?: 0L

        val apk = pickApk(assets)
        return Update(
            version = version,
            versionCode = versionCode,
            htmlUrl = rel.optString("html_url"),
            notes = rel.optString("body").trim(),
            apkUrl = apk?.first,
            apkSize = apk?.second ?: 0L,
        )
    }

    /** Finds the APK asset matching the running edition and the primary device ABI. */
    private fun pickApk(assets: org.json.JSONArray): Pair<String, Long>? {
        val prefix = if (BuildConfig.IS_ENHANCED) "qBittorrent-Enhanced-" else "qBittorrent-Remote-"
        var universal: Pair<String, Long>? = null
        var primary: Pair<String, Long>? = null
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (!name.startsWith(prefix) || !name.endsWith(".apk")) continue
            val pair = a.optString("browser_download_url") to a.optLong("size")
            val abiTag = Regex("-(arm64-v8a|armeabi-v7a|x86_64|x86)\\.apk$").find(name)?.groupValues?.get(1)
            when {
                abiTag == null -> universal = pair
                abiTag == primaryAbi -> primary = pair
                // 32-bit x86 devices can also run under x86_64 builds — not
                // preferred, kept as a last-resort fallback below
            }
        }
        // 32-bit x86 fallback: use the x86_64 build
        if (primary == null && primaryAbi == "x86") {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (name.startsWith(prefix) && name.endsWith("-x86_64.apk")) {
                    primary = a.optString("browser_download_url") to a.optLong("size")
                }
            }
        }
        return primary ?: universal
    }
}
