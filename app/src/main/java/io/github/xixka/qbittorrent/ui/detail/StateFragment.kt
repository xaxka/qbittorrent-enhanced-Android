package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.xixka.qbittorrent.databinding.FragmentTorrentStateBinding
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.util.Format
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * State tab, faithful port of LibreTorrent
 * fragment_torrent_details_state.xml: a plain stack of stat tiles —
 * speed, seeds/leechers, share ratio / availability, downloaded / ETA,
 * uploaded / pieces, active time / seeding time. Shares the
 * activity-scoped [DetailOverviewViewModel] with the overview tab — the
 * poller stays active while EITHER page is visible.
 */
class StateFragment : Fragment() {

    private var _binding: FragmentTorrentStateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailOverviewViewModel
        get() = (requireActivity() as DetailActivity).overviewViewModel

    /** Content shown once (torrent + properties both non-null). */
    private var contentShown = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTorrentStateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.stateRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.stateContent.visibility = View.GONE
        binding.loadingIndicator.setVisibilityAfterHide(View.INVISIBLE)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isNaturalLoading.collect { loading ->
                        if (loading == true) binding.loadingIndicator.show() else binding.loadingIndicator.hide()
                    }
                }
                launch {
                    combine(viewModel.torrent, viewModel.properties) { t, p -> t to p }
                        .collect { (torrent, props) ->
                            if (torrent != null && props != null) {
                                render(torrent, props)
                                revealContentOnce()
                            }
                        }
                }
                launch {
                    viewModel.isRefreshing.collect { binding.stateRefresh.isRefreshing = it }
                }
            }
        }
    }

    /** 120 ms alpha fade-in the first time content becomes available. */
    private fun revealContentOnce() {
        if (contentShown) return
        contentShown = true
        binding.stateContent.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).duration = 120
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setStateTabActive(true)
    }

    override fun onPause() {
        viewModel.setStateTabActive(false)
        super.onPause()
    }

    /** LibreTorrent tile values: formats match the original's binding
     *  adapters (progress "x/y (p%)", pieces "n (size)", 3-decimal floats). */
    private fun render(torrent: TorrentInfo, props: TorrentProperties) {
        binding.stateSpeed.text =
            "↓ ${Format.speed(torrent.dlSpeed)} | ↑ ${Format.speed(torrent.upSpeed)}"
        // LibreTorrent torrent_peers_template: "seeds (total)"
        binding.stateSeeds.text = "${props.seeds.toInt()} (${props.seedsTotal.toInt()})"
        binding.stateLeechers.text = "${props.peers.toInt()} (${props.peersTotal.toInt()})"
        binding.stateRatio.text = String.format(Locale.ROOT, "%.3f", torrent.ratio)
        binding.stateAvailability.text = torrent.availability
            .takeIf { it >= 0 }?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"

        val progressPercent =
            if (torrent.progress >= 1.0) "100" else String.format(Locale.ROOT, "%.1f", torrent.progress * 100)
        binding.stateDownloaded.text =
            "${Format.size(torrent.completed)}/${Format.size(torrent.size)} ($progressPercent%)"
        binding.stateEta.text = when {
            torrent.eta >= 8640000L -> "∞"
            torrent.eta > 0L -> DateUtils.formatElapsedTime(torrent.eta)
            else -> "—"
        }
        binding.stateUploaded.text = Format.size(torrent.uploaded)
        binding.statePieces.text =
            if (props.piecesNum > 0) "${props.piecesHave} (${Format.size(props.pieceSize)})" else "—"

        binding.stateActiveTime.text = DateUtils.formatElapsedTime(props.timeActive)
        binding.stateSeedingTime.text = DateUtils.formatElapsedTime(props.seedingTime)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
