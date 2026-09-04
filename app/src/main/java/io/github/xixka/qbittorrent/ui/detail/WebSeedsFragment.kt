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
import io.github.xixka.qbittorrent.databinding.FragmentWebSeedsBinding
import kotlinx.coroutines.launch

/**
 * Web seeds (HTTP sources) tab, qBitController TorrentWebSeedsTab parity:
 * list + add / edit / remove actions, selection via long-press like the
 * trackers tab. Web seeds let the torrent fetch data over plain HTTP in
 * addition to peers.
 */
class WebSeedsFragment : Fragment() {

    private var _binding: FragmentWebSeedsBinding? = null
    private val binding get() = _binding!!

    // Resolve the shared state through the host activity so the
    // hash-carrying factory is always used (see DetailActivity.detailViewModel).
    private val viewModel: DetailViewModel
        get() = (requireActivity() as DetailActivity).detailViewModel
    private val adapter = WebSeedsAdapter(
        isSelected = { it.url in selected },
        onClick = { if (selected.isNotEmpty()) toggleWebSeed(it.url) },
        onLongClick = { toggleWebSeed(it.url) },
    )
    private val selected = HashSet<String>()
    private var actionMode: androidx.appcompat.view.ActionMode? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWebSeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.webSeedList.layoutManager = LinearLayoutManager(requireContext())
        binding.webSeedList.adapter = adapter
        binding.webSeedList.setEmptyView(binding.emptyViewWebSeedList)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> adapter.submitList(state.webSeeds) }
            }
        }
    }

    private fun showAddWebSeedDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_web_seed_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty()) viewModel.addWebSeeds(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Replace a web seed URL (engine maps origUrl -> newUrl in one call). */
    private fun showEditWebSeedDialog(origUrl: String) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(origUrl)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_web_seed_url)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newUrl = input?.text?.toString()?.trim().orEmpty()
                if (newUrl.isNotEmpty() && newUrl != origUrl) {
                    viewModel.editWebSeed(origUrl, newUrl)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleWebSeed(url: String) {
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
            mode.menuInflater.inflate(R.menu.torrent_detail_webseeds_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu) = false

        override fun onActionItemClicked(
            mode: androidx.appcompat.view.ActionMode,
            item: MenuItem,
        ): Boolean = when (item.itemId) {
            R.id.add_web_seed_menu -> {
                showAddWebSeedDialog()
                true
            }

            R.id.share_web_seed_menu -> {
                shareSelected()
                true
            }

            R.id.delete_web_seed -> {
                viewModel.removeWebSeeds(selected.toList())
                selected.clear()
                mode.finish()
                true
            }

            R.id.edit_web_seed_url -> {
                val single = selected.singleOrNull()
                if (single != null) showEditWebSeedDialog(single)
                mode.finish()
                true
            }

            R.id.select_all_web_seeds_menu -> {
                viewModel.state.value.webSeeds.forEach { selected.add(it.url) }
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
        val urls = viewModel.state.value.webSeeds
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
