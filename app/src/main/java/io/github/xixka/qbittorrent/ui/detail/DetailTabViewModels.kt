package io.github.xixka.qbittorrent.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.model.TorrentFileNode
import io.github.xixka.qbittorrent.model.TorrentInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Events surfaced as snackbars on the detail screen, qBC Torrent*ViewModel
 * Event parity (message strings instead of sealed per-action classes).
 */
sealed interface DetailEvent {
    data class Error(val message: String) : DetailEvent
    data class Message(val res: Int) : DetailEvent
}

/** Typed combine() payload of the tab poll gate. */
private data class PollGate(
    val loading: Boolean?,
    val active: Boolean,
    val refreshing: Boolean,
    val selecting: Boolean,
)

/**
 * One ViewModel per detail tab, qBC architecture parity:
 *  - data is loaded IMMEDIATELY in init (the pager pre-creates every tab,
 *    so files/peers are already there when the user swipes to them);
 *  - the auto-refresh loop polls ONLY while the tab is the visible page
 *    AND the host is started (isScreenActive), waiting one poll interval
 *    after each completed load — never queueing requests;
 *  - switching tabs never wipes another tab's data.
 */
abstract class DetailTabViewModel(app: Application, val hash: String) :
    AndroidViewModel(app) {

    protected val repository = ServiceLocator.repository(app)
    private val prefs = ServiceLocator.prefs(app)

    private val _isScreenActive = MutableStateFlow(false)

    /** True while the tab's list has an action-mode selection: qBC pauses
     *  auto-refresh during selection so rows never repaint under the user's
     *  finger. */
    private val _isSelectionActive = MutableStateFlow(false)

    /** null = idle, true = first (blocking) load, false = background refresh. */
    private val _isNaturalLoading = MutableStateFlow<Boolean?>(null)
    val isNaturalLoading: StateFlow<Boolean?> = _isNaturalLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val eventChannel = Channel<DetailEvent>(Channel.BUFFERED)
    val eventFlow = eventChannel.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Re-arms after every state change (load completes, manual
            // refresh completes, tab becomes visible): the next poll always
            // waits one interval AFTER the last finished request — requests
            // never queue up. Watching isRefreshing too avoids a stall when
            // an in-flight pull-to-refresh makes load() a no-op.
            combine(
                _isNaturalLoading,
                _isScreenActive,
                _isRefreshing,
                _isSelectionActive,
            ) { loading, active, refreshing, selecting ->
                PollGate(loading, active, refreshing, selecting)
            }.collectLatest { gate ->
                if (gate.active && !gate.selecting && gate.loading == null) {
                    delay(prefs.pollIntervalSec * 1000L)
                    load(autoRefresh = true)
                }
            }
        }
    }

    fun setScreenActive(active: Boolean) {
        _isScreenActive.value = active
    }

    /** Selection mode (files/trackers/peers): pauses the auto-refresh loop. */
    fun setSelectionActive(active: Boolean) {
        _isSelectionActive.value = active
    }

    /** Pull-to-refresh: always allowed, shows the spinner. */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        loadJob = performLoad()
        loadJob?.invokeOnCompletion { _isRefreshing.value = false }
    }

    protected fun load(autoRefresh: Boolean = false) {
        if (loadJob?.isActive == true) return
        _isNaturalLoading.value = !autoRefresh
        loadJob = performLoad()
        loadJob?.invokeOnCompletion { _isNaturalLoading.value = null }
    }

    /** qBC parity: rerun after an action, one second later. */
    protected fun reloadAfterAction() {
        viewModelScope.launch {
            delay(1000)
            load()
        }
    }

    protected fun sendEvent(event: DetailEvent) {
        eventChannel.trySend(event)
    }

    /** Runs one round of network requests and updates the state flows. */
    protected abstract fun performLoad(): Job
}

