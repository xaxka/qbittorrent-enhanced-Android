package io.github.xixka.qbittorrent.ui.detail

import android.content.Intent
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
import io.github.xixka.qbittorrent.databinding.FragmentTrackersBinding
import kotlinx.coroutines.launch

/**
 * Trackers tab, qBC TorrentTrackersTab parity: swarm-stat cards, selection
 * mode with share / edit / delete / select all / inverse. The add action
 * lives in the toolbar (hosted by the activity).
 */
class TrackersFragment : Fragment() {

    private var _binding: FragmentTrackersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailTrackersViewModel
        get() = (requireActivity() as DetailActivity).trackersViewModel

    private val selected = LinkedHashSet<String>()
    private var actionMode: androidx.appcompat.view.ActionMode? = null

    private lateinit var adapter: TrackersAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTrackersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TrackersAdapter(
            selected = selected,
            onClick = { if (selected.isNotEmpty()) toggleTracker(it.url) },
            onLongClick = { if (!it.isBuiltIn) toggleTracker(it.url) },
        )
        binding.trackerList.layoutManager = LinearLayoutManager(requireContext())
        binding.trackerList.adapter = adapter
        binding.trackerList.setEmptyView(binding.emptyViewTrackerList)
        binding.trackerList.setLoadingView(null)

        binding.trackersRefresh.setOnRefreshListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.trackers.collect { trackers ->
                        if (trackers != null) {
                            selected.retainAll { url -> trackers.any { it.url == url } }
                            adapter.submitList(trackers)
                            if (selected.isEmpty()) actionMode?.finish()
                        }
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { binding.trackersRefresh.isRefreshing = it }
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

    private fun toggleTracker(url: String) {
        if (!selected.add(url)) selected.remove(url)
        adapter.notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        if (selected.isEmpty()) {
            actionMode?.finish()
            return
        }
        if (actionMode == null) {
            actionMode = (requireActivity() as AppCompatActivity)
                .startSupportActionMode(actionModeCallback)
        }
        actionMode?.title = getString(R.string.selected_count, selected.size)
    }

    private val actionModeCallback = object : androidx.appcompat.view.ActionMode.Callback {
        override fun onCreateActionMode(
            mode: androidx.appcompat.view.ActionMode,
            menu: Menu,
        ): Boolean {
            mode.menuInflater.inflate(R.menu.torrent_detail_trackers_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu) = false

        override fun onActionItemClicked(
            mode: androidx.appcompat.view.ActionMode,
            item: MenuItem,
        ): Boolean = when (item.itemId) {
            R.id.share_url_menu -> {
                shareSelected()
                true
            }

            R.id.delete_tracker_url -> {
                confirmDeleteTrackers(selected.toList())
                true
            }

            R.id.edit_tracker_url -> {
                val single = selected.singleOrNull()
                if (single != null) showEditTrackerDialog(single)
                mode.finish()
                true
            }

            R.id.select_all_trackers_menu -> {
                adapter.currentList.filter { !it.isBuiltIn }.forEach { selected.add(it.url) }
                adapter.notifyDataSetChanged()
                onSelectionChanged()
                true
            }

            R.id.select_inverse_trackers_menu -> {
                val old = selected.toSet()
                selected.clear()
                adapter.currentList.filter { !it.isBuiltIn }.forEach {
                    if (it.url !in old) selected.add(it.url)
                }
                adapter.notifyDataSetChanged()
                onSelectionChanged()
                true
            }

            else -> false
        }

        override fun onDestroyActionMode(mode: androidx.appcompat.view.ActionMode) {
            selected.clear()
            adapter.notifyDataSetChanged()
            actionMode = null
        }
    }

    private fun confirmDeleteTrackers(urls: List<String>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getQuantityString(R.plurals.torrent_trackers_delete_title, urls.size, urls.size))
            .setMessage(resources.getQuantityString(R.plurals.torrent_trackers_delete_desc, urls.size, urls.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.removeTrackers(urls)
                selected.clear()
                actionMode?.finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Replace a tracker URL (qBC parity): the engine maps origUrl -> newUrl
     * in one call.
     */
    private fun showEditTrackerDialog(origUrl: String) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(origUrl)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_tracker_url)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newUrl = input?.text?.toString()?.trim().orEmpty()
                if (newUrl.isNotEmpty() && newUrl != origUrl) {
                    viewModel.editTracker(origUrl, newUrl)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareSelected() {
        val urls = adapter.currentList
            .filter { it.url in selected }
            .joinToString("\n") { it.url }
        if (urls.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, urls)
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
