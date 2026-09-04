package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.util.Locale
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentTorrentInfoBinding
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.util.Format
import kotlinx.coroutines.launch

/**
 * Overview tab, ported from LibreTorrent's TorrentDetailsInfoFragment
 * (GPL-3.0): stacked filled cards — name (rename), save path (relocate),
 * options (sequential / first-last / super seeding), category + tag chips,
 * size/hash, dates, comment.
 */
class InfoFragment : Fragment() {

    private var _binding: FragmentTorrentInfoBinding? = null
    private val binding get() = _binding!!

    // Resolve the shared state through the host activity so the
    // hash-carrying factory is always used (see DetailActivity.detailViewModel).
    private val viewModel: DetailViewModel
        get() = (requireActivity() as DetailActivity).detailViewModel

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

        // rename / relocate / per-torrent limits live in the toolbar menu —
        // the buttons here open the same dialogs directly
        binding.editNameButton.setOnClickListener { showRenameDialog() }
        binding.folderChooserButton.setOnClickListener { showLocationDialog() }
        binding.freeSpace.visibility = View.GONE

        binding.sequentialDownload.setOnCheckedChangeListener { _, _ -> viewModel.toggleSequential() }
        binding.downloadFirstLastPieces.setOnCheckedChangeListener { _, _ -> viewModel.toggleFirstLast() }
        binding.superSeeding.setOnCheckedChangeListener { _, checked -> viewModel.setSuperSeeding(checked) }

