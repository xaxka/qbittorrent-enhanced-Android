package io.github.xixka.qbittorrent.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.data.TorrentRepository
import io.github.xixka.qbittorrent.databinding.ActivityMainBinding
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.qbt.LocalEngineManager
import io.github.xixka.qbittorrent.qbt.LocalEngineService
import io.github.xixka.qbittorrent.ui.addtorrent.AddTorrentActivity
import io.github.xixka.qbittorrent.ui.detail.DetailActivity
import io.github.xixka.qbittorrent.ui.settings.SettingsActivity
import io.github.xixka.qbittorrent.util.Format
import kotlinx.coroutines.launch

/**
 * Torrent list, modeled after the LibreTorrent main screen:
 * toolbar with live speed line, status filter tabs, card list with an
 * inline pause button, add-torrent FAB and a contextual bar for bulk actions.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TorrentListViewModel by viewModels { TorrentListViewModel.factory(application) }

    private lateinit var adapter: TorrentListAdapter
    private var actionMode: ActionMode? = null

    private var categories: List<QBCategory> = emptyList()

    private val selectionCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_context_torrents, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val hashes = adapter.selectedHashes()
            if (hashes.isEmpty()) {
                mode.finish()
                return true
            }
            val repo = ServiceLocator.repository(this@MainActivity)
            lifecycleScope.launch {
                runCatching {
                    when (item.itemId) {
                        R.id.action_pause -> repo.pause(hashes)
                        R.id.action_resume -> repo.resume(hashes)
                        R.id.action_force_start -> repo.setForceStart(hashes, true)
                        R.id.action_recheck -> repo.recheck(hashes)
                        R.id.action_reannounce -> repo.reannounce(hashes)
                        R.id.action_sequential -> repo.toggleSequential(hashes)
                        R.id.action_top_priority -> repo.topPriority(hashes)
                        R.id.action_bottom_priority -> repo.bottomPriority(hashes)
                        R.id.action_category -> assignCategory(hashes)
                        R.id.action_delete -> confirmDelete(hashes) {}
                        else -> return@launch
                    }
                }
                viewModel.refresh()
            }
            if (item.itemId != R.id.action_category && item.itemId != R.id.action_delete) {
                mode.finish()
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.clearSelection()
            actionMode = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = TorrentListAdapter(
            onClick = { t -> openDetail(t) },
            onTogglePause = { t, paused ->
                val repo = ServiceLocator.repository(this)
                lifecycleScope.launch {
                    runCatching { if (paused) repo.resume(listOf(t.hash)) else repo.pause(listOf(t.hash)) }
                    viewModel.refresh()
                }
            },
            onLongClick = { t ->
                if (actionMode == null) actionMode = startSupportActionMode(selectionCallback)
                adapter.toggleSelection(t.hash)
                updateSelectionMode()
            },
        )
        binding.torrentList.layoutManager = LinearLayoutManager(this)
        binding.torrentList.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        binding.fab.setOnClickListener {
            startActivity(Intent(this, AddTorrentActivity::class.java))
        }

        setupTabs()

        binding.errorRetry.setOnClickListener {
            viewModel.refresh()
        }
        binding.errorSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        observeState()
    }

    private fun setupTabs() {
        val tabs = listOf(
            "all" to R.string.filter_all,
            "downloading" to R.string.filter_downloading,
            "seeding" to R.string.filter_seeding,
            "completed" to R.string.filter_completed,
            "paused" to R.string.filter_paused,
            "active" to R.string.filter_active,
            "inactive" to R.string.filter_inactive,
            "errored" to R.string.filter_errored,
        )
        tabs.forEach { (key, res) ->
            binding.filterTabs.newTab().setTag(key).setText(res).also {
                binding.filterTabs.addTab(it)
            }
        }
        binding.filterTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                (tab.tag as? String)?.let { viewModel.setFilter(it) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: ListUiState) {
        binding.swipeRefresh.isRefreshing = false

        supportActionBar?.apply {
            subtitle = buildString {
                if (!state.configured) {
                    append(getString(R.string.status_not_configured))
                } else if (state.authError) {
                    append(getString(R.string.status_auth_failed))
                } else if (!state.connected) {
                    append(getString(R.string.status_offline))
                } else {
                    state.transfer?.let {
                        append("↓ ${Format.speed(it.dlInfoSpeed)}  ↑ ${Format.speed(it.upInfoSpeed)}")
                    }
                }
            }
        }

        adapter.submitList(state.torrents)

        val empty = state.torrents.isEmpty()
        binding.emptyView.visibility =
            if (empty && !state.loading && state.connected) View.VISIBLE else View.GONE
        binding.emptyText.setText(
            when {
                !state.configured -> R.string.empty_not_configured
                state.authError -> R.string.empty_auth_error
                !state.connected -> R.string.empty_offline
                else -> R.string.empty_no_torrents
            }
        )

        binding.errorView.visibility =
            if (empty && state.error != null && !state.connected) View.VISIBLE else View.GONE
        binding.errorText.text = state.error ?: ""
    }

    private fun updateSelectionMode() {
        actionMode?.title = getString(R.string.selected_count, adapter.selectedCount())
        if (adapter.selectedCount() == 0) actionMode?.finish()
    }

    private fun openDetail(t: io.github.xixka.qbittorrent.model.TorrentInfo) {
        DetailActivity.start(this, t.hash, t.name)
    }

    // ---------------- menu ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_local_engine)?.isVisible =
            BuildConfig.IS_ENHANCED && LocalEngineManager.isSupported(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            viewModel.refresh()
            true
        }

        R.id.action_sort -> {
            showSortDialog()
            true
        }

        R.id.action_categories -> {
            showCategoryDialog()
            true
        }

        R.id.action_speed_limits -> {
            showSpeedLimitsDialog()
            true
        }

        R.id.action_local_engine -> {
            if (LocalEngineManager.isRunning()) {
                LocalEngineService.stop(this)
            } else {
                LocalEngineService.start(this)
                Toast.makeText(this, R.string.engine_starting, Toast.LENGTH_SHORT).show()
            }
            true
        }

        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun showSortDialog() {
        val fields = listOf(
            "name" to getString(R.string.sort_name),
            "size" to getString(R.string.sort_size),
            "progress" to getString(R.string.sort_progress),
            "dlspeed" to getString(R.string.sort_dlspeed),
            "upspeed" to getString(R.string.sort_upspeed),
            "eta" to getString(R.string.sort_eta),
            "ratio" to getString(R.string.sort_ratio),
            "added_on" to getString(R.string.sort_added_on),
        )
        val names = fields.map { it.second }.toTypedArray()
        val checked = fields.indexOfFirst { it.first == viewModel.sort }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_dialog_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                viewModel.setSort(fields[which].first, viewModel.sortReverse)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.sort_reverse) { _, _ ->
                viewModel.setSort(viewModel.sort, !viewModel.sortReverse)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCategoryDialog() {
        lifecycleScope.launch {
            categories = runCatching {
                ServiceLocator.repository(this@MainActivity).categories()
            }.getOrDefault(emptyMap()).values.toList()
            val items = mutableListOf(getString(R.string.category_all))
            items.addAll(categories.map { it.name })
            items.add(getString(R.string.category_none))
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.category_dialog_title)
                .setItems(items.toTypedArray()) { dialog, which ->
                    viewModel.setCategory(
                        when {
                            which == 0 -> null
                            which == items.size - 1 -> ""
                            else -> categories[which - 1].name
                        }
                    )
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun assignCategory(hashes: List<String>) {
        lifecycleScope.launch {
            val names = runCatching {
                ServiceLocator.repository(this@MainActivity).categories().keys.toList()
            }.getOrDefault(emptyList())
            val view = layoutInflater.inflate(R.layout.dialog_input, null)
            val input = view.findViewById<TextInputEditText>(R.id.input)
            if (names.isNotEmpty()) {
                val adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_list_item_1,
                    names,
                )
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.assign_category_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        runCatching {
                            ServiceLocator.repository(this@MainActivity)
                                .setCategory(hashes, input.text?.toString()?.trim().orEmpty())
                        }
                        viewModel.refresh()
                        actionMode?.finish()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun confirmDelete(hashes: List<String>, onDismiss: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_delete, null)
        val deleteFiles = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.delete_files)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        ServiceLocator.repository(this@MainActivity)
                            .delete(hashes, deleteFiles.isChecked)
                    }
                    viewModel.refresh()
                    actionMode?.finish()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSpeedLimitsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_speed_limits, null)
        val down = view.findViewById<TextInputEditText>(R.id.download_limit)
        val up = view.findViewById<TextInputEditText>(R.id.upload_limit)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.speed_limits_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val dl = down.text?.toString()?.toLongOrNull() ?: -1L
                val ul = up.text?.toString()?.toLongOrNull() ?: -1L
                lifecycleScope.launch {
                    runCatching {
                        val repo = ServiceLocator.repository(this@MainActivity)
                        if (dl >= 0) repo.setDownloadLimit(dl * 1024)
                        if (ul >= 0) repo.setUploadLimit(ul * 1024)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        R.string.speed_limits_saved,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .setNeutralButton(R.string.toggle_alt_limits) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        ServiceLocator.repository(this@MainActivity).toggleAltSpeedLimits()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
