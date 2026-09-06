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
 * Web seeds tab, qBC TorrentWebSeedsTab parity: URL cards with share /
 * edit / delete / select-all selection mode. The add action lives in the
 * toolbar (hosted by the activity).
 */
class WebSeedsFragment : Fragment() {

    private var _binding: FragmentWebSeedsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailWebSeedsViewModel
        get() = (requireActivity() as DetailActivity).webSeedsViewModel

    private val selected = LinkedHashSet<String>()
    private var actionMode: androidx.appcompat.view.ActionMode? = null

    private lateinit var adapter: WebSeedsAdapter

    /** qBC: finishes the selection when the user swipes to another tab. */
    private var unregisterPageSwipe: (() -> Unit)? = null

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

        adapter = WebSeedsAdapter(
            selected = selected,
            onClick = { if (selected.isNotEmpty()) toggleWebSeed(it.url) },
            onLongClick = { toggleWebSeed(it.url) },
        )
        binding.webSeedList.layoutManager = LinearLayoutManager(requireContext())
        binding.webSeedList.adapter = adapter
        binding.webSeedList.setEmptyView(binding.emptyViewWebSeedList)
        binding.webSeedList.setLoadingView(null)
        binding.loadingIndicator.setVisibilityAfterHide(View.INVISIBLE)

        binding.webSeedsRefresh.setOnRefreshListener { viewModel.refresh() }

        unregisterPageSwipe = finishSelectionOnPageSwipe(DetailActivity.TAB_WEBSEEDS) {
            actionMode?.finish()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.webSeeds.collect { webSeeds ->
                        if (webSeeds != null) {
                            selected.retainAll { url -> webSeeds.any { it.url == url } }
                            adapter.submitList(webSeeds)
                            if (selected.isEmpty()) actionMode?.finish()
                        }
                    }
                }
                // qBC: indeterminate bar during the first (natural) load
                launch {
                    viewModel.isNaturalLoading.collect { loading ->
                        if (loading == true) {
                            binding.loadingIndicator.show()
                            binding.webSeedList.setLoading(true)
                        } else {
                            binding.loadingIndicator.hide()
                            binding.webSeedList.setLoading(false)
                        }
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { binding.webSeedsRefresh.isRefreshing = it }
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

    private fun toggleWebSeed(url: String) {
        if (!selected.add(url)) selected.remove(url)
        adapter.notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        // qBC: the auto-refresh loop pauses while a selection is active
        viewModel.setSelectionActive(selected.isNotEmpty())
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

        override fun onPrepareActionMode(
            mode: androidx.appcompat.view.ActionMode,
            menu: Menu,
        ): Boolean {
            // qBC: the engine's editWebSeed maps exactly ONE url
            menu.findItem(R.id.edit_web_seed_url)?.isEnabled = selected.size == 1
            return false
        }

        override fun onActionItemClicked(
            mode: androidx.appcompat.view.ActionMode,
            item: MenuItem,
        ): Boolean = when (item.itemId) {
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
                adapter.currentList.forEach { selected.add(it.url) }
                adapter.notifyDataSetChanged()
                onSelectionChanged()
                true
            }

            R.id.select_inverse_web_seeds_menu -> {
                val old = selected.toSet()
                selected.clear()
                adapter.currentList.forEach { if (it.url !in old) selected.add(it.url) }
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
            // every exit path (back key, page swipe, dialog confirm) must
            // release the poll gate, not just item taps
            viewModel.setSelectionActive(false)
        }
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
        unregisterPageSwipe?.invoke()
        unregisterPageSwipe = null
        _binding = null
        super.onDestroyView()
    }
}