/**
 * Overview tab state: the torrent itself + properties + piece states,
 * fetched in parallel every poll tick (qBC TorrentOverviewViewModel).
 */
class DetailOverviewViewModel(app: Application, hash: String) :
    DetailTabViewModel(app, hash) {

    private val _torrent = MutableStateFlow<TorrentInfo?>(null)
    val torrent: StateFlow<TorrentInfo?> = _torrent.asStateFlow()

    private val _properties = MutableStateFlow<io.github.xixka.qbittorrent.model.TorrentProperties?>(null)
    val properties: StateFlow<io.github.xixka.qbittorrent.model.TorrentProperties?> = _properties.asStateFlow()

    private val _pieces = MutableStateFlow<List<Int>>(emptyList())
    val pieces: StateFlow<List<Int>> = _pieces.asStateFlow()

    /** Server category/tag lists for the pick dialogs, loaded on demand. */
    private val _categories = MutableStateFlow<List<Pair<String, String>>?>(null)
    val categories: StateFlow<List<Pair<String, String>>?> = _categories.asStateFlow()

    private val _tags = MutableStateFlow<List<String>?>(null)
    val tags: StateFlow<List<String>?> = _tags.asStateFlow()

    override fun performLoad(): Job = viewModelScope.launch {
        val torrentDeferred = async { runCatching { repository.torrent(hash) } }
        val propertiesDeferred = async { runCatching { repository.properties(hash) } }
        val piecesDeferred = async { runCatching { repository.pieceStates(hash) } }
        awaitAll(torrentDeferred, propertiesDeferred, piecesDeferred)

        // Success with no row = the torrent was deleted elsewhere: surface
        // it once. A network failure keeps the last data and retries.
        val torrentResult = torrentDeferred.getCompleted()
        if (torrentResult.isSuccess) {
            val torrent = torrentResult.getOrNull()
            if (torrent == null && _torrent.value == null) {
                sendEvent(DetailEvent.Message(R.string.torrent_error_not_found))
            } else if (torrent != null) {
                _torrent.value = torrent
            }
        }
        propertiesDeferred.getCompleted().getOrNull()?.let { _properties.value = it }
        val pieces = piecesDeferred.getCompleted().getOrNull()
        if (pieces != null && pieces.isNotEmpty()) _pieces.value = pieces
    }

    fun loadCategories() = viewModelScope.launch {
        runCatching { repository.categories() }
            .onSuccess { map ->
                _categories.value = map.values
                    .map { it.name to it.savePath }
                    .sortedBy { it.first.lowercase() }
            }
    }

    fun loadTags() = viewModelScope.launch {
        runCatching { repository.tags() }
            .onSuccess { _tags.value = it }
    }

    // ---------- actions (qBC TorrentOverviewViewModel parity) ----------

    fun pause() = action(R.string.torrent_paused_success) {
        repository.pause(listOf(hash))
        reloadAfterAction()
    }

    fun resume() = action(R.string.torrent_resumed_success) {
        repository.resume(listOf(hash))
        reloadAfterAction()
    }

    fun recheck() = action(R.string.torrent_recheck_success) {
        repository.recheck(listOf(hash))
        reloadAfterAction()
    }

    fun reannounce() = action(R.string.torrent_reannounce_success) {
        repository.reannounce(listOf(hash))
    }

    fun rename(name: String) = action(R.string.torrent_rename_success) {
        repository.renameTorrent(hash, name)
        _torrent.value = _torrent.value?.copy(name = name)
    }

    fun setLocation(path: String) = action(R.string.torrent_option_update_success) {
        repository.setLocation(listOf(hash), path)
        _properties.value = _properties.value?.copy(savePath = path)
        reloadAfterAction()
    }

    fun setSuperSeeding(value: Boolean) = action(
        if (value) R.string.torrent_enable_super_seeding_success
        else R.string.torrent_disable_super_seeding_success,
    ) {
        repository.setSuperSeeding(listOf(hash), value)
        reloadAfterAction()
    }

    fun setForceStart(value: Boolean) = action(
        if (value) R.string.torrent_enable_force_start_success
        else R.string.torrent_disable_force_start_success,
    ) {
        repository.setForceStart(listOf(hash), value)
        reloadAfterAction()
    }

    fun setCategory(category: String?) = action(R.string.torrent_category_update_success) {
        repository.setCategory(listOf(hash), category.orEmpty())
        reloadAfterAction()
    }

    fun setTags(newTags: List<String>) = viewModelScope.launch {
        val current = _torrent.value?.tags.orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val added = newTags.filter { it !in current }
        val removed = current.filter { it !in newTags }
        try {
            if (added.isNotEmpty()) repository.addTags(listOf(hash), added)
            if (removed.isNotEmpty()) repository.removeTags(listOf(hash), removed)
            sendEvent(DetailEvent.Message(R.string.torrent_tags_update_success))
            reloadAfterAction()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }

    fun setDownloadLimit(bytesPerSec: Long) = action(R.string.torrent_option_update_success) {
        repository.setTorrentDownloadLimit(listOf(hash), bytesPerSec)
    }

    fun setUploadLimit(bytesPerSec: Long) = action(R.string.torrent_option_update_success) {
        repository.setTorrentUploadLimit(listOf(hash), bytesPerSec)
    }

    /** qBC setTorrentOptions parity: automatic TMM switch. */
    fun setAutoTmm(value: Boolean) = action(R.string.torrent_option_update_success) {
        repository.setAutoManagement(listOf(hash), value)
        reloadAfterAction()
    }

    /** qBC setTorrentOptions parity: separate incomplete-torrent path. */
    fun setDownloadPath(path: String) = action(R.string.torrent_option_update_success) {
        repository.setDownloadPath(listOf(hash), path)
        reloadAfterAction()
    }

    fun toggleSequential() = action(R.string.torrent_option_update_success) {
        repository.toggleSequential(listOf(hash))
        reloadAfterAction()
    }

    fun toggleFirstLastPiece() = action(R.string.torrent_option_update_success) {
        repository.toggleFirstLastPiecePriority(listOf(hash))
        reloadAfterAction()
    }

    fun setShareLimits(
        ratioLimit: Double,
        seedingTimeLimit: Int,
        inactiveSeedingTimeLimit: Int,
        shareLimitAction: String,
    ) = action(R.string.torrent_option_update_success) {
        repository.setShareLimits(
            listOf(hash), ratioLimit, seedingTimeLimit,
            inactiveSeedingTimeLimit, shareLimitAction,
        )
        reloadAfterAction()
    }

    fun delete(deleteFiles: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        runCatching { repository.delete(listOf(hash), deleteFiles) }
        onDone()
    }

    /** Export the .torrent file; the caller saves the returned bytes. */
    fun export(onResult: (ByteArray?) -> Unit) = viewModelScope.launch {
        val bytes = try {
            repository.exportTorrent(hash)
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
            null
        }
        onResult(bytes)
    }

    private fun action(successRes: Int, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            sendEvent(DetailEvent.Message(successRes))
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }
}

/** File list sorting (files toolbar menu). ORDER = engine file order. */
enum class FilesSortMode { ORDER, NAME, SIZE, PROGRESS }

/**
 * Files tab: the torrent content as a qBC TorrentFileNode tree, with
 * priority / rename (file and folder) support.
 */
class DetailFilesViewModel(app: Application, hash: String) :
    DetailTabViewModel(app, hash) {

    private val _root = MutableStateFlow<TorrentFileNode.Folder?>(null)
    val root: StateFlow<TorrentFileNode.Folder?> = _root.asStateFlow()

    private val _sortMode = MutableStateFlow(FilesSortMode.ORDER)
    val sortMode: StateFlow<FilesSortMode> = _sortMode.asStateFlow()

    fun setSortMode(mode: FilesSortMode) {
        _sortMode.value = mode
    }

    override fun performLoad(): Job = viewModelScope.launch {
        runCatching { repository.files(hash) }
            .onSuccess { files ->
                // qBC: older API versions do not return the file index —
                // rebuild it from the list position.
                val indexed = if (files.size > 1 && files[1].index == 0) {
                    files.mapIndexed { i, f -> f.copy(index = i) }
                } else {
                    files
                }
                _root.value = TorrentFileNode.fromFileList(indexed)
            }
            .onFailure { sendEvent(DetailEvent.Error(it.message ?: "error")) }
    }

    fun setPriority(paths: List<String>, priority: Int) = viewModelScope.launch {
        val files = _root.value?.findAllFiles(paths).orEmpty()
        if (files.isEmpty()) return@launch
        try {
            repository.setFilePriority(hash, files.map { it.index }, priority)
            sendEvent(DetailEvent.Message(R.string.torrent_files_priority_update_success))
            load()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }

    fun renameFile(path: String, newName: String) = rename(path, newName, isFolder = false)

    fun renameFolder(path: String, newName: String) = rename(path, newName, isFolder = true)

    private fun rename(path: String, newName: String, isFolder: Boolean) = viewModelScope.launch {
        val separator = _root.value?.separator ?: "/"
        val newPath = if (path.contains(separator)) {
            path.substringBeforeLast(separator) + separator + newName
        } else {
            newName
        }
        try {
            if (isFolder) repository.renameFolder(hash, path, newPath)
            else repository.renameFile(hash, path, newPath)
            sendEvent(
                DetailEvent.Message(
                    if (isFolder) R.string.torrent_files_folder_renamed_success
                    else R.string.torrent_files_file_renamed_success,
                ),
            )
            reloadAfterAction()
        } catch (e: io.github.xixka.qbittorrent.api.QBApiException) {
            // 409 = path invalid or in use
            if (e.code == 409) {
                sendEvent(DetailEvent.Message(R.string.torrent_files_error_path_is_invalid_or_in_use))
            } else {
                sendEvent(DetailEvent.Error(e.message ?: "error"))
            }
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }
}

/** Trackers tab: list + add / edit / remove (qBC TorrentTrackersViewModel). */
class DetailTrackersViewModel(app: Application, hash: String) :
    DetailTabViewModel(app, hash) {

    private val _trackers = MutableStateFlow<List<io.github.xixka.qbittorrent.model.Tracker>?>(null)
    val trackers: StateFlow<List<io.github.xixka.qbittorrent.model.Tracker>?> = _trackers.asStateFlow()

    override fun performLoad(): Job = viewModelScope.launch {
        runCatching { repository.trackers(hash) }
            .onSuccess { _trackers.value = it }
            .onFailure { sendEvent(DetailEvent.Error(it.message ?: "error")) }
    }

    fun addTrackers(urls: String) = viewModelScope.launch {
        try {
            repository.addTrackers(hash, urls)
            sendEvent(DetailEvent.Message(R.string.torrent_trackers_added))
            load()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }

    fun editTracker(origUrl: String, newUrl: String) = viewModelScope.launch {
        try {
            repository.editTracker(hash, origUrl, newUrl)
            sendEvent(DetailEvent.Message(R.string.torrent_trackers_edited))
            reloadAfterAction()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }

    fun removeTrackers(urls: List<String>) = viewModelScope.launch {
        try {
            repository.removeTrackers(hash, urls.joinToString("|"))
            sendEvent(DetailEvent.Message(R.string.torrent_trackers_deleted))
            load()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }
}

/** Peers tab: live peer list + add / ban (qBC TorrentPeersViewModel). */
class DetailPeersViewModel(app: Application, hash: String) :
    DetailTabViewModel(app, hash) {

    private val _peers = MutableStateFlow<List<io.github.xixka.qbittorrent.model.Peer>?>(null)
    val peers: StateFlow<List<io.github.xixka.qbittorrent.model.Peer>?> = _peers.asStateFlow()

    override fun performLoad(): Job = viewModelScope.launch {
        runCatching { repository.peers(hash) }
            .onSuccess { _peers.value = it }
            .onFailure { sendEvent(DetailEvent.Error(it.message ?: "error")) }
    }

    fun addPeers(peers: List<String>) = viewModelScope.launch {
        try {
            repository.addPeers(hash, peers)
            sendEvent(DetailEvent.Message(R.string.torrent_peers_added))
            reloadAfterAction()
        } catch (e: io.github.xixka.qbittorrent.api.QBApiException) {
            if (e.code == 400) {
                sendEvent(DetailEvent.Message(R.string.torrent_peers_invalid))
            } else {
                sendEvent(DetailEvent.Error(e.message ?: "error"))
            }
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }

    fun banPeers(peers: List<String>) = viewModelScope.launch {
        try {
            repository.banPeers(peers)
            sendEvent(DetailEvent.Message(R.string.torrent_peers_banned))
            load()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }
}

/** Web seeds tab: list + add / edit / remove (qBC TorrentWebSeedsViewModel). */
class DetailWebSeedsViewModel(app: Application, hash: String) :
    DetailTabViewModel(app, hash) {

    private val _webSeeds = MutableStateFlow<List<io.github.xixka.qbittorrent.model.WebSeed>?>(null)
    val webSeeds: StateFlow<List<io.github.xixka.qbittorrent.model.WebSeed>?> = _webSeeds.asStateFlow()

    override fun performLoad(): Job = viewModelScope.launch {
        runCatching { repository.webSeeds(hash) }
            .onSuccess { _webSeeds.value = it }
            .onFailure { sendEvent(DetailEvent.Error(it.message ?: "error")) }
    }

    fun addWebSeeds(urls: String) = viewModelScope.launch {
        try {
            repository.addWebSeeds(hash, urls)
            sendEvent(DetailEvent.Message(R.string.torrent_web_seeds_added))
            load()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }

    fun editWebSeed(origUrl: String, newUrl: String) = viewModelScope.launch {
        try {
            repository.editWebSeed(hash, origUrl, newUrl)
            sendEvent(DetailEvent.Message(R.string.torrent_web_seeds_edited))
            reloadAfterAction()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }

    fun removeWebSeeds(urls: List<String>) = viewModelScope.launch {
        try {
            repository.removeWebSeeds(hash, urls.joinToString("|"))
            sendEvent(DetailEvent.Message(R.string.torrent_web_seeds_deleted))
            load()
        } catch (e: Exception) {
            sendEvent(DetailEvent.Error(e.message ?: "error"))
        }
    }
}

/**
 * Factory for the per-tab ViewModels: one activity-scoped instance per
 * class, carrying the torrent hash (the activity-scoped viewModels {}
 * delegate resolves each fragment's ViewModel through it, avoiding the
 * default SavedStateFactory that cannot build the (Application, String)
 * constructors).
 */
class DetailViewModelFactory(
    private val app: Application,
    private val hash: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DetailOverviewViewModel::class.java) ->
            DetailOverviewViewModel(app, hash) as T
        modelClass.isAssignableFrom(DetailFilesViewModel::class.java) ->
            DetailFilesViewModel(app, hash) as T
        modelClass.isAssignableFrom(DetailTrackersViewModel::class.java) ->
            DetailTrackersViewModel(app, hash) as T
        modelClass.isAssignableFrom(DetailPeersViewModel::class.java) ->
            DetailPeersViewModel(app, hash) as T
        modelClass.isAssignableFrom(DetailWebSeedsViewModel::class.java) ->
            DetailWebSeedsViewModel(app, hash) as T
        else -> throw IllegalArgumentException("Unknown detail ViewModel: $modelClass")
    }
}
