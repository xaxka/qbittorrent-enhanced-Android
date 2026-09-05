package io.github.xixka.qbittorrent.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityDetailBinding
import io.github.xixka.qbittorrent.databinding.DialogTorrentOptionsBinding
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Torrent detail screen, qBC TorrentScreen parity: five pages (overview /
 * files / trackers / peers / web seeds) inside a ViewPager2 that keeps
 * every page alive (qBC beyondViewportPageCount), per-tab ViewModels that
 * load immediately and poll only while visible, and the qBC action set in
 * the toolbar — resume/pause, delete, options, category, tags, rename,
 * force recheck/reannounce, force start, super seeding, copy and export,
 * plus the current tab's add action.
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModelFactory by lazy {
        DetailViewModelFactory(application, intent.getStringExtra(EXTRA_HASH) ?: "")
    }

    val overviewViewModel: DetailOverviewViewModel by viewModels { viewModelFactory }
    val filesViewModel: DetailFilesViewModel by viewModels { viewModelFactory }
    val trackersViewModel: DetailTrackersViewModel by viewModels { viewModelFactory }
    val peersViewModel: DetailPeersViewModel by viewModels { viewModelFactory }
    val webSeedsViewModel: DetailWebSeedsViewModel by viewModels { viewModelFactory }

    private var currentTab = 0

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-bittorrent"),
    ) { uri -> if (uri != null) writeExport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(child = binding.viewPager, sideMask = WindowInsetsSide.BOTTOM)

        binding.appBar.title = intent.getStringExtra(EXTRA_NAME) ?: ""

        // qBC: every page is pre-created so each tab's data starts loading
        // the moment the screen opens — files no longer appear seconds late.
        binding.viewPager.adapter = DetailPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = TAB_TITLES.size - 1

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentTab = position
                    rebuildMenu()
                }
            },
        )

        binding.appBar.setNavigationOnClickListener { finishAfterTransition() }
        binding.appBar.setOnMenuItemClickListener { item -> onMenuItem(item.itemId) }
        rebuildMenu()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    overviewViewModel.torrent.collect { torrent ->
                        if (torrent != null) {
                            binding.appBar.title = torrent.name
                            updateMenuState(torrent)
                        }
                    }
                }
                launch {
                    overviewViewModel.eventFlow.collect { event ->
                        showEvent(event)
                    }
                }
                launch { filesViewModel.eventFlow.collect(::showEvent) }
                launch { trackersViewModel.eventFlow.collect(::showEvent) }
                launch { peersViewModel.eventFlow.collect(::showEvent) }
                launch { webSeedsViewModel.eventFlow.collect(::showEvent) }
            }
        }
    }

    /** The pager, exposed so each tab fragment can watch page swipes and
     *  finish its action-mode selection when the user swipes away (qBC). */
    val detailViewPager: androidx.viewpager2.widget.ViewPager2
        get() = binding.viewPager

    /** qBC TorrentScreen action bar: overview set + current tab extras. */
    private fun rebuildMenu() {
        val menu = binding.appBar.menu
        menu.clear()
        menuInflater.inflate(R.menu.torrent_detail, menu)
        when (currentTab) {
            TAB_FILES -> {
                menuInflater.inflate(R.menu.torrent_detail_files, menu)
                // reflect the active sort mode in the single-choice submenu
                menu.findItem(
                    when (filesViewModel.sortMode.value) {
                        FilesSortMode.NAME -> R.id.sort_files_name
                        FilesSortMode.SIZE -> R.id.sort_files_size
                        FilesSortMode.PROGRESS -> R.id.sort_files_progress
                        else -> R.id.sort_files_order
                    }
                )?.isChecked = true
            }
            TAB_TRACKERS -> menuInflater.inflate(R.menu.torrent_detail_trackers, menu)
            TAB_PEERS -> menuInflater.inflate(R.menu.torrent_detail_peers, menu)
            TAB_WEBSEEDS -> menuInflater.inflate(R.menu.torrent_detail_web_seeds, menu)
        }
        overviewViewModel.torrent.value?.let { updateMenuState(it) }
            ?: updateMenuState(null)
    }

    /**
     * qBC TorrentOverviewFragment menu matrix: until the torrent arrives
     * the data-dependent actions are hidden or disabled (a null torrent
     * previously rendered a full menu whose actions fired against empty
     * data); once loaded, pause/resume follows the qBC stopped-or-broken
     * logic and copy/reannounce follow the engine's actual state.
     */
    private fun updateMenuState(torrent: io.github.xixka.qbittorrent.model.TorrentInfo?) {
        val menu = binding.appBar.menu
        val loaded = torrent != null
        val stopped = torrent?.isStoppedOrBroken ?: false
        menu.findItem(R.id.pause_resume_torrent_menu)?.apply {
            isVisible = loaded
            setIcon(
                if (stopped) R.drawable.ic_play_arrow_24px
                else R.drawable.ic_pause_24px
            )
            setTitle(if (stopped) R.string.resume_torrent else R.string.pause_torrent)
        }
        menu.findItem(R.id.torrent_options_menu)?.isEnabled = loaded
        menu.findItem(R.id.tags_menu)?.isEnabled = loaded
        menu.findItem(R.id.force_start_menu)?.apply {
            isEnabled = loaded
            isChecked = torrent?.forceStart == true
        }
        menu.findItem(R.id.super_seeding_menu)?.apply {
            isEnabled = loaded
            isChecked =
                overviewViewModel.properties.value?.superSeeding ?: (torrent?.superSeeding ?: false)
        }
        val copyMenu = menu.findItem(R.id.copy_menu)
        copyMenu?.isEnabled = loaded
        menu.findItem(R.id.copy_name_menu)?.isEnabled = loaded
        menu.findItem(R.id.copy_hash_v1_menu)?.isEnabled = !hashV1().isNullOrBlank()
        menu.findItem(R.id.copy_hash_v2_menu)?.isEnabled = !hashV2().isNullOrBlank()
        // qBC: reannounce is a no-op on stopped/queued/error/checking torrents
        menu.findItem(R.id.force_announce_torrent_menu)?.isEnabled =
            torrent != null && torrent.state.lowercase() !in REANNOUNCE_DEAD_STATES
    }

    private fun hashV1(): String? {
        val t = overviewViewModel.torrent.value ?: return null
        return t.infohashV1?.ifBlank { null }
            ?: overviewViewModel.properties.value?.infohashV1?.ifBlank { null }
            ?: t.hash.ifBlank { null }
    }

    private fun hashV2(): String? {
        val t = overviewViewModel.torrent.value ?: return null
        return t.infohashV2?.ifBlank { null }
            ?: overviewViewModel.properties.value?.infohashV2?.ifBlank { null }
    }

    private fun showEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.Message -> Snackbar.make(
                binding.coordinatorLayout, getString(event.res), Snackbar.LENGTH_SHORT
            ).show()

            is DetailEvent.Error -> Snackbar.make(
                binding.coordinatorLayout, event.message, Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.sort_files_order -> {
            filesViewModel.setSortMode(FilesSortMode.ORDER); true
        }
        R.id.sort_files_name -> {
            filesViewModel.setSortMode(FilesSortMode.NAME); true
        }
        R.id.sort_files_size -> {
            filesViewModel.setSortMode(FilesSortMode.SIZE); true
        }
        R.id.sort_files_progress -> {
            filesViewModel.setSortMode(FilesSortMode.PROGRESS); true
        }

        R.id.pause_resume_torrent_menu -> {
            val torrent = overviewViewModel.torrent.value
            if (torrent == null) {
                false
            } else {
                // qBC: stopped-or-broken torrents (incl. error / missing files)
                // offer RESUME — the engine ignores pause on those states.
                if (torrent.isStoppedOrBroken) overviewViewModel.resume()
                else overviewViewModel.pause()
                true
            }
        }

        R.id.delete_torrent_menu -> {
            confirmDelete()
            true
        }

        R.id.torrent_options_menu -> {
            showOptionsDialog()
            true
        }

        R.id.category_menu -> {
            showCategoryDialog()
            true
        }

        R.id.tags_menu -> {
            showTagsDialog()
            true
        }

        R.id.rename_torrent_menu -> {
            showRenameDialog()
            true
        }

        R.id.force_recheck_torrent_menu -> {
            confirmRecheck()
            true
        }

        R.id.force_announce_torrent_menu -> {
            overviewViewModel.reannounce()
            true
        }

        R.id.force_start_menu -> {
            val item = binding.appBar.menu.findItem(R.id.force_start_menu)
            item.isChecked = !item.isChecked
            overviewViewModel.setForceStart(item.isChecked)
            true
        }

        R.id.super_seeding_menu -> {
            val item = binding.appBar.menu.findItem(R.id.super_seeding_menu)
            item.isChecked = !item.isChecked
            overviewViewModel.setSuperSeeding(item.isChecked)
            true
        }

        R.id.copy_name_menu -> {
            copyToClipboard(overviewViewModel.torrent.value?.name.orEmpty())
            true
        }

        R.id.copy_hash_v1_menu -> {
            hashV1()?.let { copyToClipboard(it) }
            true
        }

        R.id.copy_hash_v2_menu -> {
            hashV2()?.let { copyToClipboard(it) }
            true
        }

        R.id.copy_magnet_menu -> {
            val torrent = overviewViewModel.torrent.value
            copyToClipboard(torrent?.magnetUri ?: "magnet:?xt=urn:btih:${overviewViewModel.hash}")
            true
        }

        R.id.export_torrent_menu -> {
            overviewViewModel.export { bytes ->
                if (bytes == null) return@export
                runOnUiThread {
                    val name = (overviewViewModel.torrent.value?.name ?: "torrent") + ".torrent"
                    exportLauncher.launch(name)
                    pendingExport = bytes
                }
            }
            true
        }

        R.id.add_trackers_menu -> {
            showAddTrackersDialog()
            true
        }

        R.id.add_peers_menu -> {
            showAddPeersDialog()
            true
        }

        R.id.add_web_seeds_menu -> {
            showAddWebSeedsDialog()
            true
        }

        else -> false
    }

    // ---------------- qBC dialogs ----------------

    private fun showRenameDialog() {
        val current = overviewViewModel.torrent.value?.name ?: return
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(current)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_torrent)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) overviewViewModel.rename(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** qBC TorrentOptionsDialog parity: TMM, paths, limits, share limits. */
    private fun showOptionsDialog() {
        val torrent = overviewViewModel.torrent.value ?: return
        val props = overviewViewModel.properties.value
        val view = DialogTorrentOptionsBinding.inflate(layoutInflater)

        view.autoTmmSwitch.isChecked = torrent.autoTmm
        view.savePathInput.setText(props?.savePath ?: torrent.savePath)
        view.downloadPathSwitch.isChecked = props?.downloadPath?.isNotBlank() == true
        view.downloadPathInput.setText(props?.downloadPath.orEmpty())

        view.torrentUploadLimit.setText(limitToText(props?.upLimit ?: -1L))
        view.torrentDownloadLimit.setText(limitToText(props?.dlLimit ?: -1L))

        // -2 = global default, -1 = no limit, positive = custom
        val ratioLimit = torrent.ratioLimit
        val seedingTime = torrent.seedingTimeLimit
        val mode = when {
            ratioLimit <= -2.0 && seedingTime <= -2L -> R.id.share_limit_global
            ratioLimit == -1.0 && seedingTime == -1L -> R.id.share_limit_disable
            else -> R.id.share_limit_custom
        }
        view.shareLimitMode.check(mode)
        view.torrentRatioLimit.setText(
            if (ratioLimit > 0) formatLimit(ratioLimit) else ""
        )
        view.torrentSeedingTimeLimit.setText(
            if (seedingTime > 0) seedingTime.toString() else ""
        )
        view.torrentInactiveSeedingTimeLimit.setText(
            if (torrent.inactiveSeedingTimeLimit > 0) torrent.inactiveSeedingTimeLimit.toString() else ""
        )

        val actions = listOf(
            getString(R.string.share_limit_action_default) to "Default",
            getString(R.string.qbt_ratio_act_stop) to "Stop",
            getString(R.string.qbt_ratio_act_remove) to "Remove",
            getString(R.string.qbt_ratio_act_superseeding) to "EnableSuperSeeding",
            getString(R.string.qbt_ratio_act_remove_content) to "RemoveWithContent",
        )
        val currentAction = actions.firstOrNull { it.second == torrent.shareLimitAction }
            ?: actions.first()
        var selectedAction = currentAction.second
        view.shareLimitActionDropdown.setText(currentAction.first, false)
        view.shareLimitActionDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, actions.map { it.first })
        )
        view.shareLimitActionDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedAction = actions[position].second
        }

        view.sequentialSwitch.isChecked = torrent.sequential
        view.firstLastSwitch.isChecked = torrent.firstLastPiecePrio

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_action_options)
            .setView(view.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // paths + TMM
                val savePath = view.savePathInput.text?.toString()?.trim().orEmpty()
                if (savePath.isNotEmpty() && savePath != (props?.savePath ?: torrent.savePath)) {
                    overviewViewModel.setLocation(savePath)
                }
                if (view.autoTmmSwitch.isChecked != torrent.autoTmm) {
                    overviewViewModel.setAutoTmm(view.autoTmmSwitch.isChecked)
                }
                if (view.downloadPathSwitch.isChecked) {
                    val dlPath = view.downloadPathInput.text?.toString()?.trim().orEmpty()
                    if (dlPath.isNotBlank()) overviewViewModel.setDownloadPath(dlPath)
                }

                // speed limits (KiB/s -> bytes/s, 0 = unlimited)
                overviewViewModel.setDownloadLimit(textToLimit(view.torrentDownloadLimit.text?.toString()) * 1024)
                overviewViewModel.setUploadLimit(textToLimit(view.torrentUploadLimit.text?.toString()) * 1024)

                // share limits
                val ratio = when (view.shareLimitMode.checkedRadioButtonId) {
                    R.id.share_limit_global -> -2.0
                    R.id.share_limit_disable -> -1.0
                    else -> view.torrentRatioLimit.text?.toString()?.trim()
                        ?.toDoubleOrNull()?.takeIf { it >= 0 } ?: -2.0
                }
                val seedTime = when (view.shareLimitMode.checkedRadioButtonId) {
                    R.id.share_limit_global -> -2
                    R.id.share_limit_disable -> -1
                    else -> view.torrentSeedingTimeLimit.text?.toString()?.trim()
                        ?.toIntOrNull()?.takeIf { it >= 0 } ?: -2
                }
                val inactive = when (view.shareLimitMode.checkedRadioButtonId) {
                    R.id.share_limit_global -> -2
                    R.id.share_limit_disable -> -1
                    else -> view.torrentInactiveSeedingTimeLimit.text?.toString()?.trim()
                        ?.toIntOrNull()?.takeIf { it >= 0 } ?: -2
                }
                overviewViewModel.setShareLimits(ratio, seedTime, inactive, selectedAction)

                if (view.sequentialSwitch.isChecked != torrent.sequential) {
                    overviewViewModel.toggleSequential()
                }
                if (view.firstLastSwitch.isChecked != torrent.firstLastPiecePrio) {
                    overviewViewModel.toggleFirstLastPiece()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** qBC SetCategoryDialog parity: single-choice category chips. */
    private fun showCategoryDialog() {
        overviewViewModel.loadCategories()
        val torrent = overviewViewModel.torrent.value ?: return
        val view = layoutInflater.inflate(R.layout.dialog_chips, null)
        val group = view.findViewById<ChipGroup>(R.id.chips_group)
        var selected: String? = torrent.category.ifBlank { null }

        lifecycleScope.launch {
            val categories = overviewViewModel.categories.first { it != null } ?: return@launch
            if (categories.isEmpty()) {
                view.findViewById<android.widget.TextView>(R.id.empty_text)?.run {
                    text = getString(R.string.torrent_no_categories)
                    visibility = android.view.View.VISIBLE
                }
            }
            categories.forEach { (name, _) ->
                val chip = layoutInflater.inflate(R.layout.item_tag_chip, group, false) as Chip
                chip.text = name
                chip.isCheckable = true
                chip.isChecked = name == selected
                chip.setOnClickListener {
                    selected = if (selected == name) null else name
                }
                group.addView(chip)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_action_category)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selected != torrent.category.ifBlank { null }) {
                    overviewViewModel.setCategory(selected)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** qBC SetTagsDialog parity: multi-select tag chips. */
    private fun showTagsDialog() {
        overviewViewModel.loadTags()
        val torrent = overviewViewModel.torrent.value ?: return
        val view = layoutInflater.inflate(R.layout.dialog_chips, null)
        val group = view.findViewById<ChipGroup>(R.id.chips_group)
        val current = torrent.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val selected = current.toMutableSet()

        lifecycleScope.launch {
            val tags = overviewViewModel.tags.first { it != null } ?: return@launch
            if (tags.isEmpty()) {
                view.findViewById<android.widget.TextView>(R.id.empty_text)?.run {
                    text = getString(R.string.torrent_no_tags)
                    visibility = android.view.View.VISIBLE
                }
            }
            tags.forEach { tag ->
                val chip = layoutInflater.inflate(R.layout.item_tag_chip, group, false) as Chip
                chip.text = tag
                chip.isCheckable = true
                chip.isChecked = tag in selected
                chip.setOnClickListener {
                    if (!selected.add(tag)) selected.remove(tag)
                }
                group.addView(chip)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_action_tags)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selected != current.toSet()) {
                    overviewViewModel.setTags(selected.toList())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRecheck() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.force_recheck_torrent)
            .setMessage(R.string.torrent_force_recheck_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ -> overviewViewModel.recheck() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        val view = layoutInflater.inflate(R.layout.dialog_delete_torrent, null)
        val deleteFiles = view.findViewById<MaterialCheckBox>(R.id.delete_with_downloaded_files)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.delete) { _, _ ->
                overviewViewModel.delete(deleteFiles.isChecked) { finish() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddTrackersDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        // qBC AddTrackersDialog: multi-line, ONE tracker URL per line
        input?.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_trackers_action_add)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val raw = input?.text?.toString().orEmpty()
                val urls = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (urls.isNotEmpty()) trackersViewModel.addTrackers(urls.joinToString("\n"))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** qBC AddPeersDialog parity: one "host:port" per line or | separated. */
    private fun showAddPeersDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        // qBC AddPeersDialog: multi-line, one host:port per line
        input?.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_peers_action_add)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val raw = input?.text?.toString().orEmpty()
                val peers = raw.split('\n', '|').map { it.trim() }.filter { it.isNotEmpty() }
                if (peers.isNotEmpty()) peersViewModel.addPeers(peers)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddWebSeedsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_web_seed_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input?.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty()) webSeedsViewModel.addWebSeeds(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("torrent", text))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private var pendingExport: ByteArray? = null

    private fun writeExport(uri: Uri) {
        val bytes = pendingExport ?: return
        pendingExport = null
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }.onSuccess {
            Snackbar.make(binding.coordinatorLayout, R.string.torrent_export_success, Snackbar.LENGTH_SHORT).show()
        }.onFailure {
            Snackbar.make(binding.coordinatorLayout, R.string.torrent_export_error, Snackbar.LENGTH_LONG).show()
        }
    }

    /** Compact number rendering for prefilled share limits. */
    private fun formatLimit(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /** KiB/s text: -1 = unlimited, 0 = unlimited, n = n KiB/s. */
    private fun limitToText(bytesPerSec: Long): String = when {
        bytesPerSec < 0 -> "-1"
        bytesPerSec == 0L -> "-1"
        else -> (bytesPerSec / 1024).coerceAtLeast(1).toString()
    }

    private fun textToLimit(text: String?): Long {
        val v = text?.trim()?.toLongOrNull() ?: -1L
        return if (v <= 0L) -1L else v
    }

    private class DetailPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> InfoFragment()
            1 -> FilesFragment()
            2 -> TrackersFragment()
            3 -> PeersFragment()
            else -> WebSeedsFragment()
        }

        override fun getItemCount() = TAB_TITLES.size
    }

    companion object {
        private val TAB_TITLES = intArrayOf(
            R.string.tab_overview,
            R.string.tab_files,
            R.string.tab_trackers,
            R.string.tab_peers,
            R.string.tab_web_seeds,
        )
        const val TAB_FILES = 1
        const val TAB_TRACKERS = 2
        const val TAB_PEERS = 3
        const val TAB_WEBSEEDS = 4
        private const val EXTRA_HASH = "hash"
        private const val EXTRA_NAME = "name"

        /** qBC reannounce gate: engine-side no-op states. */
        private val REANNOUNCE_DEAD_STATES = setOf(
            "pausedup", "pauseddl", "stoppedup", "stoppeddl",
            "queuedup", "queueddl", "error", "missingfiles",
            "checkingup", "checkingdl", "checkingresumeData",
        )

        fun start(context: Context, hash: String, name: String) {
            context.startActivity(
                Intent(context, DetailActivity::class.java)
                    .putExtra(EXTRA_HASH, hash)
                    .putExtra(EXTRA_NAME, name)
            )
        }
    }
}
