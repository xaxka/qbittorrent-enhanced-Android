package io.github.xixka.qbittorrent.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.xixka.qbittorrent.api.QBAuthException
import io.github.xixka.qbittorrent.data.ServiceLocator
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
 * UI state of the torrent list: connectivity, current filter, torrents and
 * global transfer speeds (LibreTorrent-style status line).
 */
data class ListUiState(
    val loading: Boolean = false,
    val connected: Boolean = false,
    val configured: Boolean = true,
    val authError: Boolean = false,
    val error: String? = null,
    val serverVersion: String = "",
    val torrents: List<TorrentInfo> = emptyList(),
    val transfer: TransferInfo? = null,
)

class TorrentListViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ServiceLocator.repository(app)
    private val prefs = ServiceLocator.prefs(app)

    private val _state = MutableStateFlow(ListUiState(loading = true))
    val state: StateFlow<ListUiState> = _state

    /** Server-side filter (qBittorrent semantics). */
    var filter: String = "all"
        private set

    var category: String? = null
        private set

    var sort: String = "added_on"
        private set

    var sortReverse: Boolean = true
        private set

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val interval = prefs.pollIntervalSec * 1000L
                if (!prefs.serverConfig().isConfigured) {
                    _state.update { ListUiState(loading = false, configured = false) }
                    delay(1000)
                    continue
                }
                try {
                    val torrents = repository.torrents(filter, category, sort, sortReverse)
                    val transfer = runCatching { repository.transferInfo() }.getOrNull()
                    val version = if (_state.value.serverVersion.isBlank()) {
                        runCatching { repository.appVersion() }.getOrDefault("")
                    } else {
                        _state.value.serverVersion
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            connected = true,
                            configured = true,
                            authError = false,
                            error = null,
                            serverVersion = version,
                            torrents = torrents,
                            transfer = transfer,
                        )
                    }
                } catch (e: QBAuthException) {
                    _state.update {
                        it.copy(
                            loading = false,
                            connected = true,
                            configured = true,
                            authError = true,
                            error = e.message,
                            torrents = emptyList(),
                            transfer = null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            loading = false,
                            connected = false,
                            configured = true,
                            authError = false,
                            error = humanMessage(e),
                            torrents = emptyList(),
                            transfer = null,
                        )
                    }
                }
                delay(if (_state.value.connected) interval else (interval * 2).coerceAtMost(10000))
            }
        }
    }

    fun setFilter(value: String) {
        if (filter == value) return
        filter = value
        refresh()
    }

    fun setCategory(value: String?) {
        category = value
        refresh()
    }

    fun setSort(field: String, reverse: Boolean) {
        sort = field
        sortReverse = reverse
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        startPolling()
    }

    private fun humanMessage(e: Exception): String = when (e) {
        is UnknownHostException -> "Host not found — check the server address"
        is ConnectException -> "Connection refused — check address/port (default 8080)"
        is SocketTimeoutException -> "Connection timed out"
        is javax.net.ssl.SSLException -> "TLS error — self-signed certificate? enable trust-all in settings"
        else -> e.message ?: e.javaClass.simpleName
    }

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
