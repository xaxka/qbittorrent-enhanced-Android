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
import androidx.core.view.children
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
import io.github.xixka.qbittorrent.data.ServerProfile
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityMainBinding
import io.github.xixka.qbittorrent.databinding.DialogAddLinkBinding
import io.github.xixka.qbittorrent.databinding.HomeDrawerContentBinding
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.qbt.LocalEngineManager
import io.github.xixka.qbittorrent.qbt.LocalEngineService
import io.github.xixka.qbittorrent.ui.addtorrent.AddTorrentActivity
import io.github.xixka.qbittorrent.ui.detail.DetailActivity
import io.github.xixka.qbittorrent.ui.rss.RssActivity
import io.github.xixka.qbittorrent.ui.search.SearchActivity
import io.github.xixka.qbittorrent.ui.settings.SettingsFragment
import io.github.xixka.qbittorrent.ui.stats.StatisticsActivity
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.UpdateChecker
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch

/**
 * LibreTorrent-style home screen: navigation drawer with transfer stats and
 * filter chips (status / sorting / added date / categories / tags), search
 * bar, contextual toolbar, FAB popup menu and a bottom navigation that hosts
 * its destinations IN PLACE (torrents / RSS / settings) — the settings tab
 * swaps a fragment instead of opening a separate activity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerBinding: HomeDrawerContentBinding
    private val viewModel: TorrentListViewModel by viewModels { TorrentListViewModel.factory(application) }

    private lateinit var adapter: TorrentListAdapter
    private lateinit var searchAdapter: TorrentListAdapter

    private var engineFailurePrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge, exactly like LibreTorrent's Utils.enableEdgeToEdge():
        // the AppBarLayout consumes the status bar inset, the bottom
        // navigation is inset-aware (immersive navigation bar).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Material You dynamic colors (default on, Android 12+)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
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
        applyWindowInsets(
            child = binding.homeContent.torrentList,
            sideMask = WindowInsetsSide.LEFT or WindowInsetsSide.RIGHT,
        )

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

        // Local search filters the list; a tap on the empty view continues
        // the search with the engine's own search plugins (qBitController)
        binding.homeContent.searchView.setupWithSearchBar(binding.homeContent.searchBar)
        binding.homeContent.emptyViewSearchTorrentList.setOnClickListener {
            if (viewModel.searchQuery.isNotBlank()) {
                SearchActivity.start(this, viewModel.searchQuery)
            } else {
                SearchActivity.start(this)
            }
        }
        binding.homeContent.searchBar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
        // No overflow menu: session actions live in the drawer (speed limits
        // under the transfer stats) and everything else in Settings.

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

        // Immersive navigation bar: the bottom nav pads itself with the
        // gesture/navigation bar inset instead of leaving a black strip.
        applyWindowInsets(
            child = binding.bottomNavigation,
            sideMask = WindowInsetsSide.BOTTOM,
        )

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home_nav -> {
                    showDestination(null)
                    true
                }
                R.id.rss_nav -> {
                    // full-screen RSS hub (subscription tree + rules)
                    startActivity(Intent(this, RssActivity::class.java))
                    false // keep the torrent list as the selected tab
                }
                R.id.settings_nav -> {
                    showDestination(SETTINGS_DESTINATION)
                    true
                }
                else -> false
            }
        }

        setupDrawer()
        observeState()

        // Load the bundled engine together with the app — no manual step.
        maybeAutoStartEngine()

        // Non-intrusive daily update check against GitHub Releases (the
        // manual check lives in Settings → About).
        maybeAutoCheckUpdate()
    }

    // ---------------- in-place destinations ----------------

    /**
     * Swaps between the torrent home and the in-place settings page — the
     * settings tab is a fragment in the same activity, not a new page.
     */
    private fun showDestination(destinationId: String?) {
        val showHome = destinationId == null
        binding.homeContent.root.visibility = if (showHome) View.VISIBLE else View.GONE
        binding.destinationContainer.visibility = if (showHome) View.GONE else View.VISIBLE
        val settings = supportFragmentManager.findFragmentByTag(SETTINGS_DESTINATION)
        if (showHome) {
            settings?.let {
                supportFragmentManager.beginTransaction().hide(it).commitAllowingStateLoss()
            }
        } else {
            if (settings == null) {
                supportFragmentManager.beginTransaction()
                    .add(R.id.destination_container, SettingsFragment(), SETTINGS_DESTINATION)
                    .commitAllowingStateLoss()
            } else {
                supportFragmentManager.beginTransaction()
                    .show(settings)
                    .commitAllowingStateLoss()
            }
        }
    }

    // ---------------- quick speed limits ----------------

    /**
     * Quick speed-limit sheet reachable from the drawer, right under the
     * transfer statistics: alternative-speed toggle plus global
     * download/upload limits, applied immediately via
     * /api/v2/transfer/setDownloadLimit + setUploadLimit — no restart.
     */
    private fun showSpeedLimitDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_speed_limit, null)
        val altSwitch =
            view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.altSpeedSwitch)
        val dlInput = view.findViewById<TextInputEditText>(R.id.download_limit)
        val upInput = view.findViewById<TextInputEditText>(R.id.upload_limit)

        val transfer = viewModel.state.value.transfer
        altSwitch.isChecked = transfer?.useAltSpeedLimits == true
        dlInput.setText((transfer?.dlRateLimit ?: 0L).coerceAtLeast(0L).div(1024L).toString())
        upInput.setText((transfer?.upRateLimit ?: 0L).coerceAtLeast(0L).div(1024L).toString())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.speed_limits)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val repo = ServiceLocator.repository(this@MainActivity)
                    val result = runCatching {
                        dlInput.text?.toString()?.trim()?.toLongOrNull()?.let {
                            if (it >= 0) repo.setDownloadLimit(it * 1024)
                        }
                        upInput.text?.toString()?.trim()?.toLongOrNull()?.let {
                            if (it >= 0) repo.setUploadLimit(it * 1024)
                        }
                        if (altSwitch.isChecked != (transfer?.useAltSpeedLimits == true)) {
                            repo.toggleAltSpeedLimits()
                        }
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (result.isSuccess) R.string.speed_limits_applied
                        else R.string.speed_limits_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    viewModel.refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- update check (GitHub Releases) ----------------

    private fun maybeAutoCheckUpdate() {
        val prefs = ServiceLocator.prefs(this)
        if (System.currentTimeMillis() - prefs.lastUpdateCheck < UPDATE_CHECK_INTERVAL_MS) return
        ServiceLocator.prefs(this).lastUpdateCheck = System.currentTimeMillis()
        lifecycleScope.launch {
            val result = runCatching { UpdateChecker.check() }
            val alive = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            result
                .onSuccess { update ->
                    if (update != null && alive) showUpdateDialog(update)
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

    // ---------------- contextual action mode ----------------

    private fun onContextualMenuItem(itemId: Int): Boolean {
        val hashes = adapter.selectedHashes()
        if (hashes.isEmpty()) return true
        val repo = ServiceLocator.repository(this)
        when (itemId) {
            R.id.delete_torrent_menu -> confirmDelete(hashes)
            R.id.set_category_torrent_menu -> showSetCategoryDialog(hashes)
            R.id.set_tags_torrent_menu -> showSetTagsDialog(hashes)
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
        val repo = ServiceLocator.repository(this)

        d.sortDirectionToggleButton.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.setSortDirection(checkedId == R.id.sort_desc_button)
        }

        d.statusClearButton.setOnClickListener { d.drawerStatusChipGroup.clearCheck(); viewModel.setStatusFilter(null) }
        d.categoriesClearButton.setOnClickListener { d.drawerCategoriesChipGroup.clearCheck(); viewModel.setCategory(null) }
        d.tagsClearButton.setOnClickListener { d.drawerTagsChipGroup.clearCheck(); viewModel.setTag(null) }

        d.drawerStatusChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.setStatusFilter(
                when (checkedIds.firstOrNull()) {
                    R.id.drawer_status_all -> StatusFilter.ALL
                    R.id.drawer_status_downloading -> StatusFilter.DOWNLOADING
                    R.id.drawer_status_seeding -> StatusFilter.SEEDING
                    R.id.drawer_status_completed -> StatusFilter.COMPLETED
                    R.id.drawer_status_resumed -> StatusFilter.RESUMED
                    R.id.drawer_status_paused -> StatusFilter.PAUSED
                    R.id.drawer_status_active -> StatusFilter.ACTIVE
                    R.id.drawer_status_inactive -> StatusFilter.INACTIVE
                    R.id.drawer_status_stalled -> StatusFilter.STALLED
                    R.id.drawer_status_checking -> StatusFilter.CHECKING
                    R.id.drawer_status_moving -> StatusFilter.MOVING
                    R.id.drawer_status_error -> StatusFilter.ERROR
                    R.id.drawer_status_downloading_metadata -> StatusFilter.DOWNLOADING_METADATA
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
                    R.id.drawer_sorting_ratio -> SortField.RATIO
                    R.id.drawer_sorting_peers -> SortField.PEERS
                    R.id.drawer_sorting_dl_speed -> SortField.DL_SPEED
                    R.id.drawer_sorting_up_speed -> SortField.UP_SPEED
                    R.id.drawer_sorting_uploaded -> SortField.UPLOADED
                    R.id.drawer_sorting_completion_date -> SortField.COMPLETION_DATE
                    else -> SortField.DATE_ADDED
                }
            )
        }

        d.drawerCategoriesChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull()
            viewModel.setCategory(
                when (id) {
                    R.id.no_category_chip -> ""
                    null -> null
                    else -> group.findViewById<Chip?>(id)?.tag as? String
                }
            )
        }

        d.drawerTagsChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull()
            viewModel.setTag(
                when (id) {
                    R.id.no_tags_chip -> ""
                    null -> null
                    else -> group.findViewById<Chip?>(id)?.tag as? String
                }
            )
        }

        d.addCategoryButton.setOnClickListener { showAddCategoryDialog() }
        d.addTagButton.setOnClickListener { showAddTagDialog() }

        // statistics panel + quick speed limits under the transfer stats
        d.sessionDownloadStat.setOnClickListener { showSpeedLimitDialog() }
        d.sessionUploadStat.setOnClickListener { showSpeedLimitDialog() }
        d.speedLimitRow.setOnClickListener { showSpeedLimitDialog() }
        d.pauseAllRow.setOnClickListener {
            lifecycleScope.launch { runCatching { repo.pauseAll() } }
        }
        d.resumeAllRow.setOnClickListener {
            lifecycleScope.launch { runCatching { repo.resumeAll() } }
        }

        // server profile switcher (qBitController multi-server parity)
        d.activeServerLabel.setOnClickListener { showServerSwitcher() }
        d.serverSwitchIcon.setOnClickListener { showServerSwitcher() }
    }

    /**
     * Multi-server switcher: bundled engine pseudo-profile (Enhanced) plus
     * every configured remote server; add / edit / delete entries.
     */
    private fun showServerSwitcher() {
        val prefs = ServiceLocator.prefs(this)
        val profiles = prefs.serverProfiles()
        val labels = mutableListOf<String>()
        if (BuildConfig.IS_ENHANCED) labels += getString(R.string.settings_server_connection_engine)
        labels += profiles.map { it.displayName() }
        labels += getString(R.string.server_manage)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.switch_server)
            .setItems(labels.toTypedArray()) { _, which ->
                val manageIndex = labels.size - 1
                when {
                    which == manageIndex ->
                        startActivity(
                            android.content.Intent(
                                this,
                                io.github.xixka.qbittorrent.ui.settings.ServerSettingsActivity::class.java,
                            )
                        )
                    BuildConfig.IS_ENHANCED && which == 0 -> {
                        if (prefs.useRemoteServer) {
                            prefs.useRemoteServer = false
                            ServiceLocator.resetClient()
                            viewModel.restart()
                            render(viewModel.state.value)
                        }
                    }
                    else -> {
                        val index = if (BuildConfig.IS_ENHANCED) which - 1 else which
                        val profile = profiles.getOrNull(index) ?: return@setItems
                        if (!prefs.useRemoteServer) prefs.useRemoteServer = true
                        if (prefs.activeServer()?.id != profile.id) {
                            prefs.switchServer(profile.id)
                        }
                        ServiceLocator.resetClient()
                        viewModel.restart()
                        render(viewModel.state.value)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- categories (qBC parity: name + save path) ----------------

    /**
     * Assign or clear a category on the selected torrents, qBittorrent
     * context-menu style: pick an existing category, create a new one, or
     * remove the assignment entirely.
     */
    private fun showSetCategoryDialog(hashes: List<String>) {
        val existing = viewModel.state.value.categories
        val options = mutableListOf<String>()
        options += getString(R.string.category_new)
        if (existing.isNotEmpty()) {
            options += existing
            options += getString(R.string.category_remove)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.set_category)
            .setItems(options.toTypedArray()) { _, which ->
                val picked = options[which]
                when {
                    picked == getString(R.string.category_new) ->
                        promptCategoryName { name, _ -> applyCategory(hashes, name) }

                    picked == getString(R.string.category_remove) ->
                        applyCategory(hashes, "")

                    else -> applyCategory(hashes, picked)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyCategory(hashes: List<String>, category: String) {
        lifecycleScope.launch {
            val result = runCatching {
                ServiceLocator.repository(this@MainActivity).setCategory(hashes, category)
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) R.string.category_applied else R.string.category_apply_failed,
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.refresh()
        }
    }

    /** qBC-style new category dialog: name + save path. */
    private fun promptCategoryName(onName: (String, String) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_category, null)
        val nameInput = view.findViewById<TextInputEditText>(R.id.category_name)
        val pathInput = view.findViewById<TextInputEditText>(R.id.category_save_path)
        lifecycleScope.launch {
            runCatching {
                val def = ServiceLocator.repository(this@MainActivity).defaultSavePath()
                pathInput?.hint = def
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_category)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput?.text?.toString()?.trim().orEmpty()
                val path = pathInput?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) onName(name, path)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Long-press a category chip to rename or delete it. */
    private fun showManageCategoryDialog(name: String) {
        val options = arrayOf(
            getString(R.string.category_rename),
            getString(R.string.category_delete),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setItems(options) { _, which ->
                if (which == 0) showRenameCategoryDialog(name) else confirmDeleteCategory(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameCategoryDialog(name: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_input, null)
        view.findViewById<TextInputEditText>(R.id.input)?.setText(name)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.category_rename)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty() && newName != name) {
                    lifecycleScope.launch {
                        // Renaming = create the new category, move the torrents,
                        // then drop the old one (the WebUI does the same dance
                        // because the API has no direct rename).
                        runCatching {
                            val repo = ServiceLocator.repository(this@MainActivity)
                            val hashes = viewModel.state.value.torrents
                                .filter { it.category == name }
                                .map { it.hash }
                            repo.createCategory(newName, "")
                            if (hashes.isNotEmpty()) repo.setCategory(hashes, newName)
                            repo.removeCategory(name)
                        }
                        viewModel.refresh()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteCategory(name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.category_delete)
            .setMessage(getString(R.string.category_delete_confirm, name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        ServiceLocator.repository(this@MainActivity).removeCategory(name)
                    }
                    if (viewModel.category == name) {
                        drawerBinding.drawerCategoriesChipGroup.clearCheck()
                        viewModel.setCategory(null)
                    }
                    viewModel.refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddCategoryDialog() {
        promptCategoryName { name, path ->
            lifecycleScope.launch {
                runCatching {
                    ServiceLocator.repository(this@MainActivity).createCategory(name, path)
                }
                viewModel.refresh()
            }
        }
    }

    // ---------------- tags (qBC parity) ----------------

    /** Assign tags to the selected torrents, qBC context-menu style. */
    private fun showSetTagsDialog(hashes: List<String>) {
        val existing = viewModel.state.value.tags
        val checked = hashes.map { hash ->
            viewModel.state.value.torrents.firstOrNull { it.hash == hash }?.tags.orEmpty()
        }.flatMap { it.split(',').map { t -> t.trim() } }.filter { it.isNotEmpty() }.toSet()

        val names = existing.toTypedArray()
        val state = checked.toMutableSet()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.set_tags)
            .setMultiChoiceItems(names, names.map { it in state }.toBooleanArray()) { _, which, isChecked ->
                if (isChecked) state.add(names[which]) else state.remove(names[which])
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val repo = ServiceLocator.repository(this@MainActivity)
                    runCatching {
                        if (state.isNotEmpty()) repo.addTags(hashes, state.toList())
                    }
                    Toast.makeText(this@MainActivity, R.string.tag_applied, Toast.LENGTH_SHORT).show()
                    viewModel.refresh()
                }
            }
            .setNeutralButton(R.string.tag_clear) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        ServiceLocator.repository(this@MainActivity)
                            .removeTags(hashes, existing)
                    }
                    viewModel.refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddTagDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_tag)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        runCatching {
                            ServiceLocator.repository(this@MainActivity).createTags(listOf(name))
                        }
                        viewModel.refresh()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Long-press a tag chip: rename is done via delete+create. */
    private fun showManageTagDialog(name: String) {
        val options = arrayOf(getString(R.string.tag_delete))
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setItems(options) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        ServiceLocator.repository(this@MainActivity).deleteTags(listOf(name))
                    }
                    if (viewModel.tag == name) {
                        drawerBinding.drawerTagsChipGroup.clearCheck()
                        viewModel.setTag(null)
                    }
                    viewModel.refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- state rendering ----------------

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

        val prefs = ServiceLocator.prefs(this)
        val localEngine = prefs.usingLocalEngine
        val emptyText = when {
            !state.configured -> R.string.empty_not_configured
            state.authError -> R.string.empty_auth_error
            localEngine && state.engineFailed -> R.string.engine_start_failed
            localEngine && !state.connected -> R.string.engine_starting
            !state.connected -> R.string.empty_offline
            else -> R.string.torrent_list_empty
        }
        binding.homeContent.emptyViewTorrentList.setText(emptyText)

        // Only prompt once per failure episode when the engine actually dies.
        if (localEngine && state.engineFailed && !engineFailurePrompted) {
            engineFailurePrompted = true
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.engine_start_failed)
                .setMessage(LocalEngineManager.lastError ?: getString(R.string.engine_start_failed))
                .setPositiveButton(R.string.settings_engine_start) { _, _ ->
                    runCatching { LocalEngineService.start(this) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else if (!state.engineFailed) {
            engineFailurePrompted = false
        }

        val d = drawerBinding
        state.transfer?.let {
            d.sessionDownloadStat.text = "↓ ${Format.speed(it.dlInfoSpeed)} • ${Format.size(it.dlInfoData)}"
            d.sessionUploadStat.text = "↑ ${Format.speed(it.upInfoSpeed)} • ${Format.size(it.upInfoData)}"
            d.sessionDhtNodesStat.text = getString(R.string.dht_nodes_stat, it.dhtNodes.toString())
            d.speedLimitValue.text = buildString {
                append("↓ ")
                append(if (it.dlRateLimit > 0) Format.speed(it.dlRateLimit) else "∞")
                append(" • ↑ ")
                append(if (it.upRateLimit > 0) Format.speed(it.upRateLimit) else "∞")
            }
        }
        d.sessionListenPortStat.text =
            state.serverVersion.ifBlank { getString(R.string.stats) }

        // active server label
        d.activeServerLabel.text = when {
            localEngine -> getString(R.string.settings_server_connection_engine)
            prefs.activeServer() != null -> prefs.activeServer()?.displayName()
            else -> getString(R.string.settings_server_connection_not_configured)
        }

        updateDrawerChips(state.categories, state.tags)
    }

    private fun updateDrawerChips(categories: List<String>, tags: List<String>) {
        updateChips(
            group = drawerBinding.drawerCategoriesChipGroup,
            keepId = R.id.no_category_chip,
            names = categories,
            onManage = { showManageCategoryDialog(it) },
        )
        updateChips(
            group = drawerBinding.drawerTagsChipGroup,
            keepId = R.id.no_tags_chip,
            names = tags,
            onManage = { showManageTagDialog(it) },
        )
    }

    private fun updateChips(
        group: com.google.android.material.chip.ChipGroup,
        keepId: Int,
        names: List<String>,
        onManage: (String) -> Unit,
    ) {
        val existing = group.children.filter { it.id != keepId }.toList()
        existing.forEach { group.removeView(it) }
        if (names.isEmpty()) return
        names.forEach { name ->
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_tag_chip, group, false) as Chip
            chip.text = name
            chip.tag = name
            chip.setOnLongClickListener {
                onManage(name)
                true
            }
            group.addView(chip)
        }
    }

    // ---------------- delete ----------------

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

    /** Starts the bundled engine once per process (Enhanced edition only). */
    private fun maybeAutoStartEngine() {
        if (engineAutoStarted) return
        engineAutoStarted = true
        if (!BuildConfig.IS_ENHANCED) return
        if (LocalEngineManager.isSupported(this) && !LocalEngineManager.isRunning()) {
            runCatching { LocalEngineService.start(this) }
        }
    }

    companion object {
        private const val PICK_TORRENT_FILE = 42

        /** Auto update check frequency. */
        private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** Per-process guard so stopping the engine is respected until relaunch. */
        @Volatile
        private var engineAutoStarted = false

        private const val HOME_DESTINATION = "home"
        private const val RSS_DESTINATION = "rss"
        private const val SETTINGS_DESTINATION = "settings"
    }
}
