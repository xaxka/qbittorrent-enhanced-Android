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
import io.github.xixka.qbittorrent.model.TorrentFileNode
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Files tab, qBC TorrentFilesTab parity: the content as an expandable
 * folder tree. Clicking a folder toggles it; long-press enters selection
 * mode (priority / rename / select all / select inverse). Renaming works
 * for both files and folders through the engine's oldPath/newPath API.
 */
class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailFilesViewModel
        get() = (requireActivity() as DetailActivity).filesViewModel

    private val expandedPaths = LinkedHashSet<String>()
    private val selectedPaths = LinkedHashSet<String>()
    private var actionMode: androidx.appcompat.view.ActionMode? = null

    private lateinit var adapter: FilesTreeAdapter
    private var lastRoot: TorrentFileNode.Folder? = null

    /** qBC: finishes the selection when the user swipes to another tab. */
    private var unregisterPageSwipe: (() -> Unit)? = null

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

        adapter = FilesTreeAdapter(
            selected = selectedPaths,
            expanded = expandedPaths,
            onClick = ::onNodeClick,
            onLongClick = ::onNodeLongClick,
            onToggleExpand = ::toggleExpand,
            onPriorityToggle = { node, priority ->
                viewModel.setPriority(listOf(node.path), priority)
            },
        )
        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
        binding.fileList.adapter = adapter
        binding.fileList.setEmptyView(binding.emptyViewFileList)
        binding.fileList.setLoadingView(null)
        binding.loadingIndicator.setVisibilityAfterHide(View.INVISIBLE)

        binding.filesRefresh.setOnRefreshListener { viewModel.refresh() }

        unregisterPageSwipe = finishSelectionOnPageSwipe(DetailActivity.TAB_FILES) {
            actionMode?.finish()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.root.collect { root ->
                        lastRoot = root
                        if (root != null) {
                            // drop selections of vanished nodes (qBC behavior)
                            val visible = flatten(root)
                            selectedPaths.retainAll { path ->
                                visible.any { it.path == path } || selectedInTree(root, path)
                            }
                        }
                        submitNodes()
                    }
                }
                // qBC: indeterminate bar during the first (natural) load;
                // the "no files" placeholder only shows AFTER data exists
                launch {
                    viewModel.isNaturalLoading.collect { loading ->
                        if (loading == true) {
                            binding.loadingIndicator.show()
                            binding.fileList.setLoading(true)
                        } else {
                            binding.loadingIndicator.hide()
                            binding.fileList.setLoading(false)
                        }
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { binding.filesRefresh.isRefreshing = it }
                }
                // Toolbar sort menu (DetailActivity): re-flatten on change
                launch {
                    viewModel.sortMode.collect { submitNodes() }
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

    private fun selectedInTree(root: TorrentFileNode.Folder, path: String): Boolean =
        root.findChildNode(path) != null

    private fun onNodeClick(node: TorrentFileNode) {
        if (selectedPaths.isNotEmpty()) {
            toggleSelection(node.path)
        } else if (node is TorrentFileNode.Folder) {
            toggleExpand(node)
        }
    }

    private fun onNodeLongClick(node: TorrentFileNode) {
        toggleSelection(node.path)
    }

    private fun toggleExpand(node: TorrentFileNode) {
        if (!expandedPaths.add(node.path)) expandedPaths.remove(node.path)
        submitNodes()
    }

    private fun toggleSelection(path: String) {
        if (!selectedPaths.add(path)) selectedPaths.remove(path)
        submitNodes()
        onSelectionChanged()
    }

    private fun flatten(root: TorrentFileNode): List<TorrentFileNode> {
        val result = mutableListOf<TorrentFileNode>()
        val stack = ArrayDeque<TorrentFileNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node !== root) result.add(node)
            if (node is TorrentFileNode.Folder && (node.level == 0 || node.path in expandedPaths)) {
                sortedChildren(node).asReversed().forEach(stack::add)
            }
        }
        return result
    }

    /** Toolbar sort applied within every folder: folders always group
     *  first, then files by the chosen key (ORDER = engine file order). */
    private fun sortedChildren(folder: TorrentFileNode.Folder): List<TorrentFileNode> =
        when (viewModel.sortMode.value) {
            FilesSortMode.ORDER -> folder.children
            FilesSortMode.NAME -> folder.children.sortedWith(
                compareBy({ it !is TorrentFileNode.Folder }, { it.name.lowercase(Locale.ROOT) })
            )
            FilesSortMode.SIZE -> folder.children.sortedWith(
                compareBy({ it !is TorrentFileNode.Folder }, { -it.size })
            )
            FilesSortMode.PROGRESS -> folder.children.sortedWith(
                compareBy({ it !is TorrentFileNode.Folder }, { it.progress })
            )
        }

    private fun submitNodes() {
        val root = lastRoot ?: return
        adapter.submitList(flatten(root))
    }

    private fun onSelectionChanged() {
        // qBC: the auto-refresh loop pauses while a selection is active
        viewModel.setSelectionActive(selectedPaths.isNotEmpty())
        if (selectedPaths.isNotEmpty()) {
            if (actionMode == null) {
                actionMode = (requireActivity() as AppCompatActivity)
                    .startSupportActionMode(actionModeCallback)
            }
            actionMode?.title = getString(R.string.selected_count, selectedPaths.size)
        } else {
            actionMode?.finish()
        }
    }

    private val actionModeCallback = object : androidx.appcompat.view.ActionMode.Callback {
        override fun onCreateActionMode(
            mode: androidx.appcompat.view.ActionMode,
            menu: Menu,
        ): Boolean {
            mode.menuInflater.inflate(R.menu.torrent_details_files_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(
            mode: androidx.appcompat.view.ActionMode,
            menu: Menu,
        ): Boolean {
            // qBC: rename needs exactly one selected node
            menu.findItem(R.id.rename_file_menu)?.isEnabled = selectedPaths.size == 1
            return false
        }

        override fun onActionItemClicked(
            mode: androidx.appcompat.view.ActionMode,
            item: MenuItem,
        ): Boolean = when (item.itemId) {
            R.id.select_all_files_menu -> {
                adapter.currentList.forEach { selectedPaths.add(it.path) }
                submitNodes()
                onSelectionChanged()
                true
            }

            R.id.rename_file_menu -> {
                if (selectedPaths.size == 1) showRenameDialog(selectedPaths.first())
                true
            }

            R.id.change_priority_menu -> {
                if (selectedPaths.isNotEmpty()) showPriorityDialog()
                true
            }

            R.id.select_inverse_files_menu -> {
                val current = adapter.currentList.map { it.path }
                val oldSelection = selectedPaths.toSet()
                selectedPaths.clear()
                current.forEach { if (it !in oldSelection) selectedPaths.add(it) }
                submitNodes()
                onSelectionChanged()
                true
            }

            else -> false
        }

        override fun onDestroyActionMode(mode: androidx.appcompat.view.ActionMode) {
            selectedPaths.clear()
            submitNodes()
            actionMode = null
        }
    }

    /** qBC priorities: 0 skip, 1 normal, 6 high, 7 maximum. */
    private fun showPriorityDialog() {
        val labels = arrayOf(
            getString(R.string.torrent_files_priority_do_not_download),
            getString(R.string.torrent_files_priority_normal),
            getString(R.string.torrent_files_priority_high),
            getString(R.string.torrent_files_priority_maximum),
        )
        val values = intArrayOf(0, 1, 6, 7)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.torrent_files_action_priority)
            .setItems(labels) { dialog, which ->
                viewModel.setPriority(selectedPaths.toList(), values[which])
                selectedPaths.clear()
                actionMode?.finish()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Rename one file or folder (qBC RenameDialog). */
    private fun showRenameDialog(path: String) {
        val node = lastRoot?.findChildNode(path) ?: return
        val isFolder = node is TorrentFileNode.Folder
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(node.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (isFolder) R.string.torrent_files_rename_folder
                else R.string.torrent_files_rename_file
            )
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    if (isFolder) viewModel.renameFolder(path, name)
                    else viewModel.renameFile(path, name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        actionMode?.finish()
    }

    override fun onDestroyView() {
        unregisterPageSwipe?.invoke()
        unregisterPageSwipe = null
        _binding = null
        super.onDestroyView()
    }
}
