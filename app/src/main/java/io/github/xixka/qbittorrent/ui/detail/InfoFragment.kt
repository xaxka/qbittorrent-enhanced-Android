package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentTorrentInfoBinding
import io.github.xixka.qbittorrent.databinding.SheetPieceMapBinding
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.TorrentStateColors
import io.github.xixka.qbittorrent.util.TorrentStates
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Overview tab, qBC TorrentOverviewTab parity: name, progress card
 * (category/tags chips + progress line + state-colored bar + speeds),
 * pieces bar card with live count/size header (tap = piece-map bottom
 * sheet), Information and Transfer cards. Data comes from the tab-scoped
 * DetailOverviewViewModel which polls only while this page is the visible
 * one.
 *
 * qBC loading behavior: the content stays GONE until BOTH the torrent and
 * its properties have arrived (no half-rendered "—" rows flashing), an
 * indeterminate top [LinearProgressIndicator] runs during the natural
 * first load, and the content fades in over 120 ms once ready.
 */
class InfoFragment : Fragment() {

    private var _binding: FragmentTorrentInfoBinding? = null
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
        _binding = FragmentTorrentInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.overviewRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.overviewContent.visibility = View.GONE
        binding.loadingIndicator.setVisibilityAfterHide(View.INVISIBLE)

        binding.piecesCard.setOnClickListener { showPieceMapSheet() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // qBC: natural (first) load drives the top indeterminate bar
                launch {
                    viewModel.isNaturalLoading.collect { loading ->
                        if (loading == true) binding.loadingIndicator.show() else binding.loadingIndicator.hide()
                    }
                }
                // qBC: render only when BOTH the torrent and its properties
                // are in — a single flow would flash "—" placeholders while
                // the second request is still in flight.
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
                    viewModel.pieces.collect { binding.pieceBar.submit(it) }
                }
                launch {
                    viewModel.isRefreshing.collect { binding.overviewRefresh.isRefreshing = it }
                }
            }
        }
    }

    /** 120 ms alpha fade-in the first time content becomes available. */
    private fun revealContentOnce() {
        if (contentShown) return
        contentShown = true
        binding.overviewContent.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).duration = 120
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setInfoTabActive(true)
    }

    override fun onPause() {
        viewModel.setInfoTabActive(false)
        super.onPause()
    }

    private fun render(torrent: TorrentInfo, props: TorrentProperties) {
        val context = binding.root.context

        binding.torrentName.text = torrent.name

        // ---- Information card (qBC field order; rows hidden when the
        // engine has no answer yet, qBC null-handling parity) ----
        val infoRows = mutableListOf<Pair<Int, String>>()
        infoRows += R.string.torrent_overview_total_size to Format.size(props.totalSize)
        infoRows += R.string.torrent_added_date to Format.epochDate(torrent.addedOn)
        // "private" is null while metadata is unfetched -> row hidden (qBC)
        if (torrent.isPrivate != null) {
            infoRows += R.string.detail_private to getString(
                if (torrent.isPrivate == true) R.string.detail_yes else R.string.detail_no
            )
        }
        infoRows += R.string.torrent_overview_hash_v1 to
            (torrent.infohashV1?.ifBlank { null } ?: props.infohashV1.ifBlank { torrent.hash })
        infoRows += R.string.torrent_overview_hash_v2 to
            (torrent.infohashV2?.ifBlank { null } ?: props.infohashV2.ifBlank { "—" })
        infoRows += R.string.torrent_option_save_path to props.savePath
        // Comments of public torrents are NFO-style blobs with dozens of
        // blank lines: collapse them or the Information card drowns in
        // empty lines.
        infoRows += R.string.torrent_comment to (props.comment
            .let { Format.collapseBlankLines(it) }
            .ifBlank { "—" })
        infoRows += R.string.torrent_overview_pieces to
            if (props.piecesNum > 0) context.getString(
                R.string.torrent_overview_pieces_format,
                props.piecesNum.toInt(),
                Format.size(props.pieceSize),
                props.piecesHave.toInt(),
            ) else "—"
        infoRows += R.string.detail_completed_on to
            (torrent.completionOn.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—")
        infoRows += R.string.torrent_created_in_program to
            (props.createdBy.let { Format.collapseBlankLines(it) }.ifBlank { "—" })
        infoRows += R.string.torrent_create_date to
            (props.creationDate.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—")
        renderRows(binding.informationRows, infoRows)

        // ---- Transfer card (qBC field order) ----
        val timeActive = Format.duration(props.timeActive)
        val seedingTime = props.seedingTime
        val timeActiveText = if (seedingTime > 0) {
            context.getString(
                R.string.torrent_overview_time_active_seeding_time_format,
                timeActive,
                Format.duration(seedingTime),
            )
        } else {
            timeActive
        }
        val transferRows = mutableListOf<Pair<Int, String>>()
        transferRows += R.string.detail_time_active to timeActiveText
        transferRows += R.string.detail_downloaded_total to context.getString(
            R.string.torrent_overview_downloaded_format,
            Format.size(torrent.downloaded),
            Format.size(torrent.downloadedSession),
        )
        transferRows += R.string.detail_uploaded_total to context.getString(
            R.string.torrent_overview_uploaded_format,
            Format.size(torrent.uploaded),
            Format.size(torrent.uploadedSession),
        )
        transferRows += R.string.torrent_overview_reannounce_in to
            ((props.reannounce).takeIf { it > 0 }?.let { Format.duration(it) } ?: "—")
        transferRows += R.string.detail_last_activity to
            (torrent.lastActivity.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—")
        transferRows += R.string.detail_last_seen_complete to
            (props.lastSeenComplete.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—")
        transferRows += R.string.detail_connections to context.getString(
            R.string.torrent_overview_connections_format,
            (props.connections).toInt(),
            (props.connectionsLimit).let { if (it > 0) it.toString() else "∞" },
        )
        transferRows += R.string.detail_seeds to context.getString(
            R.string.torrent_overview_seeds_format,
            (props.seeds).toInt(),
            (props.seedsTotal).toInt(),
        )
        transferRows += R.string.detail_peers to context.getString(
            R.string.torrent_overview_peers_format,
            (props.peers).toInt(),
            (props.peersTotal).toInt(),
        )
        transferRows += R.string.detail_wasted to Format.size(props.totalWasted)
        transferRows += R.string.detail_availability to
            (torrent.availability.takeIf { it >= 0 }
                ?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—")
        // qB >= 5.0 swarm popularity; hidden when the engine omits it (qBC)
        if (torrent.popularity != null) {
            transferRows += R.string.detail_popularity to
                String.format(Locale.ROOT, "%.2f", torrent.popularity)
        }
        renderRows(binding.transferRows, transferRows)
    }

    /** Reuses row views across polls (texts updated in place), qBC InfoRow
     *  parity: one common label-column width, single-line ellipsized labels
     *  and left-aligned wrapping values. See [DetailParamRows]. */
    private fun renderRows(container: ViewGroup, rows: List<Pair<Int, String>>) =
        DetailParamRows.bind(requireContext(), container, rows)

    /** qBC PiecesBottomSheet parity: piece grid + primary-alpha legend. */
    private fun showPieceMapSheet() {
        val sheetBinding = SheetPieceMapBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)

        sheetBinding.pieceHeatmap.submit(viewModel.pieces.value)
        dialog.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
