package io.github.xixka.qbittorrent.ui.detail

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentDetailOverviewBinding
import io.github.xixka.qbittorrent.util.Format
import kotlinx.coroutines.launch

/**
 * Overview page: a LibreTorrent-style list of torrent properties.
 */
class OverviewFragment : Fragment() {

    private var _binding: FragmentDetailOverviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by activityViewModels {
        DetailViewModel.factory(requireActivity().application, detailHash)
    }

    private val detailHash: String
        get() = (activity as? DetailActivity)?.torrentHash ?: ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDetailOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: DetailUiState) {
        val props = state.properties ?: return
        val ctx = binding.root.context

        binding.overviewProgress.max = 1000
        val fraction = if (props.piecesNum > 0) {
            (props.piecesHave.toDouble() / props.piecesNum.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0
        binding.overviewProgress.progress = (fraction * 1000).toInt()
        binding.overviewPercent.text = Format.progress(fraction)
        binding.overviewSize.text =
            "${Format.size(props.totalDownloaded)} / ${Format.size(props.totalSize)}"

        val container = binding.overviewRows
        container.removeAllViews()

        addRow(getString(R.string.overview_total_size), Format.size(props.totalSize))
        addRow(getString(R.string.overview_ratio), Format.ratio(props.shareRatio))
        addRow(getString(R.string.overview_save_path), props.savePath)
        addRow(
            getString(R.string.overview_pieces),
            "${props.piecesHave} / ${props.piecesNum} (${Format.size(props.pieceSize)})",
        )
        addRow(
            getString(R.string.overview_connected),
            "${getString(R.string.seeds)} ${props.seeds}  •  ${getString(R.string.peers)} ${props.peers}",
        )
        addRow(getString(R.string.overview_added_on), Format.epochDate(props.additionDate))
        addRow(getString(R.string.overview_completed_on), Format.epochDate(props.completionDate))
        addRow(getString(R.string.overview_seeding_time), Format.duration(props.seedingTime))
        addRow(getString(R.string.overview_time_active), Format.duration(props.timeActive))
        addRow(getString(R.string.overview_downloaded), Format.size(props.totalDownloaded))
        addRow(getString(R.string.overview_uploaded), Format.size(props.totalUploaded))
        addRow(getString(R.string.overview_wasted), Format.size(props.totalWasted))
        addRow(
            getString(R.string.overview_dl_limit),
            if (props.dlLimit < 0) "∞" else Format.speed(props.dlLimit),
        )
        addRow(
            getString(R.string.overview_up_limit),
            if (props.upLimit < 0) "∞" else Format.speed(props.upLimit),
        )
        addRow(getString(R.string.overview_tracker), props.tracker)
        addRow(getString(R.string.overview_comment), props.comment.ifBlank { "—" })
        addRow(
            getString(R.string.overview_private),
            getString(if (props.isPrivate) android.R.string.yes else android.R.string.no),
        )
        addRow(getString(R.string.overview_infohash_v1), props.infohashV1)
        if (props.infohashV2.isNotBlank()) {
            addRow(getString(R.string.overview_infohash_v2), props.infohashV2)
        }
    }

    private fun addRow(label: String, value: String) {
        val ctx = requireContext()
        val labelColor = ContextCompat.getColor(ctx, R.color.md_theme_onSurfaceVariant)
        val valueColor = ContextCompat.getColor(ctx, R.color.md_theme_onSurface)

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val l = TextView(ctx).apply {
            text = label
            setTextColor(labelColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val v = TextView(ctx).apply {
            text = value
            setTextColor(valueColor)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            setTextIsSelectable(true)
        }
        row.addView(l)
        row.addView(v)
        binding.overviewRows.addView(row)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
