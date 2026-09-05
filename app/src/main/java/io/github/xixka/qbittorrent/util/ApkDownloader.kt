package io.github.xixka.qbittorrent.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * In-app multi-threaded APK downloader for GitHub-Releases updates.
 *
 * Splits the file into [threads] HTTP range requests that run in parallel
 * (each with its own file handle + one automatic resume attempt for its
 * remaining bytes), so update downloads use the full available bandwidth
 * instead of a browser round-trip. Falls back to a single stream when the
 * server does not advertise byte ranges or the file is small.
 */
object ApkDownloader {

    private const val MULTI_PART_MIN_BYTES = 8L * 1024 * 1024
    private const val CHUNK = 64 * 1024

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    class Progress(val downloaded: Long, val total: Long)

    /** Header metadata probed with a HEAD request (absent → single stream). */
    private class Meta(val total: Long, val acceptRanges: Boolean)

    /**
     * Downloads [url] to [dest] and returns it. The returned file is
     * complete and length-verified. Partial files are removed on failure
     * or cancellation.
     */
    suspend fun download(
        url: String,
        dest: File,
        threads: Int = 4,
        onProgress: (Progress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val meta = probe(url)
        if (dest.exists()) dest.delete()

        val ok = try {
            if (meta != null && meta.acceptRanges &&
                meta.total >= MULTI_PART_MIN_BYTES && threads > 1
            ) {
                parallel(url, dest, meta.total, threads, onProgress)
            } else {
                single(url, dest, meta?.total ?: -1L, onProgress)
            }
            meta == null || dest.length() == meta.total
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
        if (!ok) {
            dest.delete()
            throw IOException("size mismatch after download")
        }
        dest
    }

    private fun probe(url: String): Meta? = runCatching {
        http.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val ranges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
            if (total <= 0) null else Meta(total, ranges)
        }
    }.getOrNull()

    /** Plain single-connection download with progress. */
    private suspend fun single(url: String, dest: File, total: Long, onProgress: (Progress) -> Unit) {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            dest.outputStream().use { out ->
                val buf = ByteArray(CHUNK)
                var done = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = body.byteStream().read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(Progress(done, total))
                }
            }
        }
    }

    /** Parallel ranged download, one RandomAccessFile handle per part. */
    private suspend fun parallel(
        url: String,
        dest: File,
        total: Long,
        threads: Int,
        onProgress: (Progress) -> Unit,
    ) = coroutineScope {
        val done = AtomicLong(0L)
        val partSize = (total + threads - 1) / threads
        val jobs = (0 until threads).map { index ->
            async {
                val start = index * partSize
                val end = minOf(start + partSize - 1, total - 1)
                if (start > end) return@async
                // How many bytes of [start..end] are already on disk — drives
                // the one automatic retry of the missing tail.
                val partDone = AtomicLong(0L)
                var attempt = 0
                while (true) {
                    try {
                        downloadPart(url, dest, start + partDone.get(), end, partDone) { n ->
                            done.addAndGet(n.toLong())
                            onProgress(Progress(done.get(), total))
                        }
                        return@async
                    } catch (t: Throwable) {
                        coroutineContext.ensureActive() // real cancellation rethrows
                        attempt++
                        // one automatic retry resuming at partDone
                        if (attempt > 1) throw t
                    }
                }
            }
        }
        jobs.forEach { it.await() }
    }

    /**
     * Streams the byte range [from]..[end] into its slot in [dest].
     * [partDone] is kept up to date so a retry can resume mid-range; the
     * [onChunk] callback receives chunk lengths for progress accounting.
     * Throws on short reads / network errors (retryable by the caller).
     */
    private suspend fun downloadPart(
        url: String,
        dest: File,
        from: Long,
        end: Long,
        partDone: AtomicLong,
        onChunk: (Int) -> Unit,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$from-$end")
            .build()
        val expected = end - from + 1
        var transferred = 0L
        http.newCall(request).execute().use { resp ->
            // 206 for ranges; 200 means the server ignored Range — only
            // acceptable when this is the very first byte of the file.
            if (resp.code != 206 && !(resp.code == 200 && from == 0L)) {
                throw IOException("HTTP ${resp.code} for range $from-$end")
            }
            val body = resp.body ?: throw IOException("empty body")
            RandomAccessFile(dest, "rw").use { raf ->
                raf.seek(from + transferred)
                val buf = ByteArray(CHUNK)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = body.byteStream().read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    transferred += n
                    partDone.set(transferred)
                    onChunk(n)
                }
            }
        }
        if (transferred < expected) {
            throw IOException("short read: $transferred of $expected bytes")
        }
    }
}
