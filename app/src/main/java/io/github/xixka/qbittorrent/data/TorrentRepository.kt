package io.github.xixka.qbittorrent.data

import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.api.QBApiClient
import io.github.xixka.qbittorrent.model.LogEntry
import io.github.xixka.qbittorrent.model.MainData
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.model.RssRule
import io.github.xixka.qbittorrent.model.SearchPlugin
import io.github.xixka.qbittorrent.model.SearchResults
import io.github.xixka.qbittorrent.model.SearchStartResponse
import io.github.xixka.qbittorrent.model.ServerState
import io.github.xixka.qbittorrent.model.TorrentFile
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.model.Tracker
import io.github.xixka.qbittorrent.model.TransferInfo
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Thin, typed façade over the qBittorrent Web API.
 * All actions are suspend functions executed with authentication handling.
 */
class TorrentRepository(private val client: QBApiClient) {

    suspend fun appVersion(): String = client.withAuth { it.appVersion() }

    suspend fun webApiVersion(): String = client.withAuth { it.webApiVersion() }

    /** Default download location of the connected instance. */
    suspend fun defaultSavePath(): String =
        runCatching { client.withAuth { it.defaultSavePath() } }.getOrDefault("")

    /** Full preference snapshot (`GET /api/v2/app/preferences`). */
    suspend fun appPreferences(): JsonObject = client.withAuth { it.appPreferences() }

    /** Partial preference update (`POST /api/v2/app/setPreferences`). */
    suspend fun setPreferences(diff: JsonObject) =
        client.withAuth { it.setPreferences(diff.toString()) }

    suspend fun transferInfo(): TransferInfo = client.withAuth { it.transferInfo() }

    suspend fun torrents(
        filter: String? = null,
        category: String? = null,
        sort: String? = null,
        reverse: Boolean? = null,
    ): List<TorrentInfo> = client.withAuth { it.torrents(filter, category, sort, reverse) }

    suspend fun properties(hash: String): TorrentProperties =
        client.withAuth { it.torrentProperties(hash) }

    suspend fun files(hash: String): List<TorrentFile> = client.withAuth { it.torrentFiles(hash) }

    suspend fun trackers(hash: String): List<Tracker> = client.withAuth { it.torrentTrackers(hash) }

    suspend fun peers(hash: String): List<Peer> =
        client.withAuth { it.torrentPeers(hash).peers?.values?.toList() ?: emptyList() }

    suspend fun categories(): Map<String, QBCategory> = client.withAuth { it.categories() }


    suspend fun toggleFirstLastPiecePriority(hashes: List<String>) =
        client.withAuth { it.toggleFirstLastPiecePriority(hashes.joinToString("|")) }

    suspend fun pauseAll() = client.withAuth { it.pause("all") }
    suspend fun resumeAll() = client.withAuth { it.resume("all") }

    // ---------- actions ----------

    /**
     * Adds torrents by URL/magnet or .torrent file with the full parameter
     * set of the qBittorrent WebUI add dialog (qBitController parity).
     *
     * Limits are bytes/s; [seedingTimeLimit] is minutes; [autoTmm] null =
     * leave the server default. Both `paused` and `stopped` keys are sent
     * so 4.x (`paused`) and 5.x (`stopped`) servers both behave.
     */
    suspend fun addTorrent(
        urls: String?,
        fileBytes: ByteArray? = null,
        fileName: String = "torrent.torrent",
        savePath: String? = null,
        category: String? = null,
        paused: Boolean = false,
        sequential: Boolean = false,
        skipChecking: Boolean = false,
        firstLastPiece: Boolean = false,
        autoTmm: Boolean? = null,
        stopCondition: String? = null,
        contentLayout: String? = null,
        rename: String? = null,
        dlLimit: Long? = null,
        upLimit: Long? = null,
        ratioLimit: Double? = null,
        seedingTimeLimit: Long? = null,
    ): Response<ResponseBody> {
        val fields = buildMap {
            urls?.trim()?.takeIf { it.isNotEmpty() }?.let { put("urls", it) }
            savePath?.takeIf { it.isNotBlank() }?.let { put("savepath", it.trim()) }
            category?.takeIf { it.isNotBlank() }?.let { put("category", it.trim()) }
            put("paused", paused.toString())
            put("stopped", paused.toString())
            put("sequentialDownload", sequential.toString())
            put("skip_checking", skipChecking.toString())
            put("firstLastPiecePrio", firstLastPiece.toString())
            autoTmm?.let { put("autoTMM", it.toString()) }
            stopCondition?.let { put("stopCondition", it) }
            contentLayout?.let { put("contentLayout", it) }
            rename?.takeIf { it.isNotBlank() }?.let { put("rename", it.trim()) }
            dlLimit?.let { put("dlLimit", it.toString()) }
            upLimit?.let { put("upLimit", it.toString()) }
            ratioLimit?.let { put("ratioLimit", it.toString()) }
            seedingTimeLimit?.let { put("seedingTimeLimit", it.toString()) }
        }
        return client.withAuth {
            if (fileBytes != null) {
                val mediaType = "application/x-bittorrent".toMediaTypeOrNull()
                val body = fileBytes.toRequestBody(mediaType)
                val part = MultipartBody.Part.createFormData("torrents", fileName, body)
                it.addTorrentFile(part, fields.mapValues { v -> v.value.toFormPart() })
            } else {
                it.addTorrent(fields)
            }
        }
    }