        binding.addTagButton.setOnClickListener { showAddTagDialog() }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: DetailUiState) {
        val info = state.info ?: return
        renderTransferRows(state)
        binding.name.text = info.name
        binding.savePath.text = state.properties?.savePath ?: info.savePath
        binding.size.text = Format.size(info.size)
        binding.hashSum.text = info.hash
        binding.dateAdded.text = Format.epochDate(info.addedOn)
        binding.createDate.text =
            state.properties?.creationDate?.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—"
        binding.torrentCreatedInProgram.text =
            state.properties?.createdBy?.takeIf { it.isNotBlank() } ?: "—"
        binding.comment.text =
            state.properties?.comment?.takeIf { it.isNotBlank() } ?: "—"

        binding.sequentialDownload.setOnCheckedChangeListener(null)
        binding.sequentialDownload.isChecked = info.sequential
        binding.sequentialDownload.setOnCheckedChangeListener { _, _ -> viewModel.toggleSequential() }

        binding.downloadFirstLastPieces.setOnCheckedChangeListener(null)
        binding.downloadFirstLastPieces.isChecked = info.firstLastPiecePrio
        binding.downloadFirstLastPieces.setOnCheckedChangeListener { _, _ -> viewModel.toggleFirstLast() }

        binding.superSeeding.setOnCheckedChangeListener(null)
        binding.superSeeding.isChecked = state.properties?.superSeeding ?: info.superSeeding
        binding.superSeeding.setOnCheckedChangeListener { _, checked -> viewModel.setSuperSeeding(checked) }

        val signature = "${state.info?.category}|${state.info?.tags}|${state.tags.joinToString(",")}"
        if (signature != lastChipsSignature) {
            lastChipsSignature = signature
            renderCategories(state.categories)
            renderTags(state)
        }
    }

    /** Avoids re-inflating chips on every 3-second background poll. */
    private var lastChipsSignature: String? = null

    /**
     * qBitController overview parity: the transfer statistics card —
     * progress, speeds, traffic totals, ratio, wasted, seeds/peers, ETA,
     * time active, seeded for, completed on, private flag. Rows are reused
     * across polls (texts updated in place, never re-inflated).
     */
    private fun renderTransferRows(state: DetailUiState) {
        val info = state.info ?: return
        val props = state.properties
        val rows: List<Pair<Int, String>> = listOf(
            R.string.detail_progress to "${(info.progress * 100).let { if (it < 10 && it > 0) String.format(Locale.ROOT, "%.1f", it) else it.toInt().toString() }}%",
            R.string.detail_eta to if (info.eta in 1..8639999) Format.duration(info.eta) else "—",
            R.string.detail_speed to "↓ ${Format.speed(info.dlSpeed)} • ↑ ${Format.speed(info.upSpeed)}",
            R.string.detail_downloaded_total to Format.size(info.downloaded),
            R.string.detail_uploaded_total to Format.size(info.uploaded),
            R.string.detail_session_downloaded to Format.size(props?.downloadedSession ?: 0L),
            R.string.detail_session_uploaded to Format.size(props?.uploadedSession ?: 0L),
            R.string.detail_ratio to String.format(Locale.ROOT, "%.2f", info.ratio),
            R.string.detail_wasted to Format.size(props?.totalWasted ?: 0L),
            R.string.detail_seeds to "${info.numSeeds} / ${info.numSeedsTotal}",
            R.string.detail_peers to "${info.numLeechs} / ${info.numLeechsTotal}",
            R.string.detail_time_active to Format.duration(props?.timeActive ?: 0L),
            R.string.detail_seeded_for to Format.duration(props?.seedingTime ?: 0L),
            R.string.detail_completed_on to
                (info.completionOn.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—"),
            // qBitController overview parity: swarm availability, last
            // activity, last seen complete, v2 info hash, connection count
            R.string.detail_availability to
                (info.availability.takeIf { it >= 0 }?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"),
            R.string.detail_last_activity to
                (info.lastActivity.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—"),
            R.string.detail_last_seen_complete to
                (state.properties?.lastSeenComplete?.takeIf { it > 0 }?.let { Format.epochDate(it) } ?: "—"),
            R.string.detail_info_hash_v2 to
                (state.properties?.infohashV2?.takeIf { it.isNotBlank() } ?: "—"),
            R.string.detail_connections to (props?.connections ?: 0L).toString(),
            R.string.detail_private to getString(
                if (info.isPrivate == true) R.string.detail_yes else R.string.detail_no
            ),
        )
        val container = binding.transferRows
        if (container.childCount != rows.size) {
            container.removeAllViews()
            rows.forEach { layoutInflater.inflate(R.layout.item_detail_param, container, true) }
        }
        rows.forEachIndexed { index, (labelRes, value) ->
            val row = container.getChildAt(index)
            row.findViewById<TextView>(R.id.param_label).setText(labelRes)
            row.findViewById<TextView>(R.id.param_value).text = value
        }
    }

    private fun renderCategories(categories: List<QBCategory>) {
        val info = viewModel.state.value.info ?: return
        val group = binding.tagsChipGroup
        group.removeAllViews()
        val inflater = layoutInflater
        val current = info.category
        if (categories.isEmpty()) {
            if (current.isBlank()) return
            addChip(inflater, group, current, checked = true)
            return
        }
        for (c in categories) {
            addChip(inflater, group, c.name, checked = c.name == current)
        }
        if (current.isBlank()) {
            addChip(inflater, group, getString(R.string.no_categories), checked = false)
        }
    }

    private fun addChip(inflater: LayoutInflater, group: com.google.android.material.chip.ChipGroup, name: String, checked: Boolean) {
        val chip = inflater.inflate(R.layout.item_tag_chip, group, false) as Chip
        chip.text = name
        chip.isChecked = checked
        chip.setOnClickListener {
            viewModel.setCategory(if (checked) "" else name)
        }
        group.addView(chip)
    }

    /** Torrent tags: the torrent's own tags + the server's other tags. */
    private fun renderTags(state: DetailUiState) {
        val info = state.info ?: return
        val own = info.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val others = state.tags.filter { it !in own }
        val group = binding.tagsChipGroup
        val inflater = layoutInflater
        for (tag in own) {
            val chip = inflater.inflate(R.layout.item_tag_chip, group, false) as Chip
            chip.text = tag
            chip.isChecked = true
            chip.setOnClickListener { viewModel.removeTag(tag) }
            group.addView(chip)
        }
        for (tag in others) {
            val chip = inflater.inflate(R.layout.item_tag_chip, group, false) as Chip
            chip.text = tag
            chip.isChecked = false
            chip.setOnClickListener { viewModel.addTags(listOf(tag)) }
            group.addView(chip)
        }
    }

    private fun showRenameDialog() {
        val info = viewModel.state.value.info ?: return
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(info.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename_torrent)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.rename(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLocationDialog() {
        val state = viewModel.state.value
        val current = state.properties?.savePath ?: state.info?.savePath.orEmpty()
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(current)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.change_save_location)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val path = input?.text?.toString()?.trim().orEmpty()
                if (path.isNotEmpty()) viewModel.setLocation(path)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddTagDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_tag)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    viewModel.addTags(listOf(name))
                    Toast.makeText(requireContext(), R.string.tag_applied, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
