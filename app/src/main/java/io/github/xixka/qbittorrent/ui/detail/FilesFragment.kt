package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentFilesBinding
import io.github.xixka.qbittorrent.model.TorrentFile
import kotlinx.coroutines.launch

/**
 * Files tab, ported from LibreTorrent's DetailTorrentFilesFragment (GPL-3.0):
 * EmptyRecyclerView list + action mode with priority change / select all.
 */
class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    // Resolve the shared state through the host activity so the
    // hash-carrying factory is always used (see DetailActivity.detailViewModel).
    private val viewModel: DetailViewModel
        get() = (requireActivity() as DetailActivity).detailViewModel
    private lateinit var adapter: FilesAdapter
    private var actionMode: androidx.appcompat.view.ActionMode? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FilesAdapter(
            onSelect = { file ->
                adapter.toggleSelection(file.index)
                onSelectionChanged()
            },
            onClick = { file -> changePriority(listOf(file)) },
        )
        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
        binding.fileList.adapter = adapter
        binding.fileList.setEmptyView(binding.emptyViewFileList)
        binding.fileList.setLoadingView(null)

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.files)
                }
            }
        }
    }

    private fun onSelectionChanged() {
        if (adapter.selectedCount() > 0) {
            if (actionMode == null) {
                actionMode = (requireActivity() as AppCompatActivity)
                    .startSupportActionMode(actionModeCallback)
            }
            actionMode?.title = getString(R.string.selected_count, adapter.selectedCount())
        } else {
            actionMode?.finish()
        }
        adapter.notifyDataSetChanged()
    }

    private val actionModeCallback = object : androidx.appcompat.view.ActionMode.Callback {
        override fun onCreateActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.torrent_details_files_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu) = false

        override fun onActionItemClicked(
            mode: androidx.appcompat.view.ActionMode,
            item: MenuItem,
        ): Boolean = when (item.itemId) {
            R.id.select_all_files_menu -> {
                adapter.selectAll()
                onSelectionChanged()
                true
            }

            R.id.rename_file_menu -> {
                // qBitController parity: exactly one selected file is renamed
                val indexes = adapter.selectedIndexes()
                val files = viewModel.state.value.files
                val selected = indexes.mapNotNull { idx -> files.firstOrNull { it.index == idx } }
                if (selected.size == 1) showRenameFileDialog(selected.first())
                true
            }

            R.id.change_priority_menu -> {
                val indexes = adapter.selectedIndexes()
                if (indexes.isNotEmpty()) {
                    // file.index is the torrent's own file id, not a list
                    // position — resolve via the current snapshot
                    val files = viewModel.state.value.files
                    val selected = indexes.mapNotNull { idx -> files.firstOrNull { it.index == idx } }
                    if (selected.isNotEmpty()) changePriority(selected)
                }
                true
            }

            else -> false
        }

        override fun onDestroyActionMode(mode: androidx.appcompat.view.ActionMode) {
            adapter.clearSelection()
            actionMode = null
        }
    }

    /**
     * Rename a single file (qBitController TorrentFilesTab parity): the
     * engine endpoint is torrents/renameFile with the file index + the new
     * name (path relative to the torrent root allowed).
     */
    private fun showRenameFileDialog(file: TorrentFile) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(file.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename_file)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.renameFile(file.index, name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Priority dialog: qBittorrent priorities — skip(0), low(1), normal(4),
     * high(7), maximal(8)? The API accepts 0..7: 0 skip, 1 low, 2..6 vary,
     * 7 max. Present the same four-choice UI as LibreTorrent.
     */
    private fun changePriority(files: List<TorrentFile>) {
        val labels = arrayOf(
            getString(R.string.file_priority_skip),
            getString(R.string.file_priority_low),
            getString(R.string.file_priority_normal),
            getString(R.string.file_priority_high),
            getString(R.string.file_priority_max),
        )
        val values = intArrayOf(0, 1, 4, 6, 7)
        val current = files.firstOrNull()?.priority ?: 1
        val checked = values.indexOfFirst { it == current }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.change_priority)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setFilePriority(
                    files.map { it.index },
                    values[which],
                )
                dialog.dismiss()
                actionMode?.finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
