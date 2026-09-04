package io.github.xixka.qbittorrent.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityPanelBinding
import io.github.xixka.qbittorrent.model.ServerState
import io.github.xixka.qbittorrent.ui.main.MainActivity
import io.github.xixka.qbittorrent.util.Format
import kotlinx.coroutines.launch

/**
 * qBitController-style statistics panel (StatisticsDialog parity) rendered
 * with LibreTorrent's flat-row UI: user statistics, cache statistics and
 * performance statistics sections fed by /sync/maindata's server_state.
 * Opened from Settings, hosted IN PLACE — no separate window.
 */
class StatisticsFragment : Fragment() {

    private var _binding: ActivityPanelBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ActivityPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.setTitle(R.string.stats)
        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun load() {
        val content = binding.content
        content.removeAllViews()
        content.addView(row(getString(R.string.loading)))
        lifecycleScope.launch {
            val state = runCatching { ServiceLocator.repository(requireContext()).serverState() }
                .getOrNull()
            content.removeAllViews()
            if (state == null) {
                content.addView(row(getString(R.string.error_connection)))
                return@launch
            }
            bind(state, content)
        }
    }

    private fun bind(state: ServerState, content: LinearLayout) {
        content.addView(header(R.string.stats_category_user))
        content.addView(row(R.string.stats_all_time_upload, Format.size(state.allTimeUpload)))
        content.addView(row(R.string.stats_all_time_download, Format.size(state.allTimeDownload)))
        content.addView(row(R.string.stats_all_time_share_ratio, state.globalRatio))
        content.addView(row(R.string.stats_session_waste, Format.size(state.sessionWaste)))
        content.addView(row(R.string.stats_connected_peers, state.connectedPeers.toString()))

        content.addView(header(R.string.stats_category_cache))
        content.addView(row(R.string.stats_read_cache_hits, state.readCacheHits + "%"))
        content.addView(row(R.string.stats_total_buffer_size, Format.size(state.bufferSize)))

        content.addView(header(R.string.stats_category_performance))
        content.addView(row(R.string.stats_write_cache_overload, state.writeCacheOverload + "%"))
        content.addView(row(R.string.stats_read_cache_overload, state.readCacheOverload + "%"))
        content.addView(row(R.string.stats_queued_io_jobs, state.queuedIOJobs.toString()))
        content.addView(
            row(
                getString(R.string.stats_average_time_in_queue),
                getString(R.string.stats_ms_format, state.averageTimeInQueue),
            )
        )
        content.addView(row(R.string.stats_total_queued_size, Format.size(state.queuedSize)))

        content.addView(header(R.string.stats_category_session))
        content.addView(row(R.string.download_speed, Format.speed(state.downloadSpeed)))
        content.addView(row(R.string.upload_speed, Format.speed(state.uploadSpeed)))
        content.addView(row(R.string.stats_session_download, Format.size(state.downloadSession)))
        content.addView(row(R.string.stats_session_upload, Format.size(state.uploadSession)))
        content.addView(
            row(
                getString(R.string.stats_free_space),
                if (state.freeSpace >= 0) Format.size(state.freeSpace) else "—",
            )
        )
    }

    private fun header(titleRes: Int): View {
        val v = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_panel_header, binding.content, false)
        v.findViewById<TextView>(R.id.header_title).setText(titleRes)
        return v
    }

    private fun row(label: String, value: String = ""): View {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.item_stat_row, binding.content, false)
        v.findViewById<TextView>(R.id.label).text = label
        v.findViewById<TextView>(R.id.value).text = value
        return v
    }

    private fun row(labelRes: Int, value: String = ""): View =
        row(getString(labelRes), value)
}
