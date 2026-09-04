package io.github.xixka.qbittorrent.data

import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.api.QBApiClient
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.model.TorrentFile
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.model.Tracker
import io.github.xixka.qbittorrent.model.TransferInfo
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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
                addTorrentFile(part, fields.mapValues { it.value.toFormPart() })
            } else {
                addTorrent(fields)
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
}

private fun String.toFormPart() = toRequestBody("text/plain".toMediaTypeOrNull())