    suspend fun pause(hashes: List<String>) = client.withAuth { it.pause(hashes.joinToString("|")) }

    suspend fun resume(hashes: List<String>) = client.withAuth { it.resume(hashes.joinToString("|")) }

    suspend fun delete(hashes: List<String>, deleteFiles: Boolean) =
        client.withAuth { it.delete(hashes.joinToString("|"), deleteFiles) }

    suspend fun recheck(hashes: List<String>) =
        client.withAuth { it.recheck(hashes.joinToString("|")) }

    suspend fun reannounce(hashes: List<String>) =
        client.withAuth { it.reannounce(hashes.joinToString("|")) }

    suspend fun setFilePriority(hash: String, fileIndexes: List<Int>, priority: Int) =
        client.withAuth { it.setFilePriority(hash, fileIndexes.joinToString("|"), priority) }

    suspend fun topPriority(hashes: List<String>) =
        client.withAuth { it.topPriority(hashes.joinToString("|")) }

    suspend fun bottomPriority(hashes: List<String>) =
        client.withAuth { it.bottomPriority(hashes.joinToString("|")) }

    suspend fun setCategory(hashes: List<String>, category: String) =
        client.withAuth { it.setCategory(hashes.joinToString("|"), category) }

    suspend fun createCategory(name: String, savePath: String) =
        client.withAuth { it.createCategory(name, savePath) }

    suspend fun editCategory(name: String, savePath: String) =
        client.withAuth { it.editCategory(name, savePath) }

    suspend fun removeCategory(name: String) =
        client.withAuth { it.removeCategories(name) }

    suspend fun toggleSequential(hashes: List<String>) =
        client.withAuth { it.toggleSequentialDownload(hashes.joinToString("|")) }

    suspend fun toggleSuperSeeding(hashes: List<String>) =
        client.withAuth { it.toggleSuperSeeding(hashes.joinToString("|")) }

    suspend fun setForceStart(hashes: List<String>, value: Boolean) =
        client.withAuth { it.setForceStart(hashes.joinToString("|"), value.toString()) }

    suspend fun addTrackers(hash: String, urls: String) =
        client.withAuth { it.addTrackers(hash, urls) }

    suspend fun removeTrackers(hash: String, urls: String) =
        client.withAuth { it.removeTrackers(hash, urls) }

    suspend fun setDownloadLimit(bytesPerSec: Long) =
        client.withAuth { it.setDownloadLimit(bytesPerSec.toString()) }

    suspend fun setUploadLimit(bytesPerSec: Long) =
        client.withAuth { it.setUploadLimit(bytesPerSec.toString()) }

    suspend fun toggleAltSpeedLimits() = client.withAuth { it.toggleAltSpeedLimits() }

    // ---------- extended surface (qBitController parity) ----------

    /** Server state / statistics snapshot. */
    suspend fun mainData(): MainData = client.withAuth { it.mainData() }

    suspend fun serverState(): ServerState? =
        runCatching { mainData().serverState }.getOrNull()

    // tags

    suspend fun tags(): List<String> = runCatching {
        client.withAuth { it.tags() }
    }.getOrDefault(emptyList())

    suspend fun createTags(names: List<String>) =
        client.withAuth { it.createTags(names.joinToString(",")) }

    suspend fun deleteTags(names: List<String>) =
        client.withAuth { it.deleteTags(names.joinToString(",")) }

    suspend fun addTags(hashes: List<String>, tags: List<String>) =
        client.withAuth { it.addTags(hashes.joinToString("|"), tags.joinToString(",")) }

    suspend fun removeTags(hashes: List<String>, tags: List<String>) =
        client.withAuth { it.removeTags(hashes.joinToString("|"), tags.joinToString(",")) }

    // per-torrent management

    suspend fun renameTorrent(hash: String, name: String) =
        client.withAuth { it.renameTorrent(hash, name) }

    suspend fun setLocation(hashes: List<String>, location: String) =
        client.withAuth { it.setLocation(hashes.joinToString("|"), location) }

