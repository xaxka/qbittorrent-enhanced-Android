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
 * Trackers tab, ported from LibreTorrent's DetailTrackersFragment (GPL-3.0):
 * list + add-tracker fab action + action mode (share / delete / select all).
 */
class TrackersFragment : Fragment() {

    private var _binding: FragmentTrackersBinding? = null
    private val binding get() = _binding!!

    // Resolve the shared state through the host activity so the
    // hash-carrying factory is always used (see DetailActivity.detailViewModel).
    private val viewModel: DetailViewModel
        get() = (requireActivity() as DetailActivity).detailViewModel
    private val adapter = TrackersAdapter(
        isSelected = { it.url in selected },
        onClick = { if (selected.isNotEmpty()) toggleTracker(it.url) },
        onLongClick = { toggleTracker(it.url) },
    )
    private val selected = HashSet<String>()
    private var actionMode: androidx.appcompat.view.ActionMode? = null

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
        binding.trackerList.layoutManager = LinearLayoutManager(requireContext())
        binding.trackerList.adapter = adapter
        binding.trackerList.setEmptyView(binding.emptyViewTrackerList)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> adapter.submitList(state.trackers) }
            }
        }
    }

    private fun showAddTrackerDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_tracker_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty()) viewModel.addTracker(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Replace a tracker URL (qBitController parity): the engine maps
     * origUrl -> newUrl in one call.
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
                selected.forEach { viewModel.removeTracker(it) }
                selected.clear()
                mode.finish()
                true
            }

            R.id.edit_tracker_url -> {
                val single = selected.singleOrNull()
                if (single != null) showEditTrackerDialog(single)
                mode.finish()
                true
            }

            R.id.select_all_trackers_menu -> {
                viewModel.state.value.trackers.forEach { selected.add(it.url) }
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

    private fun shareSelected() {
        val urls = viewModel.state.value.trackers
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
