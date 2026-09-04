package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentTorrentInfoBinding
import io.github.xixka.qbittorrent.databinding.SheetPieceMapBinding
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.TorrentStates
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Overview tab, qBC TorrentOverviewTab parity: name, progress card
 * (category/tags chips + progress line + state-colored bar + speeds),
 * pieces bar card (tap = piece-map bottom sheet), Information and
 * Transfer cards. Data comes from the tab-scoped DetailOverviewViewModel
 * which polls only while this page is the visible one.
 */
class InfoFragment : Fragment() {

    private var _binding: FragmentTorrentInfoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailOverviewViewModel
        get() = (requireActivity() as DetailActivity).overviewViewModel

    private var lastChipsSignature: String? = null

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

        binding.piecesCard.setOnClickListener { showPieceMapSheet() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.torrent.collect { torrent ->
                        if (torrent != null) render(torrent, viewModel.properties.value)
                    }
                }
                launch {
                    viewModel.properties.collect { props ->
                        val torrent = viewModel.torrent.value
                        if (torrent != null && props != null) render(torrent, props)
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

    override fun onResume() {
        super.onResume()
        viewModel.setScreenActive(true)
    }

    override fun onPause() {
        viewModel.setScreenActive(false)
        super.onPause()
    }

    private fun render(torrent: TorrentInfo, props: TorrentProperties?) {
        val context = binding.root.context

        binding.torrentName.text = torrent.name

        // ---- progress card ----
        val signature = "${torrent.category}|${torrent.tags}"
        if (signature != lastChipsSignature) {
            lastChipsSignature = signature
            renderChips(torrent)
        }

        val progressPercent = (torrent.progress * 100).let {
            if (torrent.progress >= 1.0) "100" else String.format(Locale.ROOT, "%.1f", it)
        }
        binding.progressText.text = context.getString(
            R.string.torrent_item_progress_format,
            Format.size(torrent.completed),
            Format.size(torrent.size),
            progressPercent,
            String.format(Locale.ROOT, "%.2f", torrent.ratio),
        )
        binding.etaText.text =
            if (torrent.eta in 1..8639999) Format.duration(torrent.eta) else ""

        binding.progressIndicator.apply {
            setProgress((torrent.progress * 100).toInt().coerceIn(0, 100))
            val (color, track) = stateColor(torrent.state)
            setIndicatorColor(color)
            trackColor = track
        }

        binding.stateText.setText(TorrentStates.labelRes(torrent.state))
        binding.speedText.text = buildString {
            if (torrent.dlSpeed > 0) append("↓ ").append(Format.speed(torrent.dlSpeed))
            if (torrent.dlSpeed > 0 && torrent.upSpeed > 0) append("  ")
            if (torrent.upSpeed > 0) append("↑ ").append(Format.speed(torrent.upSpeed))
        }

        // ---- Information card (qBC field order) ----
        renderRows(
            binding.informationRows,
            listOf(
                R.string.torrent_overview_total_size to Format.size(props?.totalSize ?: torrent.size),
                R.string.torrent_added_date to Format.epochDate(torrent.addedOn),
                R.string.detail_private to getString(
                    if (torrent.isPrivate == true) R.string.detail_yes else R.string.detail_no
                ),
                R.string.torrent_overview_hash_v1 to (props?.infohashV1?.ifBlank { torrent.hash } ?: torrent.hash),
                R.string.torrent_overview_hash_v2 to (props?.infohashV2?.ifBlank { "—" } ?: "—"),
                R.string.torrent_option_save_path to (props?.savePath ?: torrent.savePath),
                // Comments of public torrents are NFO-style blobs with
                // dozens of blank lines: collapse them or the Information
                // card drowns in empty lines.
                R.string.torrent_comment to (props?.comment
                    ?.let { Format.collapseBlankLines(it) }
                    ?.ifBlank { "—" } ?: "—"),
                R.string.torrent_overview_pieces to (
                    if (props != null && props.piecesNum > 0) context.getString(
                        R.string.torrent_overview_pieces_format,
                        props.piecesNum.toInt(),
                        Format.size(props.pieceSize),
                        props.piecesHave.toInt(),
                    ) else "—"
                    ),
                R.string.detail_completed_on to
                    (torrent.completionOn.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—"),
                R.string.torrent_created_in_program to (props?.createdBy
                    ?.let { Format.collapseBlankLines(it) }
                    ?.ifBlank { "—" } ?: "—"),
                R.string.torrent_create_date to
                    (props?.creationDate?.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—"),
            ),
        )

        // ---- Transfer card (qBC field order) ----
        val timeActive = Format.duration(props?.timeActive ?: 0L)
        val seedingTime = props?.seedingTime ?: 0L
        val timeActiveText = if (seedingTime > 0) {
            context.getString(
                R.string.torrent_overview_time_active_seeding_time_format,
                timeActive,
                Format.duration(seedingTime),
            )
        } else {
            timeActive
        }
        renderRows(
            binding.transferRows,
            listOf(
                R.string.detail_time_active to timeActiveText,
                R.string.detail_downloaded_total to context.getString(
                    R.string.torrent_overview_downloaded_format,
                    Format.size(torrent.downloaded),
                    Format.size(torrent.downloadedSession),
                ),
                R.string.detail_uploaded_total to context.getString(
                    R.string.torrent_overview_uploaded_format,
                    Format.size(torrent.uploaded),
                    Format.size(torrent.uploadedSession),
                ),
                R.string.torrent_overview_reannounce_in to
                    ((props?.reannounce ?: 0L).takeIf { it > 0 }?.let { Format.duration(it) } ?: "—"),
                R.string.detail_last_activity to
                    (torrent.lastActivity.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—"),
                R.string.detail_last_seen_complete to
                    (props?.lastSeenComplete?.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—"),
                R.string.detail_connections to context.getString(
                    R.string.torrent_overview_connections_format,
                    (props?.connections ?: 0L).toInt(),
                    (props?.connectionsLimit ?: -1L).let { if (it > 0) it.toString() else "∞" },
                ),
                R.string.detail_seeds to context.getString(
                    R.string.torrent_overview_seeds_format,
                    (props?.seeds ?: torrent.numSeeds.toLong()).toInt(),
                    (props?.seedsTotal ?: torrent.numSeedsTotal.toLong()).toInt(),
                ),
                R.string.detail_peers to context.getString(
                    R.string.torrent_overview_peers_format,
                    (props?.peers ?: torrent.numLeechs.toLong()).toInt(),
                    (props?.peersTotal ?: torrent.numLeechsTotal.toLong()).toInt(),
                ),
                R.string.detail_wasted to Format.size(props?.totalWasted ?: 0L),
                R.string.detail_availability to
                    (torrent.availability.takeIf { it >= 0 }
                        ?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"),
            ),
        )
    }

    /** qBC state colors: downloading = primary, uploading = tertiary, paused = outline. */
    private fun stateColor(state: String): Pair<Int, Int> {
        val theme = binding.root.context.theme
        fun attr(resId: Int): Int {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(resId, typedValue, true)
            return typedValue.data
        }

        fun track(base: Int) = (base and 0x00FFFFFF) or 0x61000000

        return when (state.lowercase()) {
            "uploading", "forcedup" -> {
                val c = attr(com.google.android.material.R.attr.colorTertiary)
                c to track(c)
            }

            "stoppedup", "pausedup", "stoppeddl", "pauseddl" -> {
                val c = attr(com.google.android.material.R.attr.colorOutline)
                c to track(c)
            }

            "error", "missingfiles" -> {
                val c = attr(android.R.attr.colorError)
                c to track(c)
            }

            else -> {
                val c = attr(android.R.attr.colorPrimary)
                c to track(c)
            }
        }
    }

    /** Display-only category + tags chips (qBC Progress card). */
    private fun renderChips(torrent: TorrentInfo) {
        val group = binding.tagsChipGroup
        group.removeAllViews()
        val inflater = layoutInflater
        if (torrent.category.isNotBlank()) {
            val chip = inflater.inflate(R.layout.item_tag_chip, group, false) as Chip
            chip.text = torrent.category
            chip.isClickable = false
            chip.isCheckable = false
            group.addView(chip)
        }
        torrent.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
            val chip = inflater.inflate(R.layout.item_tag_chip, group, false) as Chip
            chip.text = tag
            chip.isClickable = false
            chip.isCheckable = false
            group.addView(chip)
        }
        group.visibility = if (group.childCount == 0) View.GONE else View.VISIBLE
    }

    /** Reuses row views across polls (texts updated in place). */
    private fun renderRows(container: ViewGroup, rows: List<Pair<Int, String>>) {
        if (container.childCount != rows.size) {
            container.removeAllViews()
            rows.forEach { layoutInflater.inflate(R.layout.item_detail_param, container, true) }
        }
        rows.forEachIndexed { index, (labelRes, value) ->
            val row = container.getChildAt(index)
            row.findViewById<TextView>(R.id.param_label).setText(labelRes)
            // A blank value would render an empty-looking row; the em dash
            // keeps the panel readable when the server sent nothing.
            row.findViewById<TextView>(R.id.param_value).text = value.ifBlank { "—" }
        }
    }

    /** qBC PiecesBottomSheet parity: heatmap + legend + per-piece summary. */
    private fun showPieceMapSheet() {
        val sheetBinding = SheetPieceMapBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)

        val pieces = viewModel.pieces.value
        val props = viewModel.properties.value
        val have = pieces.count { it == 2 }
        sheetBinding.piecesSummary.text = when {
            pieces.isEmpty() && props != null && props.piecesNum > 0 -> getString(
                R.string.pieces_summary,
                props.piecesHave.toInt(),
                props.piecesNum.toInt(),
                Format.size(props.pieceSize),
            )
            pieces.isEmpty() -> getString(R.string.pieces_unavailable)
            else -> getString(
                R.string.pieces_summary,
                have,
                pieces.size,
                if (props != null && props.pieceSize > 0) Format.size(props.pieceSize) else "—",
            )
        }
        sheetBinding.pieceHeatmap.submit(pieces)
        dialog.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
