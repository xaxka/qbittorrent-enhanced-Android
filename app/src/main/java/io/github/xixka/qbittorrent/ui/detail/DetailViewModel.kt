package io.github.xixka.qbittorrent.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.model.TorrentFile
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.model.Tracker
import io.github.xixka.qbittorrent.model.WebSeed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val categories: List<QBCategory> = emptyList(),
    val tags: List<String> = emptyList(),
    val info: TorrentInfo? = null,
    val properties: TorrentProperties? = null,
    val files: List<TorrentFile> = emptyList(),
    val trackers: List<Tracker> = emptyList(),
    val peers: List<Peer> = emptyList(),
    val pieceStates: List<Int> = emptyList(),
    val webSeeds: List<WebSeed> = emptyList(),
    val activeTab: Int = 0,
)

/**
 * Detail screen state: polls the torrent overview plus the data of the
 * currently visible tab (files / trackers / peers / pieces).
 */
class DetailViewModel(app: Application, private val initialHash: String) :
    AndroidViewModel(app) {

    private val repository = ServiceLocator.repository(app)

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    val hash: String get() = initialHash

    private var pollJob: Job? = null

    init {
        restart()
    }

    fun setTab(index: Int) {
        _state.update { it.copy(activeTab = index) }
    }

    fun restart() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val props = runCatching { repository.properties(hash) }
                        .getOrNull() ?: _state.value.properties
                    val info = runCatching {
                        repository.torrents().firstOrNull { it.hash == hash }
                    }.getOrNull() ?: _state.value.info
                    val cats = runCatching { repository.categories().values.toList() }
                        .getOrDefault(_state.value.categories)
                    val tagList = runCatching { repository.tags() }
                        .getOrDefault(_state.value.tags)
                    val tab = _state.value.activeTab
                    // Only the visible tab's data is refreshed; the other
                    // tabs keep their last values (empty while never visited).
                    val (files, trackers, peers, pieces, webSeeds) = when (tab) {
                        FILES_TAB -> Quint(
                            repository.files(hash),
                            _state.value.trackers,
                            emptyList(),
                            _state.value.pieceStates,
                            _state.value.webSeeds,
                        )
                        TRACKERS_TAB -> Quint(
                            _state.value.files,
                            repository.trackers(hash),
                            emptyList(),
                            _state.value.pieceStates,
                            _state.value.webSeeds,
                        )
                        PEERS_TAB -> Quint(
                            _state.value.files,
                            _state.value.trackers,
                            repository.peers(hash),
                            _state.value.pieceStates,
                            _state.value.webSeeds,
                        )
                        PIECES_TAB -> Quint(
                            _state.value.files,
                            _state.value.trackers,
                            _state.value.peers,
                            repository.pieceStates(hash),
                            _state.value.webSeeds,
                        )
                        WEBSEEDS_TAB -> Quint(
                            _state.value.files,
                            _state.value.trackers,
                            _state.value.peers,
                            _state.value.pieceStates,
                            repository.webSeeds(hash),
                        )
                        else -> Quint(
                            emptyList(),
                            emptyList(),
                            emptyList(),
                            _state.value.pieceStates,
                            _state.value.webSeeds,
                        )
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            info = info,
                            categories = cats,
                            tags = tagList,
                            properties = props,
                            files = files,
                            trackers = trackers,
                            peers = peers,
                            pieceStates = pieces,
                            webSeeds = webSeeds,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(loading = false, error = e.message ?: "error") }
                }
                delay(3000)
            }
        }
    }

    // ---------- actions forwarded to the repository ----------

    fun pause() = launchAction { repository.pause(listOf(hash)) }
    fun resume() = launchAction { repository.resume(listOf(hash)) }
    fun recheck() = launchAction { repository.recheck(listOf(hash)) }
    fun reannounce() = launchAction { repository.reannounce(listOf(hash)) }
    fun toggleSequential() = launchAction { repository.toggleSequential(listOf(hash)) }
    fun toggleSuperSeeding() = launchAction { repository.toggleSuperSeeding(listOf(hash)) }
    fun toggleFirstLast() = launchAction { repository.toggleFirstLastPiecePriority(listOf(hash)) }
    fun setCategory(name: String) = launchAction { repository.setCategory(listOf(hash), name) }

    /** Rename the torrent (qBittorrent rename dialog). */
    fun rename(name: String) = launchAction {
        repository.renameTorrent(hash, name)
        _state.update { s ->
            val info = s.info
            if (info != null) s.copy(info = info.copy(name = name)) else s
        }
    }

    /** Rename one file inside the torrent (qBitController parity). */
    fun renameFile(fileId: Int, name: String) = launchAction {
        repository.renameFile(hash, fileId, name)
        _state.update { s ->
            s.copy(files = s.files.map { f -> if (f.index == fileId) f.copy(name = name) else f })
        }
    }

    /** Move the torrent to another save location. */
    fun setLocation(location: String) = launchAction {
        repository.setLocation(listOf(hash), location)
        _state.update { s ->
            val props = s.properties
            if (props != null) s.copy(properties = props.copy(savePath = location)) else s
        }
    }

    /** Per-torrent download limit, bytes/s (0 = unlimited). */
    fun setDownloadLimit(bytesPerSec: Long) = launchAction {
        repository.setTorrentDownloadLimit(listOf(hash), bytesPerSec)
    }

    /** Per-torrent upload limit, bytes/s (0 = unlimited). */
    fun setUploadLimit(bytesPerSec: Long) = launchAction {
        repository.setTorrentUploadLimit(listOf(hash), bytesPerSec)
    }

    /**
     * Share limits: ratio, seeding time (minutes), inactive seeding time
     * (minutes) plus the action taken when a limit is reached (engine enum
     * name, required by qBittorrent 5.x). [onError] runs when the server
     * rejects the request so the dialog can surface the failure.
     */
    fun setShareLimits(
        ratioLimit: Double,
        seedingTimeLimit: Int,
        inactiveSeedingTimeLimit: Int,
        shareLimitAction: String,
        onError: (Exception) -> Unit = {},
    ) = viewModelScope.launch {
        try {
            repository.setShareLimits(
                listOf(hash), ratioLimit, seedingTimeLimit,
                inactiveSeedingTimeLimit, shareLimitAction,
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    /** Direct super-seeding switch (shows the real state, unlike the toggle). */
    fun setSuperSeeding(value: Boolean) = launchAction {
        repository.setSuperSeeding(listOf(hash), value)
    }

    fun addTags(tags: List<String>) = launchAction { repository.addTags(listOf(hash), tags) }

    fun removeTag(tag: String) = launchAction { repository.removeTags(listOf(hash), listOf(tag)) }

    fun addTracker(url: String) = launchAction { repository.addTrackers(hash, url) }
    fun removeTracker(url: String) = launchAction { repository.removeTrackers(hash, url) }

    /** Replace a tracker URL (origUrl -> newUrl). */
    fun editTracker(origUrl: String, newUrl: String) = launchAction {
        repository.editTracker(hash, origUrl, newUrl)
    }

    // ---------- web seeds (qBC TorrentWebSeedsTab parity) ----------

    fun addWebSeeds(urls: String) = launchAction {
        repository.addWebSeeds(hash, urls)
    }

    fun editWebSeed(origUrl: String, newUrl: String) = launchAction {
        repository.editWebSeed(hash, origUrl, newUrl)
    }

    fun removeWebSeeds(urls: List<String>) = launchAction {
        repository.removeWebSeeds(hash, urls.joinToString("|"))
    }

    suspend fun categories(): Map<String, io.github.xixka.qbittorrent.model.QBCategory> =
        runCatching { repository.categories() }.getOrDefault(emptyMap())

    fun delete(deleteFiles: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        runCatching { repository.delete(listOf(hash), deleteFiles) }
        onDone()
    }

    fun setFilePriority(indexes: List<Int>, priority: Int) = launchAction {
        repository.setFilePriority(hash, indexes, priority)
        _state.update { s ->
            s.copy(files = s.files.map { f ->
                if (f.index in indexes) f.copy(priority = priority) else f
            })
        }
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    /** Local quintuple to keep the tab polling readable. */
    private data class Quint(
        val files: List<TorrentFile>,
        val trackers: List<Tracker>,
        val peers: List<Peer>,
        val pieces: List<Int>,
        val webSeeds: List<WebSeed>,
    )

    companion object {
        const val FILES_TAB = 1
        const val TRACKERS_TAB = 2
        const val PEERS_TAB = 3
        const val PIECES_TAB = 4
        const val WEBSEEDS_TAB = 5

        fun factory(app: Application, hash: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(app, hash) as T
                }
            }
    }
}
