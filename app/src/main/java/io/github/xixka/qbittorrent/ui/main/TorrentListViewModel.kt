package io.github.xixka.qbittorrent.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.xixka.qbittorrent.api.QBAuthException
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TransferInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Home screen state, modeled after LibreTorrent's TorrentListViewModel:
 * drawer chips (status / date added / category), sorting with direction,
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
)

enum class StatusFilter { NONE, DOWNLOADING, DOWNLOADED, DOWNLOADING_METADATA, ERROR }
enum class DateAddedFilter { NONE, TODAY, YESTERDAY, WEEK, MONTH, YEAR }
enum class SortField { DATE_ADDED, NAME, SIZE, PROGRESS, ETA, PEERS }

class TorrentListViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ServiceLocator.repository(app)
    private val prefs = ServiceLocator.prefs(app)

    private val _state = MutableStateFlow(ListUiState())
    val state: StateFlow<ListUiState> = _state

    // drawer filters
    var statusFilter = StatusFilter.NONE
        private set
    var dateFilter = DateAddedFilter.NONE
        private set
    var category: String? = null
        private set
    var sortField = SortField.DATE_ADDED
        private set
    var sortDescending = false
        private set
    var searchQuery: String = ""
        private set

    private var pollJob: Job? = null

    init {
        restart()
    }

    fun restart() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(3000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshOnce() }
    }

    private suspend fun refreshOnce() {
        if (!prefs.serverConfig().isConfigured) {
            _state.update { it.copy(configured = false, connected = false, loading = false) }
            return
        }
        _state.update { it.copy(loading = true, configured = true) }
        try {
            val torrents = repository.torrents(filter = null)
            val transfer = runCatching { repository.transferInfo() }.getOrNull()
            val categories = runCatching {
                repository.categories().keys.filter { it.isNotBlank() }.sorted()
            }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    loading = false,
                    connected = true,
                    authError = false,
                    error = null,
                    serverVersion = runCatching { repository.appVersion() }.getOrDefault(""),
                    torrents = filtered(torrents),
                    allCount = torrents.size,
                    transfer = transfer,
                    categories = categories,
                )
            }
        } catch (e: QBAuthException) {
            _state.update { it.copy(loading = false, connected = false, authError = true) }
        } catch (e: Exception) {
            val msg = when (e) {
                is UnknownHostException -> "Unknown host"
                is ConnectException -> "Connection refused"
                is SocketTimeoutException -> "Timeout"
                else -> e.message ?: "error"
            }
            _state.update { it.copy(loading = false, connected = false, error = msg) }
        }
    }

    fun setStatusFilter(filter: StatusFilter?) {
        statusFilter = filter ?: StatusFilter.NONE
        refilter()
    }

    fun setDateAddedFilter(filter: DateAddedFilter?) {
        dateFilter = filter ?: DateAddedFilter.NONE
        refilter()
    }

    fun setCategory(category: String?) {
        this.category = category
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

    fun resetFilters() {
        statusFilter = StatusFilter.NONE
        dateFilter = DateAddedFilter.NONE
        category = null
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

        result = when (statusFilter) {
            StatusFilter.NONE -> result
            StatusFilter.DOWNLOADING -> result.filter { !isPaused(it) && it.progress < 1.0 && it.state.lowercase() != "metadl" }
            StatusFilter.DOWNLOADED -> result.filter { it.progress >= 1.0 }
            StatusFilter.DOWNLOADING_METADATA -> result.filter { it.state.lowercase() == "metadl" }
            StatusFilter.ERROR -> result.filter { it.state.startsWith("error") || it.state.startsWith("missing") }
        }

        result = when (dateFilter) {
            DateAddedFilter.NONE -> result
            else -> {
                val now = System.currentTimeMillis() / 1000
                val from = when (dateFilter) {
                    DateAddedFilter.TODAY -> now - 86400
                    DateAddedFilter.YESTERDAY -> now - 172800
                    DateAddedFilter.WEEK -> now - 604800
                    DateAddedFilter.MONTH -> now - 2592000
                    DateAddedFilter.YEAR -> now - 31536000
                    else -> 0
                }
                result.filter { it.addedOn >= from }
            }
        }

        category?.let { cat ->
            result = if (cat.isEmpty()) {
                result.filter { it.category.isBlank() }
            } else {
                result.filter { it.category == cat }
            }
        }

        if (searchQuery.isNotBlank()) {
            result = result.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }

        val sorted = when (sortField) {
            SortField.DATE_ADDED ->
                if (sortDescending) result.sortedByDescending { it.addedOn } else result.sortedBy { it.addedOn }
            else -> {
                val cmp = when (sortField) {
                    SortField.NAME -> compareBy<TorrentInfo> { it.name.lowercase() }
                    SortField.SIZE -> compareBy { it.size }
                    SortField.PROGRESS -> compareBy { it.progress }
                    SortField.ETA -> compareBy { it.eta }
                    else -> compareBy { it.numLeechsTotal + it.numSeedsTotal }
                }
                if (sortDescending) result.sortedWith(cmp.reversed()) else result.sortedWith(cmp)
            }
        }
        return sorted.toList()
    }

    private fun isPaused(t: TorrentInfo) =
        t.state.lowercase() in setOf("pauseddl", "pausedup", "stoppeddl", "stoppedup")

    fun pauseAll() = viewModelScope.launch { runCatching { repository.pauseAll() } }
    fun resumeAll() = viewModelScope.launch { runCatching { repository.resumeAll() } }

    companion object {
        fun factory(app: Application): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return TorrentListViewModel(app) as T
                }
            }
    }
}
