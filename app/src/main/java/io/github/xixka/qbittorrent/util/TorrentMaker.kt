package io.github.xixka.qbittorrent.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Builds .torrent metainfo on-device, mirroring the qBittorrent desktop
 * torrent creator: source file/folder (SAF), tracker tiers (blank line
 * separates tiers), web seeds, comment, piece size (auto or fixed),
 * private flag — with SHA-1 piece hashing streamed straight from the
 * document tree, so arbitrary large sources never sit in memory.
 */
class TorrentMaker(private val resolver: ContentResolver) {

    data class Options(
        /** Torrent name; defaults to the source display name. */
        val name: String,
        /** Tracker tiers; tier 0's first URL becomes "announce". */
        val trackers: List<List<String>>,
        val webSeeds: List<String>,
        val comment: String,
        val createdBy: String,
        val private: Boolean,
        /** null = auto (16 KiB floor, double while > 2000 pieces, 16 MiB cap). */
        val pieceSize: Long?,
    )

    data class Result(
        val bytes: ByteArray,
        val name: String,
        val pieceSize: Long,
        val totalSize: Long,
        val fileCount: Int,
    )

    /** One walkable leaf of the source tree (or the single picked file). */
    private class Entry(val relPath: List<String>, val size: Long, val uri: Uri)

    /**
     * Builds the .torrent bytes. Blocking I/O — call from Dispatchers.IO;
     * cooperative with coroutine cancellation (checked per file & piece).
     */
    suspend fun build(
        source: Uri,
        sourceIsTree: Boolean,
        options: Options,
        onProgress: (bytesDone: Long, total: Long) -> Unit,
    ): Result {
        val entries = collectEntries(source, sourceIsTree)
        if (entries.isEmpty()) throw IllegalArgumentException("empty source")
        val totalSize = entries.sumOf { it.size }
        if (totalSize <= 0L) throw IllegalArgumentException("empty source")

        val pieceSize = (options.pieceSize ?: autoPieceSize(totalSize))
            .coerceAtLeast(MIN_PIECE)
        val name = options.name.ifBlank { entries.first().relPath.first() }

        // ---- SHA-1 pieces, streamed file by file ----
        val pieces = ByteArrayOutputStream((entries.size + 1) * 20)
        val digest = MessageDigest.getInstance("SHA-1")
        val buf = ByteArray(pieceSize.toInt())
        var pending = 0          // bytes buffered toward the current piece
        var done = 0L            // bytes hashed overall (for progress)
        var lastReport = 0L      // progress throttle watermark (per build)
        val pieceStart = System.currentTimeMillis()

        for (entry in entries) {
            coroutineContext.ensureActive()
            // empty files contribute no piece bytes — skip the stream, they
            // are still listed in the metainfo below
            if (entry.size == 0L) continue
            resolver.openInputStream(entry.uri)?.use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    val want = buf.size - pending
                    val n = input.read(buf, pending, want)
                    if (n < 0) break
                    pending += n
                    done += n
                    if (pending == buf.size) {
                        digest.update(buf, 0, pending)
                        pieces.write(digest.digest())
                        pending = 0
                    }
                    if (done - lastReport > 1 shl 20) {
                        lastReport = done
                        onProgress(done, totalSize)
                    }
                }
            } ?: throw IllegalStateException("cannot open ${entry.relPath.joinToString("/")}")
        }
        if (pending > 0) {
            digest.update(buf, 0, pending)
            pieces.write(digest.digest())
        }
        onProgress(totalSize, totalSize)

        // ---- metainfo ----
        val info = linkedMapOf<String, Any>(
            "piece length" to pieceSize,
            "pieces" to pieces.toByteArray(),
            "name" to name,
        )
        if (entries.size == 1 && !sourceIsTree) {
            info["length"] = entries[0].size
        } else {
            info["files"] = entries.map { e ->
                mapOf(
                    "length" to e.size,
                    "path" to e.relPath,
                )
            }
        }
        if (options.private) info["private"] = 1L

        val meta = linkedMapOf<String, Any>()
        val firstTracker = options.trackers.firstOrNull()?.firstOrNull()
        if (!firstTracker.isNullOrBlank()) meta["announce"] = firstTracker
        val tiers = options.trackers.filter { it.isNotEmpty() }
        if (tiers.isNotEmpty()) {
            meta["announce-list"] = tiers.map { tier -> tier.filter { it.isNotBlank() } }
        }
        if (options.webSeeds.isNotEmpty()) meta["url-list"] = options.webSeeds
        if (options.comment.isNotBlank()) meta["comment"] = options.comment
        if (options.createdBy.isNotBlank()) meta["created by"] = options.createdBy
        meta["creation date"] = pieceStart / 1000L
        meta["info"] = info

        return Result(
            bytes = Bencode.encode(meta),
            name = name,
            pieceSize = pieceSize,
            totalSize = totalSize,
            fileCount = entries.size,
        )
    }

    private fun collectEntries(source: Uri, isTree: Boolean): List<Entry> {
        val out = mutableListOf<Entry>()
        if (isTree) {
            val rootId = DocumentsContract.getTreeDocumentId(source)
            walk(source, rootId, emptyList(), out)
        } else {
            val displayName = queryDisplayName(source)
                ?: source.lastPathSegment ?: "source"
            val size = querySize(source) ?: 0L
            out += Entry(listOf(displayName), size, source)
        }
        // deterministic order: by relative path (qB sorts by path)
        return out.sortedBy { it.relPath.joinToString("/") }
    }

    private fun walk(
        treeUri: Uri,
        docId: String,
        prefix: List<String>,
        out: MutableList<Entry>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val dirs = mutableListOf<Pair<String, String>>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    dirs += id to name
                } else {
                    // 0-byte files are part of the source tree: qBittorrent
                    // desktop includes them in the metainfo, and leaving them
                    // out makes other clients fail the re-check
                    val size = c.getLong(3)
                    out += Entry(
                        prefix + name,
                        size,
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    )
                }
            }
        }
        for ((id, name) in dirs) {
            walk(treeUri, id, prefix + name, out)
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun querySize(uri: Uri): Long? =
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_SIZE),
            null, null, null,
        )?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    companion object {
        const val MIN_PIECE = 16L * 1024
        const val MAX_PIECE = 16L * 1024 * 1024

        /** Classic creator heuristic: 16 KiB floor, double while > 2000 pieces. */
        fun autoPieceSize(totalSize: Long): Long {
            var piece = MIN_PIECE
            while (piece < MAX_PIECE && totalSize / piece > 2000) piece *= 2
            return piece
        }
    }
}
