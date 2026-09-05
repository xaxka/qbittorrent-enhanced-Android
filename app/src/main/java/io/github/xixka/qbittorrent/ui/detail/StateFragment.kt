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
import com.google.android.material.chip.Chip
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentTorrentStateBinding
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.TorrentStateColors
import io.github.xixka.qbittorrent.util.TorrentStates
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * State tab, LibreTorrent fragment_torrent_details_state parity: the
 * progress card (category/tags chips, progress line, state-colored bar,
 * state + speeds) above the dashboard of filled stat tiles (speed,
 * seeds/leechers, share ratio / availability, downloaded / ETA,
 * uploaded / pieces). Shares the activity-scoped
 * [DetailOverviewViewModel] with the overview tab — the poller stays
 * active while EITHER page is visible.
 */
class StateFragment : Fragment() {

    private var _binding: FragmentTorrentStateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailOverviewViewModel
        get() = (requireActivity() as DetailActivity).overviewViewModel

    private var lastChipsSignature: String? = null

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

    private fun render(torrent: TorrentInfo, props: TorrentProperties) {
        val context = binding.root.context

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
            val (color, track) = TorrentStateColors.resolve(binding.root, torrent.state)
            setIndicatorColor(color)
            trackColor = track
        }

        binding.stateText.setText(TorrentStates.labelRes(torrent.state))

        // qBC: "↓ speed" is drawn in colorPrimary and "↑ speed" in
        // colorTertiary, joined by a single space (buildAnnotatedString)
        binding.speedText.text = SpannableStringBuilder().apply {
            if (torrent.dlSpeed > 0) {
                val start = length
                append("↓ ").append(Format.speed(torrent.dlSpeed))
                setSpan(
                    ForegroundColorSpan(
                        MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
                    ),
                    start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (torrent.dlSpeed > 0 && torrent.upSpeed > 0) append(" ")
            if (torrent.upSpeed > 0) {
                val start = length
                append("↑ ").append(Format.speed(torrent.upSpeed))
                setSpan(
                    ForegroundColorSpan(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorTertiary)
                    ),
                    start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        // LibreTorrent status-panel tiles
        binding.stateSpeed.text =
            "↓ ${Format.speed(torrent.dlSpeed)} | ↑ ${Format.speed(torrent.upSpeed)}"
        binding.stateSeeds.text = context.getString(
            R.string.torrent_overview_seeds_format,
            props.seeds.toInt(),
            props.seedsTotal.toInt(),
        )
        binding.stateLeechers.text = context.getString(
            R.string.torrent_overview_peers_format,
            props.peers.toInt(),
            props.peersTotal.toInt(),
        )
        binding.stateRatio.text = String.format(Locale.ROOT, "%.2f", torrent.ratio)
        binding.stateAvailability.text = torrent.availability
            .takeIf { it >= 0 }?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"
        binding.stateDownloaded.text = Format.size(torrent.downloaded)
        binding.stateEta.text = when {
            torrent.eta >= 8640000L -> "∞"
            torrent.eta > 0L -> DateUtils.formatElapsedTime(torrent.eta)
            else -> "—"
        }
        binding.stateUploaded.text = Format.size(torrent.uploaded)
        binding.statePieces.text =
            if (props.piecesNum > 0) "${props.piecesHave}/${props.piecesNum}" else "—"
    }

    /** Display-only category + tags chips (qBC Progress card): the category
     *  renders as a primaryContainer block and each tag as a
     *  tertiaryContainer block, like qBC's CategoryChip / TagChip. */
    private fun renderChips(torrent: TorrentInfo) {
        val group = binding.tagsChipGroup
        group.removeAllViews()
        val inflater = layoutInflater
        if (torrent.category.isNotBlank()) {
            val chip = inflater.inflate(
                R.layout.item_overview_category_chip, group, false,
            ) as TextView
            chip.text = torrent.category
            group.addView(chip)
        }
        torrent.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
            val chip = inflater.inflate(
                R.layout.item_overview_tag_chip, group, false,
            ) as TextView
            chip.text = tag
            group.addView(chip)
        }
        group.visibility = if (group.childCount == 0) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