    suspend fun setTorrentDownloadLimit(hashes: List<String>, bytesPerSec: Long) =
        client.withAuth { it.setTorrentDownloadLimit(hashes.joinToString("|"), bytesPerSec.toString()) }

    suspend fun setTorrentUploadLimit(hashes: List<String>, bytesPerSec: Long) =
        client.withAuth { it.setTorrentUploadLimit(hashes.joinToString("|"), bytesPerSec.toString()) }

    suspend fun setShareLimits(
        hashes: List<String>,
        ratioLimit: Double,
        seedingTimeLimit: Int,
        inactiveSeedingTimeLimit: Int,
    ) = client.withAuth {
        it.setShareLimits(
            hashes.joinToString("|"),
            ratioLimit.toString(),
            seedingTimeLimit.toString(),
            inactiveSeedingTimeLimit.toString(),
        )
    }

    suspend fun setSuperSeeding(hashes: List<String>, value: Boolean) =
        client.withAuth { it.setSuperSeeding(hashes.joinToString("|"), value.toString()) }

    suspend fun editTracker(hash: String, origUrl: String, newUrl: String) =
        client.withAuth { it.editTracker(hash, origUrl, newUrl) }

    suspend fun pieceStates(hash: String): List<Int> =
        runCatching { client.withAuth { it.pieceStates(hash) } }.getOrDefault(emptyList())

    // log

    suspend fun log(
        lastKnownId: Long = -1,
        normal: Boolean = true,
        info: Boolean = true,
        warning: Boolean = true,
        critical: Boolean = true,
    ): List<LogEntry> = client.withAuth { it.logMain(lastKnownId, normal, info, warning, critical) }

    // RSS

    /** Raw RSS tree (`withData` embeds the articles of every feed). */
    suspend fun rssItems(withData: Boolean = false): JsonObject =
        client.withAuth { it.rssItems(withData) }

    suspend fun rssMarkAsRead(itemPath: String, articleId: String? = null) =
        client.withAuth { it.rssMarkAsRead(itemPath, articleId) }

    suspend fun rssRefreshItem(itemPath: String) =
        client.withAuth { it.rssRefreshItem(itemPath) }

    suspend fun rssAddFeed(url: String, path: String) =
        client.withAuth { it.rssAddFeed(url, path) }

    suspend fun rssSetFeedUrl(path: String, url: String) =
        client.withAuth { it.rssSetFeedUrl(path, url) }

    suspend fun rssAddFolder(path: String) =
        client.withAuth { it.rssAddFolder(path) }

    suspend fun rssMoveItem(itemPath: String, destPath: String) =
        client.withAuth { it.rssMoveItem(itemPath, destPath) }

    suspend fun rssRemoveItem(path: String) =
        client.withAuth { it.rssRemoveItem(path) }

    suspend fun rssRules(): Map<String, RssRule> =
        runCatching { client.withAuth { it.rssRules() } }.getOrDefault(emptyMap())

    suspend fun rssSetRule(name: String, rule: RssRule) =
        client.withAuth {
            it.rssSetRule(name, com.google.gson.Gson().toJson(rule))
        }

    suspend fun rssRenameRule(name: String, newName: String) =
        client.withAuth { it.rssRenameRule(name, newName) }

    suspend fun rssRemoveRule(name: String) =
        client.withAuth { it.rssRemoveRule(name) }

    // search engine

    suspend fun searchStart(pattern: String, category: String, plugins: String): SearchStartResponse =
        client.withAuth { it.searchStart(pattern, category, plugins) }

    suspend fun searchStop(id: Int) = client.withAuth { it.searchStop(id) }

    suspend fun searchDelete(id: Int) = client.withAuth { it.searchDelete(id) }

    suspend fun searchResults(id: Int, offset: Int = 0, limit: Int = 500): SearchResults =
        client.withAuth { it.searchResults(id, offset, limit) }

    suspend fun searchPlugins(): List<SearchPlugin> =
        runCatching { client.withAuth { it.searchPlugins() } }.getOrDefault(emptyList())

    suspend fun searchEnablePlugin(names: List<String>, enable: Boolean) =
        client.withAuth { it.searchEnablePlugin(names.joinToString("|"), enable) }

    suspend fun searchInstallPlugin(sources: String) =
        client.withAuth { it.searchInstallPlugin(sources) }

    suspend fun searchUninstallPlugin(names: List<String>) =
        client.withAuth { it.searchUninstallPlugin(names.joinToString("|")) }

    suspend fun searchUpdatePlugins() = client.withAuth { it.searchUpdatePlugins() }
}

private fun String.toFormPart() = toRequestBody("text/plain".toMediaTypeOrNull())
