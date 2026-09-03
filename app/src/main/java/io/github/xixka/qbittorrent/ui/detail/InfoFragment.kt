package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
 * (GPL-3.0): stacked filled cards — name, save path, options, size/hash,
 * dates, comment, category chips.
 */
class InfoFragment : Fragment() {

    private var _binding: FragmentTorrentInfoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by activityViewModels()

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

        // qBittorrent can rename torrents, but the API call is not wired yet
        binding.editNameButton.visibility = View.GONE
        // save path is read-only over the remote API
        binding.folderChooserButton.visibility = View.GONE
        binding.freeSpace.visibility = View.GONE

        binding.sequentialDownload.setOnCheckedChangeListener { _, _ -> viewModel.toggleSequential() }
        binding.downloadFirstLastPieces.setOnCheckedChangeListener { _, _ -> viewModel.toggleFirstLast() }

        binding.addTagButton.setOnClickListener { showAddCategoryDialog() }

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

        renderCategories(state.categories)
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

    private fun showAddCategoryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_category)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    viewModel.setCategory(name)
                    Toast.makeText(
                        requireContext(),
                        R.string.speed_limits_saved,
                        Toast.LENGTH_SHORT,
                    ).show()
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
