package io.github.xixka.qbittorrent.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityMainBinding
import io.github.xixka.qbittorrent.databinding.DialogAddLinkBinding
import io.github.xixka.qbittorrent.databinding.HomeDrawerContentBinding
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.qbt.LocalEngineManager
import io.github.xixka.qbittorrent.qbt.LocalEngineService
import io.github.xixka.qbittorrent.ui.addtorrent.AddTorrentActivity
import io.github.xixka.qbittorrent.ui.detail.DetailActivity
import io.github.xixka.qbittorrent.ui.settings.SettingsActivity
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.UpdateChecker
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch

/**
 * Pixel-perfect port of LibreTorrent's home screen: navigation drawer with
 * stats + filter chips, search bar, contextual toolbar, FAB popup menu and
 * a search view over the torrent list.
 *
 * Like LibreTorrent (Utils.enableEdgeToEdge), the window is laid out
 * edge-to-edge so the app bar's 48dp search-bar margin — measured from the
 * top of the screen — matches the original design instead of stacking on
 * top of the status bar reservation and leaving a blank strip.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerBinding: HomeDrawerContentBinding
    private val viewModel: TorrentListViewModel by viewModels { TorrentListViewModel.factory(application) }

    private lateinit var adapter: TorrentListAdapter
    private lateinit var searchAdapter: TorrentListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge, exactly like LibreTorrent's Utils.enableEdgeToEdge():
        // the AppBarLayout consumes the status bar inset (fitsSystemWindows +
        // statusBarForeground), see fragment_home.xml.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigation drawer content (stats + filters), LibreTorrent style
        drawerBinding = HomeDrawerContentBinding.inflate(layoutInflater, binding.navigationView, true)
        // Drawer content starts below the status bar (LibreTorrent parity)
        applyWindowInsets(binding.navigationView, drawerBinding.root)

        adapter = TorrentListAdapter(
            onClick = { openDetail(it) },
            onTogglePause = { t, paused ->
                lifecycleScope.launch {
                    runCatching {
                        val repo = ServiceLocator.repository(this@MainActivity)
                        if (paused) repo.resume(listOf(t.hash)) else repo.pause(listOf(t.hash))
                    }
                }
            },
            onLongClick = { onSelectionChanged() },
        )
        binding.homeContent.torrentList.layoutManager = LinearLayoutManager(this)
        binding.homeContent.torrentList.adapter = adapter
        binding.homeContent.torrentList.setEmptyView(binding.homeContent.emptyViewTorrentList)
        binding.homeContent.torrentList.setLoadingView(binding.homeContent.loadingViewTorrentList)
        // Inset-aware list decoration, LibreTorrent style (Utils.buildListDivider)
        binding.homeContent.torrentList.addItemDecoration(
            MaterialDividerItemDecoration(this, LinearLayoutManager.VERTICAL).apply {
                dividerInsetStart = 32
                dividerInsetEnd = 32
                isLastItemDecorated = false
            }
        )
        applyWindowInsets(binding.homeContent.torrentList, WindowInsetsSide.LEFT or WindowInsetsSide.RIGHT)

        searchAdapter = TorrentListAdapter(
            onClick = { openDetail(it) },
            onTogglePause = { t, paused ->
                lifecycleScope.launch {
                    runCatching {
                        val repo = ServiceLocator.repository(this@MainActivity)
                        if (paused) repo.resume(listOf(t.hash)) else repo.pause(listOf(t.hash))
                    }
                }
            },
            onLongClick = {},
        )
        binding.homeContent.searchTorrentList.layoutManager = LinearLayoutManager(this)
        binding.homeContent.searchTorrentList.adapter = searchAdapter
        binding.homeContent.searchTorrentList.setEmptyView(binding.homeContent.emptyViewSearchTorrentList)

        binding.homeContent.searchView.setupWithSearchBar(binding.homeContent.searchBar)
        binding.homeContent.searchBar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
        binding.homeContent.searchBar.setOnMenuItemClickListener { onHomeMenuItem(it.itemId) }
        binding.homeContent.searchBar.menu.findItem(R.id.action_local_engine)?.isVisible =
            BuildConfig.IS_ENHANCED && LocalEngineManager.isSupported(this)

        binding.homeContent.searchView.editText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.setSearchQuery(v.text.toString().trim())
                true
            } else false
        }

        binding.homeContent.addTorrentFab.setOnClickListener { showFabMenu(it) }

        binding.homeContent.contextualAppBar.setNavigationOnClickListener {
            adapter.clearSelection()
            onSelectionChanged()
        }
        binding.homeContent.contextualAppBar.setOnMenuItemClickListener { onContextualMenuItem(it.itemId) }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home_nav -> true
                R.id.settings_nav -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        setupDrawer()

        observeState()

        // Load the bundled engine together with the app — no manual step.
        maybeAutoStartEngine()

        // Non-intrusive daily update check against GitHub Releases.
        maybeAutoCheckUpdate()
    }

    private fun onHomeMenuItem(itemId: Int): Boolean {
        val repo = ServiceLocator.repository(this)
        when (itemId) {
            R.id.pause_all_menu -> lifecycleScope.launch { runCatching { repo.pauseAll() } }
            R.id.resume_all_menu -> lifecycleScope.launch { runCatching { repo.resumeAll() } }
            R.id.about_menu -> showAboutDialog()
            R.id.settings_menu -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.check_update_menu -> checkUpdate(manual = true)
            R.id.action_local_engine -> toggleLocalEngine()
            else -> return false
        }
        return true
    }

    private fun toggleLocalEngine() {
        if (LocalEngineManager.isRunning()) {
            LocalEngineService.stop(this)
        } else {
            LocalEngineService.start(this)
            Toast.makeText(this, R.string.engine_starting, Toast.LENGTH_SHORT).show()
        }
    }

    /** Starts the bundled engine once per process (Enhanced edition only). */
    private fun maybeAutoStartEngine() {
        if (engineAutoStarted) return
        engineAutoStarted = true
        if (!BuildConfig.IS_ENHANCED) return
        if (LocalEngineManager.isSupported(this) && !LocalEngineManager.isRunning()) {
            runCatching { LocalEngineService.start(this) }
        }
    }

    // ---------------- update check (GitHub Releases) ----------------

    private fun maybeAutoCheckUpdate() {
        val prefs = ServiceLocator.prefs(this)
        if (System.currentTimeMillis() - prefs.lastUpdateCheck < UPDATE_CHECK_INTERVAL_MS) return
        checkUpdate(manual = false)
    }

    private fun checkUpdate(manual: Boolean) {
        ServiceLocator.prefs(this).lastUpdateCheck = System.currentTimeMillis()
        lifecycleScope.launch {
            val result = runCatching { UpdateChecker.check() }
            val alive = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            result
                .onSuccess { update ->
                    when {
                        update != null && alive -> showUpdateDialog(update)
                        manual -> Toast.makeText(
                            this@MainActivity,
                            getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .onFailure {
                    if (manual) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.update_check_failed, it.message ?: ""),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
        }
    }

    private fun showUpdateDialog(update: UpdateChecker.Update) {
        val notes = if (update.notes.isBlank()) "" else "\n\n" + update.notes.take(600)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_message, update.version) + notes)
            .setPositiveButton(R.string.update_download) { _, _ -> openUrl(update.apkUrl ?: update.htmlUrl) }
            .setNeutralButton(R.string.update_release_page) { _, _ -> openUrl(update.htmlUrl) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about)
            .setMessage(
                getString(R.string.about_message) +
                    "\n\n" + getString(R.string.about_version, BuildConfig.VERSION_NAME)
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun onContextualMenuItem(itemId: Int): Boolean {
        val hashes = adapter.selectedHashes()
        if (hashes.isEmpty()) return true
        val repo = ServiceLocator.repository(this)
        when (itemId) {
            R.id.delete_torrent_menu -> confirmDelete(hashes)
            R.id.force_recheck_torrent_menu ->
                lifecycleScope.launch { runCatching { repo.recheck(hashes) }; viewModel.refresh() }
            R.id.force_announce_torrent_menu ->
                lifecycleScope.launch { runCatching { repo.reannounce(hashes) }; viewModel.refresh() }
            R.id.select_all_torrent_menu -> {
                adapter.selectAll(viewModel.state.value.torrents)
                onSelectionChanged()
            }
            else -> return false
        }
        return true
    }

    private fun onSelectionChanged() {
        val count = adapter.selectedCount()
        binding.homeContent.contextualAppBarContainer.visibility =
            if (count > 0) View.VISIBLE else View.GONE
        binding.homeContent.appBarLayout.visibility =
            if (count > 0) View.GONE else View.VISIBLE
        binding.homeContent.contextualAppBar.title = getString(R.string.selected_count, count)
    }

    private fun showFabMenu(anchor: View) {
        val ctx = ContextThemeWrapper(
            this,
            R.style.App_Components_FloatingActionButton_Menu,
        )
        val popup = PopupMenu(ctx, anchor)
        popup.menuInflater.inflate(R.menu.home_fab, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.add_link -> showAddLinkDialog()
                R.id.open_file -> pickTorrentFile()
            }
            true
        }
        popup.show()
    }

    private fun showAddLinkDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_link, null)
        val linkInput = view.findViewById<TextInputEditText>(R.id.link)
        view.findViewById<View>(R.id.clipboard_button)?.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            (cm.primaryClip?.getItemAt(0)?.coerceToText(this))?.let { linkInput.setText(it) }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_link)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val link = linkInput.text?.toString()?.trim().orEmpty()
                if (link.isNotEmpty()) AddTorrentActivity.start(this, link)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickTorrentFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-bittorrent", "application/octet-stream"))
        }
        try {
            startActivityForResult(intent, PICK_TORRENT_FILE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_file_manager, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_TORRENT_FILE && resultCode == RESULT_OK) {
            data?.data?.let { AddTorrentActivity.start(this, uri = it) }
        }
    }

    // ---------------- drawer ----------------

    private fun setupDrawer() {
        val d = drawerBinding

        d.sortDirectionToggleButton.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.setSortDirection(checkedId == R.id.sort_desc_button)
        }

        d.statusClearButton.setOnClickListener { d.drawerStatusChipGroup.clearCheck(); viewModel.setStatusFilter(null) }
        d.tagsClearButton.setOnClickListener { d.drawerTagsChipGroup.clearCheck(); viewModel.setCategory(null) }

        d.drawerStatusChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.setStatusFilter(
                when (checkedIds.firstOrNull()) {
                    R.id.drawer_status_downloading -> StatusFilter.DOWNLOADING
                    R.id.drawer_status_downloading_metadata -> StatusFilter.DOWNLOADING_METADATA
                    R.id.drawer_status_downloaded -> StatusFilter.DOWNLOADED
                    R.id.drawer_status_error -> StatusFilter.ERROR
                    else -> null
                }
            )
        }

        d.drawerDateAddedChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.setDateAddedFilter(
                when (checkedIds.firstOrNull()) {
                    R.id.drawer_date_added_today -> DateAddedFilter.TODAY
                    R.id.drawer_date_added_yesterday -> DateAddedFilter.YESTERDAY
                    R.id.drawer_date_added_week -> DateAddedFilter.WEEK
                    R.id.drawer_date_added_month -> DateAddedFilter.MONTH
                    R.id.drawer_date_added_year -> DateAddedFilter.YEAR
                    else -> null
                }
            )
        }

        d.drawerSortingChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.setSortField(
                when (checkedIds.firstOrNull()) {
                    R.id.drawer_sorting_name -> SortField.NAME
                    R.id.drawer_sorting_size -> SortField.SIZE
                    R.id.drawer_sorting_progress -> SortField.PROGRESS
                    R.id.drawer_sorting_ETA -> SortField.ETA
                    R.id.drawer_sorting_peers -> SortField.PEERS
                    else -> SortField.DATE_ADDED
                }
            )
        }

        d.drawerTagsChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull()
            viewModel.setCategory(
                when (id) {
                    R.id.no_tags_chip -> ""
                    null -> null
                    else -> d.drawerTagsChipGroup.findViewById<Chip?>(id)?.tag as? String
                }
            )
        }

        d.addTagButton.setOnClickListener { showAddCategoryDialog() }
    }

    private fun showAddCategoryDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_category)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        runCatching { ServiceLocator.repository(this@MainActivity).createCategory(name, "") }
                        viewModel.refresh()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateDrawerCategories(names: List<String>) {
        val group = drawerBinding.drawerTagsChipGroup
        // keep the static "no tags" chip
        val existing = group.children.filter { it.id != R.id.no_tags_chip }.toList()
        existing.forEach { group.removeView(it) }
        if (names.isEmpty()) return
        val ctx = this
        names.forEach { name ->
            val chip = LayoutInflater.from(ctx)
                .inflate(R.layout.item_tag_chip, group, false) as Chip
            chip.text = name
            chip.tag = name
            group.addView(chip)
        }
    }

    // ---------------- state ----------------

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ListUiState) {
        binding.homeContent.torrentList.setLoading(state.loading)
        adapter.submitList(state.torrents)
        searchAdapter.submitList(
            if (viewModel.searchQuery.isBlank()) emptyList() else state.torrents
        )

        val emptyText = when {
            !state.configured -> R.string.empty_not_configured
            state.authError -> R.string.empty_auth_error
            !state.connected -> R.string.empty_offline
            else -> R.string.torrent_list_empty
        }
        binding.homeContent.emptyViewTorrentList.setText(emptyText)

        val d = drawerBinding
        state.transfer?.let {
            d.sessionDownloadStat.text = "↓ ${Format.speed(it.dlInfoSpeed)} • ${Format.size(it.dlInfoData)}"
            d.sessionUploadStat.text = "↑ ${Format.speed(it.upInfoSpeed)} • ${Format.size(it.upInfoData)}"
            d.sessionDhtNodesStat.text = getString(R.string.dht_nodes_stat, it.dhtNodes.toString())
        }
        d.sessionListenPortStat.text = getString(R.string.session_listen_port, state.serverVersion)

        updateDrawerCategories(state.categories)
    }

    private fun confirmDelete(hashes: List<String>) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_delete_torrent, null)
        val deleteFiles = view.findViewById<MaterialCheckBox>(R.id.delete_with_downloaded_files)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        ServiceLocator.repository(this@MainActivity).delete(hashes, deleteFiles.isChecked)
                    }
                    adapter.clearSelection()
                    onSelectionChanged()
                    viewModel.refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openDetail(t: TorrentInfo) {
        DetailActivity.start(this, t.hash, t.name)
    }

    companion object {
        private const val PICK_TORRENT_FILE = 42

        /** Auto update check frequency. */
        private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** Per-process guard so stopping the engine is respected until relaunch. */
        @Volatile
        private var engineAutoStarted = false
    }
}
