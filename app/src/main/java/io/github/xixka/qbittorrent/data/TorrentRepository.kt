package io.github.xixka.qbittorrent.data

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
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin, typed façade over the qBittorrent Web API.
 * All actions are suspend functions executed with authentication handling.
 */
class TorrentRepository(private val client: QBApiClient) {

    suspend fun appVersion(): String = client.withAuth { it.appVersion() }

    suspend fun webApiVersion(): String = client.withAuth { it.webApiVersion() }

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

    suspend fun addTorrent(
        urls: String?,
        fileBytes: ByteArray? = null,
        fileName: String = "torrent.torrent",
        savePath: String? = null,
        category: String? = null,
        paused: Boolean = false,
        sequential: Boolean = false,
    ) = client.withAuth {
        if (fileBytes != null) {
            val mediaType = "application/x-bittorrent".toMediaTypeOrNull()
            val body = fileBytes.toRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("torrents", fileName, body)
            it.addTorrentFile(
                torrents = part,
                savePath = savePath?.toFormPart(),
                category = category?.takeIf { c -> c.isNotBlank() }?.toFormPart(),
                paused = paused.toString().toFormPart(),
                sequential = sequential.toString().toFormPart(),
            )
        } else {
            it.addTorrent(
                urls = urls?.trim(),
                savePath = savePath?.takeIf { p -> p.isNotBlank() },
                category = category?.takeIf { c -> c.isNotBlank() },
                paused = paused.toString(),
                sequential = sequential.toString(),
            )
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
