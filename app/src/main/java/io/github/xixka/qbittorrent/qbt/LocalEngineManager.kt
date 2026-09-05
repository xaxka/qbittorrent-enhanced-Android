package io.github.xixka.qbittorrent.qbt

import android.content.Context
import io.github.xixka.qbittorrent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Lifecycle manager for the bundled qbittorrent-enhanced-nox engine
 * (bionic dynamic-linked build produced by the OpenListAndroid project).
 *
 * The binary ships as `libqbittorrent-nox.so` inside the APK's jniLibs so that
 * the Android package installer extracts it into the executable
 * `nativeLibraryDir`. It is started as a child process via ProcessBuilder with
 * `LD_LIBRARY_PATH` pointing at the same directory, a private profile dir that
 * holds `qBittorrent.conf`, and the system CA bundle exported to a PEM file.
 *
 * Only the Enhanced edition bundles the binary; the standard edition is a pure
 * remote-control client.
 */
object LocalEngineManager {

    enum class State { STOPPED, STARTING, RUNNING, FAILED }

    @Volatile
    var state: State = State.STOPPED
        private set

    @Volatile
    var lastError: String? = null
        private set

    private val processRef = AtomicReference<Process?>(null)
    private val running = AtomicBoolean(false)

    val logLines: ArrayDeque<String> = ArrayDeque()

    fun isSupported(context: Context): Boolean =
        BuildConfig.IS_ENHANCED && binaryFile(context)?.isFile == true

    fun binaryFile(context: Context): File? =
        context.applicationInfo?.nativeLibraryDir
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it, NoxConfig.BINARY_LIB_NAME) }

    fun isRunning(): Boolean = processRef.get()?.isAlive == true

    /**
     * Starts the engine and waits until the WebUI answers, then returns the
     * endpoint (127.0.0.1:<port>).
     */
    suspend fun start(context: Context, port: Int, lanAccess: Boolean, savePath: String): String =
        withContext(Dispatchers.IO) {
            val ctx = context.applicationContext
            if (!isSupported(ctx)) {
                throw IllegalStateException("Local engine is not available in this build")
            }
            stopInternal()

            state = State.STARTING
            lastError = null
            appendLog("starting qbittorrent-enhanced-nox on port $port")

            val effectiveSave = savePath.ifBlank {
                io.github.xixka.qbittorrent.data.Prefs.defaultEngineSavePath()
            }
            // The engine writes through the app's storage view: make sure the
            // target folder exists (and is writable) before the child starts.
            // A failure here means the all-files permission has not been
            // granted yet — log it and keep going; the UI prompts for access
            // and the engine retries on its next (re)start.
            runCatching { File(effectiveSave).mkdirs() }
                .onFailure { appendLog("warning: could not create save path $effectiveSave (storage permission?)") }
            // The engine's WebUI credentials are app-managed: seed the exact
            // username/password the app itself logs in with so the config
            // file can never drift away from the app + LAN browser login.
            val enginePrefs = io.github.xixka.qbittorrent.data.ServiceLocator.prefs(ctx)
            NoxConfig.seed(
                ctx, port, lanAccess, effectiveSave,
                username = enginePrefs.engineUsername,
                password = enginePrefs.enginePassword,
            )

            val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)
            val profileDir = NoxConfig.profileDir(ctx)
            val tmpDir = File(ctx.cacheDir, "nox-tmp").apply { mkdirs() }
            val caBundle = exportCaBundle(ctx)

            val pb = ProcessBuilder(
                binaryFile(ctx)!!.absolutePath,
                "--confirm-legal-notice",
                "--profile=${profileDir.absolutePath}",
                "--webui-port=$port",
            ).apply {
                redirectErrorStream(true)
                environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
                environment()["HOME"] = profileDir.absolutePath
                environment()["TMPDIR"] = tmpDir.absolutePath
                environment()["TEMP"] = tmpDir.absolutePath
                environment()["TMP"] = tmpDir.absolutePath
                environment()["TZ"] = "UTC"
                if (caBundle != null) environment()["SSL_CERT_FILE"] = caBundle.absolutePath
            }
            val proc = pb.start()
            processRef.set(proc)
            running.set(true)

            // pump stdout so the child never blocks on a full pipe
            Thread {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                        lines.forEach { line -> appendLog(line.take(500)) }
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; name = "nox-log-pump" }.start()

            // wait for the WebUI to become ready — probe at 150 ms so the
            // moment the listener binds is caught almost immediately
            // (qbittorrent-nox answers /app/version only after the session
            // is up; the app-side wait is dominated by the engine itself)
            val deadline = System.currentTimeMillis() + 25_000
            var ready = false
            while (System.currentTimeMillis() < deadline) {
                if (!proc.isAlive) break
                if (probeVersion(port) != null) {
                    ready = true
                    break
                }
                Thread.sleep(150)
            }
            if (ready) {
                state = State.RUNNING
                appendLog("engine ready: http://127.0.0.1:$port")
                "127.0.0.1:$port"
            } else {
                state = State.FAILED
                lastError = if (!proc.isAlive) {
                    "engine exited with code ${proc.exitValue()}"
                } else {
                    "engine did not become ready within timeout"
                }
                appendLog("engine start failed: $lastError")
                stopInternal()
                throw IllegalStateException(lastError)
            }
        }

    fun stop() {
        state = State.STOPPED
        stopInternal()
        appendLog("engine stopped")
    }

    private fun stopInternal() {
        running.set(false)
        processRef.getAndSet(null)?.let { proc ->
            runCatching { proc.destroy() }
            runCatching {
                // give it a moment before the forceful kill
                val waitThread = Thread { runCatching { proc.waitFor() } }
                waitThread.isDaemon = true
                waitThread.start()
                waitThread.join(1500)
                if (proc.isAlive) proc.destroyForcibly()
            }
        }
    }

    private fun probeVersion(port: Int): String? = runCatching {
        val conn = URL("http://127.0.0.1:$port/api/v2/app/version").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText().trim()
            } else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Exports the Android system trust store to a PEM bundle for the bare process. */
    private fun exportCaBundle(context: Context): File? = runCatching {
        val out = File(context.cacheDir, "nox-ca-bundle.pem")
        val ks = KeyStore.getInstance("AndroidCAStore")
        ks.load(null)
        val sb = StringBuilder()
        val aliases = ks.aliases()
        while (aliases.hasMoreElements()) {
            val cert: Certificate = ks.getCertificate(aliases.nextElement()) ?: continue
            sb.append("-----BEGIN CERTIFICATE-----\n")
            sb.append(java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(cert.encoded))
            sb.append("\n-----END CERTIFICATE-----\n")
        }
        out.writeText(sb.toString())
        out
    }.getOrNull()

    @Synchronized
    private fun appendLog(line: String) {
        logLines.addLast(line)
        while (logLines.size > 200) logLines.removeFirst()
    }
}
