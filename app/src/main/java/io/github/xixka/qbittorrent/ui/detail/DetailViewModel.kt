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
    val info: TorrentInfo? = null,
    val properties: TorrentProperties? = null,
    val files: List<TorrentFile> = emptyList(),
    val trackers: List<Tracker> = emptyList(),
    val peers: List<Peer> = emptyList(),
    val activeTab: Int = 0,
)

/**
 * Detail screen state: polls the torrent overview plus the data of the
 * currently visible tab (files / trackers / peers).
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
                    val props = repository.properties(hash)
                    val info = runCatching {
                        repository.torrents().firstOrNull { it.hash == hash }
                    }.getOrNull() ?: _state.value.info
                    val cats = runCatching { repository.categories().values.toList() }
                        .getOrDefault(_state.value.categories)
                    val tab = _state.value.activeTab
                    val (files, trackers, peers) = when (tab) {
                        FILES_TAB -> Triple(repository.files(hash), _state.value.trackers, emptyList())
                        TRACKERS_TAB -> Triple(_state.value.files, repository.trackers(hash), emptyList())
                        PEERS_TAB -> Triple(_state.value.files, _state.value.trackers, repository.peers(hash))
                        else -> Triple(emptyList(), emptyList(), emptyList())
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            info = info,
                            categories = cats,
                            properties = props,
                            files = files,
                            trackers = trackers,
                            peers = peers,
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

    fun addTracker(url: String) = launchAction { repository.addTrackers(hash, url) }
    fun removeTracker(url: String) = launchAction { repository.removeTrackers(hash, url) }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    companion object {
        const val FILES_TAB = 1
        const val TRACKERS_TAB = 2
        const val PEERS_TAB = 3

        fun factory(app: Application, hash: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(app, hash) as T
                }
            }
    }
}
