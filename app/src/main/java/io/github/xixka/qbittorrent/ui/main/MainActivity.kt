package io.github.xixka.qbittorrent.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServerConfig
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityMainBinding
import io.github.xixka.qbittorrent.databinding.DialogAddLinkBinding
import io.github.xixka.qbittorrent.databinding.SheetFabMenuBinding
import io.github.xixka.qbittorrent.databinding.HomeDrawerContentBinding
import io.github.xixka.qbittorrent.ui.createtorrent.CreateTorrentActivity
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.ServerState
import io.github.xixka.qbittorrent.qbt.LocalEngineManager
import io.github.xixka.qbittorrent.qbt.LocalEngineService
import io.github.xixka.qbittorrent.ui.addtorrent.AddTorrentActivity
import io.github.xixka.qbittorrent.ui.detail.DetailActivity
import io.github.xixka.qbittorrent.ui.rss.RssFragment
import io.github.xixka.qbittorrent.ui.search.SearchFragment
import io.github.xixka.qbittorrent.ui.settings.SettingsFragment
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.StorageAccess
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.UpdateChecker
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch

/**
 * LibreTorrent-style home screen: navigation drawer with transfer stats and
 * filter chips (status / sorting / added date / categories / tags), search
 * bar, contextual toolbar, FAB popup menu and a bottom navigation that hosts
 * ALL of its destinations IN PLACE (torrents / RSS / settings) — no bottom
 * nav entry ever opens a separate window; sub-pages are pushed onto the
 * same in-place container.
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
            binding.homeContent.searchView.hide()
            pushPage(SearchFragment.newInstance(viewModel.searchQuery.ifBlank { null }))
        }
        binding.homeContent.searchBar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        // No overflow menu: pause/resume-all live as toolbar icons
        // (LibreTorrent home menu parity), everything else in Settings.
        binding.homeContent.searchBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.pause_all_menu -> {
                    lifecycleScope.launch { runCatching { repo().pauseAll() } }
                    true
                }
                R.id.resume_all_menu -> {
                    lifecycleScope.launch { runCatching { repo().resumeAll() } }
                    true
                }
                else -> false
            }
        }

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
            // Swallow programmatic/restore-driven selection changes: only
            // real user taps may switch tabs (showTab would otherwise run
            // during state restoration and wipe the in-place page stack).
            if (navSelectionSuppressed) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.home_nav -> {
                    showTab(TAB_HOME)
                    true
                }
                R.id.rss_nav -> {
                    // the RSS hub is an in-place destination, never a window
                    showTab(TAB_RSS)
                    true
                }
                R.id.settings_nav -> {
                    showTab(TAB_SETTINGS)
                    true
                }
                else -> false
            }
        }

        // Single-activity back navigation: close the search overlay or the
        // drawer, pop in-place sub-pages, return to the torrent list before
        // finally leaving the app.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        binding.homeContent.searchView.isShowing ->
                            binding.homeContent.searchView.hide()

                        binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                            binding.drawerLayout.closeDrawer(GravityCompat.START)

                        pageStack.isNotEmpty() -> popPage()

                        currentTab != TAB_HOME -> goHome()

                        else -> finish()
                    }
                }
            },
        )

        restoreFragments(savedInstanceState)

        // RSS section visibility follows the Settings toggle (default on);
        // live changes update the bottom navigation without a restart.
        applyRssVisibility(ServiceLocator.prefs(this).showRss)
        registerRssPrefListener()

        setupDrawer()
        observeState()

        // Load the bundled engine together with the app — no manual step.
        maybeAutoStartEngine()

        // Non-intrusive daily update check against GitHub Releases (the
        // manual check lives in Settings → About).
        maybeAutoCheckUpdate()
    }

    // ---------------- in-place destinations (single-activity) ----------------

    /** Currently selected bottom-nav tab. */
    private var currentTab = TAB_HOME

    /** True while the nav bar selection is driven by state restoration. */
    private var navSelectionSuppressed = false

    /** Root fragment of each bottom-nav destination. */
    private val tabRoots = mutableMapOf<String, Fragment>()

    /** In-place sub-pages pushed above the current destination root. */
    private val pageStack = mutableListOf<Fragment>()

    private fun repo() = ServiceLocator.repository(this)

    private fun topFragment(): Fragment? =
        pageStack.lastOrNull() ?: tabRoots[currentTab]

    private fun updateContainerVisibility() {
        val showContainer = currentTab != TAB_HOME || pageStack.isNotEmpty()
        binding.homeContent.root.visibility = if (showContainer) View.GONE else View.VISIBLE
        binding.destinationContainer.visibility = if (showContainer) View.VISIBLE else View.GONE
    }

    /**
     * Switches the bottom-navigation destination IN PLACE — the torrent list,
     * the RSS hub and the settings hub are all hosted in the same container,
     * no new window is ever opened. Re-tapping the current tab drops its
     * sub-pages and returns to the tab root.
     */
    private fun showTab(tab: String) {
        if (tab == currentTab) {
            while (pageStack.isNotEmpty()) popPage()
            updateContainerVisibility()
            return
        }
        clearPageStack()
        tabRoots[currentTab]?.let { root ->
            supportFragmentManager.beginTransaction().hide(root).commitAllowingStateLoss()
        }
        currentTab = tab
        if (tab == TAB_HOME) {
            updateContainerVisibility()
        } else {
            val tag = ROOT_TAG_PREFIX + tab
            val existing = supportFragmentManager.findFragmentByTag(tag)
            if (existing == null) {
                val root = createTabRoot(tab)
                tabRoots[tab] = root
                supportFragmentManager.beginTransaction()
                    .add(R.id.destination_container, root, tag)
                    .commitAllowingStateLoss()
            } else {
                tabRoots[tab] = existing
                supportFragmentManager.beginTransaction().show(existing).commitAllowingStateLoss()
            }
            updateContainerVisibility()
        }
    }

    private fun createTabRoot(tab: String): Fragment = when (tab) {
        TAB_RSS -> RssFragment()
        TAB_SETTINGS -> SettingsFragment()
        else -> throw IllegalArgumentException("unknown tab $tab")
    }

    // ---------------- RSS section visibility ----------------

    /** Hides or shows the RSS tab of the bottom navigation. */
    private fun applyRssVisibility(show: Boolean) {
        binding.bottomNavigation.menu.findItem(R.id.rss_nav)?.isVisible = show
        if (!show && currentTab == TAB_RSS) {
            // the visible tab vanished from the nav: return to the list
            navSelectionSuppressed = true
            currentTab = TAB_HOME
            updateContainerVisibility()
            binding.bottomNavigation.selectedItemId = R.id.home_nav
            navSelectionSuppressed = false
        }
    }

    /** Reacts to Settings toggling the RSS section while the app runs. */
    private fun registerRssPrefListener() {
        val prefs = ServiceLocator.prefs(this)
        rssPrefListener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == io.github.xixka.qbittorrent.data.Prefs.KEY_SHOW_RSS) {
                    runOnUiThread { applyRssVisibility(prefs.showRss) }
                }
            }
        prefs.registerChangeListener(rssPrefListener!!)
    }

    private var rssPrefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    /**
     * Pushes an in-place sub-page (settings sub-screens, RSS articles, engine
     * search, plugins) on top of the current destination — still no window.
     */
    fun pushPage(fragment: Fragment) {
        val top = topFragment()
        val tx = supportFragmentManager.beginTransaction()
        top?.let { tx.hide(it) }
        tx.add(R.id.destination_container, fragment)
        tx.commitAllowingStateLoss()
        pageStack += fragment
        updateContainerVisibility()
    }

    fun popPage(): Boolean {
        if (pageStack.isEmpty()) return false
        val top = pageStack.removeAt(pageStack.lastIndex)
        val tx = supportFragmentManager.beginTransaction()
        tx.remove(top)
        topFragment()?.let { tx.show(it) }
        tx.commitAllowingStateLoss()
        updateContainerVisibility()
        return true
    }

    private fun clearPageStack() {
        if (pageStack.isEmpty()) return
        val tx = supportFragmentManager.beginTransaction()
        pageStack.forEach { tx.remove(it) }
        tx.commitAllowingStateLoss()
        pageStack.clear()
    }

    /** Bottom-nav / toolbar-arrow "back to torrents". */
    fun goHome() {
        if (currentTab == TAB_HOME && pageStack.isEmpty()) return
        clearPageStack()
        if (currentTab != TAB_HOME) showTab(TAB_HOME) else updateContainerVisibility()
        if (binding.bottomNavigation.selectedItemId != R.id.home_nav) {
            binding.bottomNavigation.selectedItemId = R.id.home_nav
        }
    }

    /**
     * Re-attaches tab roots / sub-pages surviving an activity recreation
     * (theme change, dynamic-color toggle, night-mode switch, rotation).
     *
     * The current tab is restored from the explicitly saved state: the
     * BottomNavigationView restores its selected item only in
     * onRestoreInstanceState — AFTER onCreate — so reading it here would
     * always yield the default (home) tab and dump the user back on the
     * torrent list, which is exactly what happened when toggling dynamic
     * colors from Settings.
     */
    private fun restoreFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        listOf(TAB_RSS, TAB_SETTINGS).forEach { tab ->
            supportFragmentManager.findFragmentByTag(ROOT_TAG_PREFIX + tab)?.let {
                tabRoots[tab] = it
            }
        }
        val roots = tabRoots.values.toSet()
        supportFragmentManager.fragments
            .filter { it.id == R.id.destination_container && it !in roots }
            .forEach { pageStack += it }
        val savedTab = savedInstanceState.getString(STATE_CURRENT_TAB)
        currentTab = when (savedTab) {
            TAB_RSS -> TAB_RSS
            TAB_SETTINGS -> TAB_SETTINGS
            else -> when (binding.bottomNavigation.selectedItemId) {
                R.id.rss_nav -> TAB_RSS
                R.id.settings_nav -> TAB_SETTINGS
                else -> TAB_HOME
            }
        }
        // Sync the nav bar selection without dispatching a listener-driven
        // showTab() — the fragment hidden/shown flags survived recreation
        // already, only the container visibility has to be re-derived.
        navSelectionSuppressed = true
        binding.bottomNavigation.selectedItemId = when (currentTab) {
            TAB_RSS -> R.id.rss_nav
            TAB_SETTINGS -> R.id.settings_nav
            else -> R.id.home_nav
        }
        navSelectionSuppressed = false
        updateContainerVisibility()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENT_TAB, currentTab)
    }

    override fun onDestroy() {
        rssPrefListener?.let {
            runCatching { ServiceLocator.prefs(this).unregisterChangeListener(it) }
        }
        rssPrefListener = null
        super.onDestroy()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        // The nav view re-selects its saved item here; keep that restore-time
        // dispatch away from showTab() so pushed sub-pages are not popped.
        navSelectionSuppressed = true
        super.onRestoreInstanceState(savedInstanceState)
        navSelectionSuppressed = false
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

    // ---------------- statistics popup (qBitController parity) ----------------

    /**
     * qBitController-style statistics dialog, popped up by tapping the
     * drawer's "listening port" / "DHT nodes" stat rows — user, cache and
     * performance statistics from /sync/maindata's server_state. This is a
     * plain popup window, not a pushed page, and it is the only statistics
     * entry point (no duplicate row in Settings).
     */
    private fun showStatisticsDialog() {
        lifecycleScope.launch {
            val state = runCatching { repo().serverState() }.getOrNull()
            if (state == null) {
                Toast.makeText(this@MainActivity, R.string.error_connection, Toast.LENGTH_SHORT).show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.stats)
                .setMessage(buildStatisticsText(state))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun buildStatisticsText(s: ServerState): String = buildString {
        appendLine(getString(R.string.stats_category_user))
        statLine(R.string.stats_all_time_upload, Format.size(s.allTimeUpload))
        statLine(R.string.stats_all_time_download, Format.size(s.allTimeDownload))
        statLine(R.string.stats_all_time_share_ratio, s.globalRatio)
        statLine(R.string.stats_session_waste, Format.size(s.sessionWaste))
        statLine(R.string.stats_connected_peers, s.connectedPeers.toString())
        appendLine()
        appendLine(getString(R.string.stats_category_cache))
        statLine(R.string.stats_read_cache_hits, s.readCacheHits + "%")
        statLine(R.string.stats_total_buffer_size, Format.size(s.bufferSize))
        appendLine()
        appendLine(getString(R.string.stats_category_performance))
        statLine(R.string.stats_write_cache_overload, s.writeCacheOverload + "%")
        statLine(R.string.stats_read_cache_overload, s.readCacheOverload + "%")
        statLine(R.string.stats_queued_io_jobs, s.queuedIOJobs.toString())
        statLine(R.string.stats_average_time_in_queue, getString(R.string.stats_ms_format, s.averageTimeInQueue))
        statLine(R.string.stats_total_queued_size, Format.size(s.queuedSize))
    }

    private fun StringBuilder.statLine(labelRes: Int, value: String) {
        append(getString(labelRes)).append(": ").appendLine(value)
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
            .setPositiveButton(R.string.update_download) { _, _ ->
                // In-app multi-threaded download + install — no browser,
                // no manual APK download anymore.
                io.github.xixka.qbittorrent.util.UpdateInstaller.downloadAndInstall(this, update)
            }
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
            R.id.pause_torrent_menu ->
                lifecycleScope.launch { runCatching { repo.pause(hashes) }; viewModel.refresh() }
            R.id.resume_torrent_menu ->
                lifecycleScope.launch { runCatching { repo.resume(hashes) }; viewModel.refresh() }
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

    private fun showFabMenu(@Suppress("UNUSED_PARAMETER") anchor: View) {
        // A bottom sheet replaces the old PopupMenu: the popup opened
        // downward from the screen-bottom FAB and was clipped/covered by the
        // bottom navigation, while a bottom sheet always expands upward and
        // can never be blocked. It also scales with the extra entries
        // (create torrent).
        val sheet = BottomSheetDialog(this)
        val content = SheetFabMenuBinding.inflate(layoutInflater)
        content.actionAddLink.setOnClickListener {
            sheet.dismiss()
            showAddLinkDialog()
        }
        content.actionOpenFile.setOnClickListener {
            sheet.dismiss()
            pickTorrentFile()
        }
        content.actionCreateTorrent.setOnClickListener {
            sheet.dismiss()
            CreateTorrentActivity.start(this)
        }
        sheet.setContentView(content.root)
        sheet.show()
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

        d.addCategoryButton.setOnClickListener { showCategoriesManager() }
        d.addTagButton.setOnClickListener { showAddTagDialog() }

        // Quick speed limits: the speed-display rows under the transfer
        // stats open the limits dialog (requirement: speed limits live at
        // the speed display, no extra rows, no menu entry).
        d.sessionDownloadStat.setOnClickListener { showSpeedLimitDialog() }
        d.sessionUploadStat.setOnClickListener { showSpeedLimitDialog() }
        // LibreTorrent drawer stats rows: tapping the download/upload rows
        // opens the quick speed-limit sheet; tapping the listening-port and
        // DHT rows pops up the qBitController-style statistics dialog (the
        // ONLY statistics entry — there is no duplicate one in Settings).
        d.sessionListenPortStat.setOnClickListener { showStatisticsDialog() }
        d.sessionDhtNodesStat.setOnClickListener { showStatisticsDialog() }
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
                        promptCategoryName { name, path -> createAndApplyCategory(hashes, name, path) }

                    picked == getString(R.string.category_remove) ->
                        applyCategory(hashes, "")

                    else -> applyCategory(hashes, picked)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * "New category" from the torrent context menu: the WebUI first REGISTERS
     * the category through the dynamic categories API (name + save path) and
     * only then assigns it. Previously this path threw the typed save path
     * away and relied on setCategory's implicit auto-create, so a category
     * made here never got its own path — the dynamic-API creation the
     * round-18 commit message promised was effectively missing.
     */
    private fun createAndApplyCategory(hashes: List<String>, name: String, path: String) {
        lifecycleScope.launch {
            runCatching {
                ServiceLocator.repository(this@MainActivity).createCategory(name, path)
            }
            applyCategory(hashes, name)
        }
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
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_category, null)
        val nameInput = view.findViewById<TextInputEditText>(R.id.category_name)
        val pathInput = view.findViewById<TextInputEditText>(R.id.category_save_path)
        nameInput?.setText(name)
        // Prefill the current save path from the live categories API.
        lifecycleScope.launch {
            runCatching { repo().categories() }.getOrNull()?.get(name)?.let { meta ->
                pathInput?.setText(meta.savePath)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.category_rename)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = nameInput?.text?.toString()?.trim().orEmpty()
                val newPath = pathInput?.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        // Renaming = create the new category, move the torrents,
                        // then drop the old one (the WebUI does the same dance
                        // because the API has no direct rename).
                        runCatching {
                            val repo = ServiceLocator.repository(this@MainActivity)
                            val hashes = viewModel.state.value.torrents
                                .filter { it.category == name }
                                .map { it.hash }
                            repo.createCategory(newName, newPath)
                            if (hashes.isNotEmpty()) repo.setCategory(hashes, newName)
                            if (newName != name) repo.removeCategory(name)
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

    /**
     * Categories manager (分流): every row — name, save path and torrent
     * count — is generated live from the official categories API, so the
     * screen always mirrors the connected engine (or remote server).
     */
    private fun showCategoriesManager() {
        lifecycleScope.launch {
            val cats = runCatching { repo().categories() }.getOrDefault(emptyMap())
            val counts = viewModel.state.value.torrents
                .filter { it.category.isNotBlank() }
                .groupingBy { it.category }.eachCount()
            val list = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.dialog_categories_manager, null)
                .findViewById<LinearLayout>(R.id.categories_list)
            if (cats.isEmpty()) {
                val empty = MaterialTextView(this@MainActivity).apply {
                    setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
                    )
                    setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            this, android.R.attr.textColorSecondary
                        )
                    )
                    text = getString(R.string.categories_empty)
                    setPadding(48, 32, 48, 32)
                }
                list.addView(empty)
            } else {
                for ((name, meta) in cats.toSortedMap()) {
                    val row = LayoutInflater.from(this@MainActivity)
                        .inflate(R.layout.item_category_row, list, false)
                    row.findViewById<MaterialTextView>(R.id.category_row_name).text = name
                    val pathText = meta.savePath.ifBlank { "—" }
                    val count = counts[name] ?: 0
                    row.findViewById<MaterialTextView>(R.id.category_row_path).text =
                        getString(R.string.category_torrents_fmt, count) + " · " + pathText
                    row.setOnClickListener { showManageCategoryDialog(name) }
                    list.addView(row)
                }
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.categories)
                .setView(list.parent as View)
                .setPositiveButton(R.string.add_category) { d, _ ->
                    d.dismiss()
                    showAddCategoryDialog()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
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
        maybePromptForStorageAccess(localEngine)
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
        // LibreTorrent parity: the stat rows are ALWAYS rebound — a null
        // transfer (connection dropped, engine restarting) zeroes them
        // instead of leaving the last poll's values frozen on screen.
        val t = state.transfer
        d.sessionDownloadStat.text =
            "${Format.size(t?.dlInfoData ?: 0L)} • ${Format.speed(t?.dlInfoSpeed ?: 0L)}"
        d.sessionUploadStat.text =
            "${Format.size(t?.upInfoData ?: 0L)} • ${Format.speed(t?.upInfoSpeed ?: 0L)}"
        d.sessionDhtNodesStat.text =
            getString(R.string.dht_nodes_stat, (t?.dhtNodes ?: 0L).toString())
        // Listening port: the bundled engine's WebUI port, or the active
        // remote server's port — LibreTorrent drawer row. The bundled
        // engine's resident memory (VmRSS) rides along in the same row.
        val listenPort = if (prefs.usingLocalEngine) {
            prefs.enginePort
        } else {
            prefs.activeServer()?.port ?: ServerConfig.DEFAULT_PORT
        }
        d.sessionListenPortStat.text = buildString {
            append(getString(R.string.listen_port_stat)).append(": ").append(listenPort)
            state.engineRss?.let { rss ->
                append("  •  ").append(getString(R.string.mem_usage, Format.size(rss)))
            }
        }

        updateDrawerChips(state.categories, state.tags)
    }

    /**
     * One prompt per process: the bundled engine downloads into the PUBLIC
     * /storage/emulated/0/Download/qbittorrent folder, and raw-path writes
     * there need "All files access" (Android 11+) / the classic WRITE grant
     * (Android 9-10). Without it every download fails with a write error.
     */
    private fun maybePromptForStorageAccess(localEngine: Boolean) {
        if (!localEngine || storagePromptShown) return
        if (StorageAccess.isGranted(this)) {
            storagePromptShown = true
            return
        }
        storagePromptShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.storage_access_title)
            .setMessage(getString(R.string.storage_access_message))
            .setPositiveButton(R.string.storage_access_grant) { _, _ ->
                val legacy = StorageAccess.legacyRuntimePermission
                if (legacy != null) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity, arrayOf(legacy), REQUEST_STORAGE
                    )
                } else {
                    StorageAccess.allFilesSettingsIntent(this@MainActivity)?.let {
                        runCatching { startActivity(it) }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) R.string.storage_access_granted
                else R.string.storage_access_denied,
                Toast.LENGTH_SHORT,
            ).show()
        }
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
        // Chips are keyed by name and REUSED across the 1 s poll refreshes
        // so a checked chip keeps both its checked state and its view id
        // (recreating them every poll would silently drop the selection).
        //
        // Each dynamic chip gets a generated view id: the checked-state
        // listener resolves the tapped chip via findViewById(id) — without
        // an id every chip registers as NO_ID and the listener would always
        // resolve whichever no-id chip is first in the group, i.e. tapping
        // ANY category/tag chip filtered by the FIRST one.
        val existing = HashMap<String, Chip>()
        group.children.forEach { child ->
            if (child.id != keepId && child is Chip) {
                (child.tag as? String)?.let { name -> existing[name] = child }
            }
        }
        val wanted = names.toSet()
        // drop chips whose category/tag no longer exists on the server
        group.children.filter { it.id != keepId }.toList().forEach { child ->
            val name = (child as? Chip)?.tag as? String
            if (name == null || name !in wanted) group.removeView(child)
        }
        if (names.isEmpty()) return
        names.forEach { name ->
            if (name !in existing) {
                val chip = LayoutInflater.from(this)
                    .inflate(R.layout.item_tag_chip, group, false) as Chip
                chip.id = View.generateViewId()
                chip.text = name
                chip.tag = name
                chip.setOnLongClickListener {
                    onManage(name)
                    true
                }
                group.addView(chip)
            }
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

        /** Runtime request id of the legacy (pre-R) storage permission. */
        private const val REQUEST_STORAGE = 43

        /** One-shot per process so the storage prompt is never nagging. */
        @Volatile
        private var storagePromptShown = false

        /** Auto update check frequency. */
        private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** Per-process guard so stopping the engine is respected until relaunch. */
        @Volatile
        private var engineAutoStarted = false

        private const val TAB_HOME = "home"
        private const val TAB_RSS = "rss"
        private const val TAB_SETTINGS = "settings"
        private const val ROOT_TAG_PREFIX = "root_"

        /** Saved bottom-nav tab, restored after activity recreation. */
        private const val STATE_CURRENT_TAB = "state_current_tab"
    }
}
