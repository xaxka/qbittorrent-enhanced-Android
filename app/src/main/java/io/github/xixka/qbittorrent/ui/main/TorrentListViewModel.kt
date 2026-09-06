package io.github.xixka.qbittorrent.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.xixka.qbittorrent.api.QBAuthException
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.qbt.LocalEngineManager
import io.github.xixka.qbittorrent.model.EngineScale
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TransferInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Home screen state, modeled after LibreTorrent's TorrentListViewModel:
 * drawer chips (status / category / tag), sorting with direction,
 * search query and live transfer stats.
 */
data class ListUiState(
    val loading: Boolean = false,
    val connected: Boolean = false,
    val configured: Boolean = true,
    val authError: Boolean = false,
    val error: String? = null,
    val serverVersion: String = "",
    val torrents: List<TorrentInfo> = emptyList(),
    val allCount: Int = 0,
    val transfer: TransferInfo? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** Resident memory (bytes) of the bundled engine, null when not running. */
    val engineRss: Long? = null,
    /** Free space (bytes) on the torrent save path, null when unknown. */
    val freeSpace: Long? = null,
    /** local-engine states (Enhanced): engine running / failed / starting. */
    val engineRunning: Boolean = false,
    val engineFailed: Boolean = false,
)

/**
 * The status filter set of the qBittorrent WebUI sidebar, with exactly
 * the server-side semantics (qb-enhanced src/base/torrentfilter.cpp +
 * torrentimpl.cpp): `downloading` includes stopped-downloading torrents
 * AND metadata-fetching ones, `completed` is the seeding state set,
 * `resumed` is anything not stopped, `active`/`inactive` are based on
 * current transfer speeds. Matches the engine's own WebUI sidebar
 * (views/filters.html) filter-for-filter — the engine offers neither a
 * standalone "downloading metadata" filter (metadl is counted under
 * `downloading`) nor any date-added window filters, so neither exists
 * here either.
 */
enum class StatusFilter(val states: Set<String>? = null, val speedBased: Int = 0) {
    ALL(null),
    DOWNLOADING(
        setOf(
            "downloading", "metadl", "forcedmetadl", "stalleddl", "checkingdl",
            "stoppeddl", "pauseddl", "queueddl", "forceddl",
        )
    ),
    SEEDING(setOf("uploading", "stalledup", "checkingup", "queuedup", "forcedup")),
    COMPLETED(
        setOf(
            "uploading", "stalledup", "checkingup", "stoppedup", "pausedup",
            "queuedup", "forcedup",
        )
    ),
    RESUMED,
    PAUSED(setOf("stoppeddl", "stoppedup", "pauseddl", "pausedup")),
    ACTIVE(null, speedBased = 1),
    INACTIVE(null, speedBased = -1),
    STALLED(setOf("stalleddl", "stalledup")),
    CHECKING(setOf("checkingup", "checkingdl", "checkingresumedata")),
    MOVING(setOf("moving")),
    ERROR(setOf("error", "missingfiles")),
}

enum class SortField {
    DATE_ADDED, NAME, SIZE, PROGRESS, ETA, PEERS, RATIO,
    DL_SPEED, UP_SPEED, UPLOADED, COMPLETION_DATE,
}

/** State sets shared by [StatusFilter.RESUMED]. */
private val STOPPED_STATES =
    setOf("stoppeddl", "stoppedup", "pauseddl", "pausedup")

private const val RUNNING = "RUNNING"
private const val FAILED = "FAILED"

class TorrentListViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ServiceLocator.repository(app)
    private val prefs = ServiceLocator.prefs(app)

    /** True once any fetch has ever succeeded (switches off fast retries). */
    @Volatile
    private var everConnected = false

    private val _state = MutableStateFlow(ListUiState())
    val state: StateFlow<ListUiState> = _state

    // drawer filters
    var statusFilter = StatusFilter.ALL
        private set
    var category: String? = null
        private set
    var tag: String? = null
        private set
    var sortField = SortField.DATE_ADDED
        private set
    var sortDescending = false
        private set
    var searchQuery: String = ""
        private set

    private var pollJob: Job? = null

    /**
     * Foreground gate for the poll loop: the activity flips this on
     * STARTED / off on STOP, so the list never generates network traffic
     * while the app is in the background.
     */
    private val pollActive = MutableStateFlow(false)

    fun setPollingActive(active: Boolean) {
        pollActive.value = active
    }

    init {
        // Fast start: no torrent-list snapshot is persisted — the list shown
        // is always live data. Startup speed comes from the engine booting in
        // parallel with the UI (QBApp cold-start fast path) plus the fast
        // 300 ms retry loop below, which puts the list on screen the moment
        // the WebUI answers.
        deleteLegacyCacheFile()
        restart()
    }

    /**
     * Removes the torrent-list snapshot written by older app versions.
     * The list is never cached anymore; deleting the stale file keeps
     * filesDir clean and guarantees no old data can ever resurface.
     */
    private fun deleteLegacyCacheFile() {
        runCatching { File(getApplication<Application>().filesDir, LEGACY_CACHE_FILE_NAME).delete() }
    }

    fun restart() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            var first = true
            while (isActive) {
                // hold traffic while the app is in the background; resumes
                // (and refreshes immediately) the moment it comes back
                pollActive.first { it }
                // blocking spinner only when nothing is on screen yet — an
                // already-rendered list stays visible while it refreshes
                val ok = refreshOnce(showLoading = first && _state.value.torrents.isEmpty())
                first = false
                // While the first connection has not succeeded yet (engine
                // still booting), retry every 300 ms instead of waiting a
                // full poll interval — the list appears the moment the
                // WebUI answers. Windowed to 15 s so a permanently dead
                // remote server is never hammered.
                val interval = if (!ok && !everConnected &&
                    System.currentTimeMillis() - startedAt < 15_000
                ) {
                    300L
                } else {
                    prefs.pollIntervalSec.coerceIn(1, 60) * 1000L
                }
                delay(interval)
            }
        }
    }

    /** Explicit (user-initiated) refresh: allowed to show the loading view. */
    fun refresh() {
        viewModelScope.launch { refreshOnce(showLoading = _state.value.torrents.isEmpty()) }
    }

    private suspend fun refreshOnce(showLoading: Boolean = false): Boolean {
        if (!prefs.serverConfig().isConfigured) {
            _state.update { it.copy(configured = false, connected = false, loading = false) }
            return false
        }
        // The full-screen loading indicator only appears while there is
        // nothing to show yet (initial load / manual refresh on an empty
        // list). Background polls update the data silently — a spinner
        // flashing over the list every few seconds looks broken.
        if (showLoading) {
            _state.update { it.copy(loading = true, configured = true) }
        } else {
            _state.update { it.copy(configured = true) }
        }
        try {
            val torrents = repository.torrents(filter = null)
            val transfer = runCatching { repository.transferInfo() }.getOrNull()
                // fallback source: the same counters also ride on
                // sync/maindata's server_state — used when /transfer/info
                // answered an error so the drawer stats never blank out
                ?: runCatching { repository.serverState() }.getOrNull()?.let {
                    TransferInfo(
                        connectionStatus = "",
                        dhtNodes = 0L,
                        dlInfoData = it.downloadSession,
                        dlInfoSpeed = it.downloadSpeed,
                        upInfoData = it.uploadSession,
                        upInfoSpeed = it.uploadSpeed,
                    )
                }
            // Engine RSS for the drawer's listening-port row; /proc reads are
            // tiny but belong off the main thread
            val engineRss = if (prefs.usingLocalEngine) {
                withContext(Dispatchers.IO) {
                    runCatching { LocalEngineManager.engineRssBytes(getApplication<Application>()) }.getOrNull()
                }
            } else null
            // Free space on the torrent save path for the drawer's DHT row:
            // local engine = stat() on the folder, remote = sync/maindata's
            // server_state.free_space_on_disk
            val freeSpace = if (prefs.usingLocalEngine) {
                withContext(Dispatchers.IO) {
                    runCatching { File(prefs.engineSavePath).usableSpace }.getOrNull()?.takeIf { it > 0 }
                }
            } else {
                runCatching { repository.serverState() }.getOrNull()
                    ?.freeSpace?.takeIf { it > 0 }
            }
            val categories = runCatching {
                repository.categories().keys.filter { it.isNotBlank() }.sorted()
            }.getOrDefault(emptyList())
            val tagList = runCatching { repository.tags() }.getOrDefault(emptyList())
            val version = runCatching { repository.appVersion() }.getOrDefault("")
            // qB 5.1+ switched file/peer progress from 0..1 to 0..100
            if (version.isNotBlank()) EngineScale.progressIsPercent = isProgressPercentEngine(version)
            _state.update {
                it.copy(
                    loading = false,
                    connected = true,
                    authError = false,
                    error = null,
                    serverVersion = version,
                    torrents = filtered(torrents),
                    allCount = torrents.size,
                    transfer = transfer,
                    engineRss = engineRss,
                    freeSpace = freeSpace,
                    categories = categories,
                    tags = tagList,
                )
            }
            everConnected = true
            return true
        } catch (e: QBAuthException) {
            // local engine: while it is coming up, don't flag an auth error
            val engine = engineState()
            _state.update {
                it.copy(
                    loading = false,
                    connected = false,
                    authError = !prefs.usingLocalEngine || engine == FAILED,
                    engineRunning = engine == RUNNING,
                    engineFailed = engine == FAILED,
                    // LibreTorrent parity: stat rows zero out while offline
                    // instead of freezing at the last (stale) values
                    transfer = null,
                    freeSpace = null,
                )
            }
            return false
        } catch (e: Exception) {
            val engine = engineState()
            val msg = when (e) {
                is UnknownHostException -> "Unknown host"
                is ConnectException -> "Connection refused"
                is SocketTimeoutException -> "Timeout"
                else -> e.message ?: "error"
            }
            // For the local engine a connection refusal during startup is
            // expected: only surface it once the engine actually FAILED.
            _state.update {
                it.copy(
                    loading = false,
                    connected = false,
                    error = if (engine == RUNNING) msg else null,
                    engineRunning = engine == RUNNING,
                    engineFailed = engine == FAILED,
                    transfer = null,
                    freeSpace = null,
                )
            }
            return false
        }
    }

    private fun engineState(): String? =
        if (prefs.usingLocalEngine) LocalEngineManager.state.name else null

    /** qBittorrent 5.1+ reports file/peer progress on a 0..100 scale. */
    private fun isProgressPercentEngine(version: String): Boolean {
        val m = Regex("(\\d+)\\.(\\d+)").find(version.removePrefix("v").removePrefix("V")) ?: return false
        val major = m.groupValues[1].toLongOrNull() ?: return false
        val minor = m.groupValues[2].toLongOrNull() ?: return false
        return major > 5 || (major == 5L && minor >= 1)
    }

    fun setStatusFilter(filter: StatusFilter?) {
        statusFilter = filter ?: StatusFilter.ALL
        refilter()
    }

    fun setCategory(category: String?) {
        this.category = category
        refilter()
    }

    fun setTag(tag: String?) {
        this.tag = tag
        refilter()
    }

    fun setSortField(field: SortField) {
        sortField = field
        refilter()
    }

    fun setSortDirection(descending: Boolean) {
        sortDescending = descending
        refilter()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        refilter()
    }

    /** Resets the full drawer state: filters, sorting and search query. */
    fun resetFilters() {
        statusFilter = StatusFilter.ALL
        category = null
        tag = null
        sortField = SortField.DATE_ADDED
        sortDescending = false
        searchQuery = ""
        refilter()
    }

    private fun refilter() {
        _state.update { s -> s.copy(torrents = applyFilters()) }
    }

    // Full snapshot of the last refresh; drawer filters are applied on top.
    @Volatile
    private var lastAll: List<TorrentInfo> = emptyList()

    private fun filtered(source: List<TorrentInfo>): List<TorrentInfo> {
        lastAll = source
        return applyFilters(source)
    }

    private fun applyFilters(source: List<TorrentInfo> = lastAll): List<TorrentInfo> {
        var result = source.asSequence()

        val f = statusFilter
        when {
            // "All" keeps everything
            f == StatusFilter.ALL -> {}
            // Resumed: everything that is not stopped (Torrent::isRunning)
            f == StatusFilter.RESUMED ->
                result = result.filter { it.state.lowercase() !in STOPPED_STATES }
            // Active/Inactive: current transfer speeds (Torrent::isActive)
            f.speedBased == 1 -> result = result.filter { it.dlSpeed > 0 || it.upSpeed > 0 }
            f.speedBased == -1 -> result = result.filter { it.dlSpeed <= 0 && it.upSpeed <= 0 }
            // Explicit state sets with the server's own filter semantics
            f.states != null -> result = result.filter { it.state.lowercase() in f.states }
        }

        category?.let { cat ->
            result = if (cat.isEmpty()) {
                result.filter { it.category.isBlank() }
            } else {
                result.filter { it.category == cat }
            }
        }

        tag?.let { t ->
            result = if (t.isEmpty()) {
                result.filter { it.tags.isBlank() }
            } else {
                result.filter { t in it.tags.split(',').map { x -> x.trim() } }
            }
        }

        if (searchQuery.isNotBlank()) {
            result = result.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }

        val base: java.util.Comparator<TorrentInfo> = when (sortField) {
            SortField.DATE_ADDED -> compareBy { it.addedOn }
            SortField.NAME -> compareBy { it.name.lowercase() }
            SortField.SIZE -> compareBy { it.size }
            SortField.PROGRESS -> compareBy { it.progress }
            SortField.ETA -> compareBy { it.eta }
            SortField.PEERS -> compareBy { it.numLeechsTotal + it.numSeedsTotal }
            SortField.RATIO -> compareBy { it.ratio }
            SortField.DL_SPEED -> compareBy { it.dlSpeed }
            SortField.UP_SPEED -> compareBy { it.upSpeed }
            SortField.UPLOADED -> compareBy { it.uploaded }
            SortField.COMPLETION_DATE -> compareBy { it.completionOn }
        }
        // TOTAL order, deterministic regardless of the server's array
        // order: the server serializes its torrent map in QHash order,
        // which reshuffles whenever the key set changes. A partial
        // comparator leaves items that compare EQUAL on the primary key
        // (same addedOn, same progress, …) in that ever-changing source
        // order — so every 1 s poll re-shuffled them and the list visibly
        // jumped around, right after tapping a sort chip. Chaining name +
        // hash tiebreakers pins equal-key items to a stable position:
        // the same data now always sorts to the same list.
        val cmp = base
            .thenBy { it.name.lowercase() }
            .thenBy { it.hash }
        val sorted = if (sortDescending) result.sortedWith(cmp.reversed()) else result.sortedWith(cmp)
        return sorted.toList()
    }

    fun pauseAll() = viewModelScope.launch { runCatching { repository.pauseAll() } }
    fun resumeAll() = viewModelScope.launch { runCatching { repository.resumeAll() } }

    companion object {
        /** Snapshot file of older app versions, deleted on startup. */
        private const val LEGACY_CACHE_FILE_NAME = "torrent_list_cache.json"

        fun factory(app: Application): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return TorrentListViewModel(app) as T
                }
            }
    }
}
