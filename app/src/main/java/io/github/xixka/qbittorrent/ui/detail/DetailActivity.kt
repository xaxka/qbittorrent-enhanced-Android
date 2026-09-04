package io.github.xixka.qbittorrent.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityDetailBinding
import io.github.xixka.qbittorrent.databinding.DialogTorrentLimitsBinding
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch

/**
 * Torrent details, ported from LibreTorrent's TorrentDetailsFragment
 * (GPL-3.0): toolbar with back navigation, scrollable tabs, ViewPager2 pages
 * (overview / files / trackers / peers / pieces) and the full qBittorrent
 * per-torrent action set: rename, change location, speed & share limits,
 * super seeding, tracker management.
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels {
        DetailViewModel.factory(application, intent.getStringExtra(EXTRA_HASH) ?: "")
    }

    private val title by lazy { intent.getStringExtra(EXTRA_NAME) ?: "" }

    /**
     * Shared detail state for the tab fragments. They resolve the ViewModel
     * through this property INSTEAD of activityViewModels() — the plain
     * activityViewModels() delegate would fall back to the default factory,
     * which cannot construct the (Application, String) signature and crashed
     * the app the moment a torrent was tapped ("Cannot create an instance of
     * class DetailViewModel"). Going through here always uses the hash-aware
     * factory registered above.
     */
    val detailViewModel: DetailViewModel
        get() = viewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Material You dynamic colors (default on, Android 12+)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // keep list pages clear of the navigation bar / display cutouts
        applyWindowInsets(child = binding.viewPager, sideMask = WindowInsetsSide.BOTTOM)

        // LibreTorrent-style: plain MaterialToolbar with app:menu, no setSupportActionBar
        binding.appBar.title = title

        binding.viewPager.adapter = DetailPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.attach()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = viewModel.setTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.appBar.setNavigationOnClickListener { finishAfterTransition() }
        binding.appBar.setOnMenuItemClickListener { item -> onMenuItem(item.itemId) }

        // qBitController parity: the pause/resume toolbar action reflects
        // the torrent's live state — play icon + "Resume" while stopped,
        // pause icon + "Pause" while running (previously a static pause
        // icon that did the opposite of what it showed when paused).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { st ->
                    val paused = st.info?.isPaused ?: false
                    binding.appBar.menu.findItem(R.id.pause_resume_torrent_menu)?.apply {
                        setIcon(
                            if (paused) R.drawable.ic_play_arrow_24px
                            else R.drawable.ic_pause_24px
                        )
                        setTitle(if (paused) R.string.resume_torrent else R.string.pause_torrent)
                    }
                }
            }
        }
    }

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.pause_resume_torrent_menu -> {
            if (viewModel.state.value.info?.isPaused == true) viewModel.resume() else viewModel.pause()
            true
        }

        R.id.delete_torrent_menu -> {
            confirmDelete()
            true
        }

        R.id.rename_torrent_menu -> {
            showRenameDialog()
            true
        }

        R.id.change_location_menu -> {
            showLocationDialog()
            true
        }

        R.id.torrent_limits_menu -> {
            showLimitsDialog()
            true
        }

        R.id.super_seeding_menu -> {
            showSuperSeedingDialog()
            true
        }

        R.id.force_recheck_torrent_menu -> {
            viewModel.recheck()
            true
        }

        R.id.force_announce_torrent_menu -> {
            viewModel.reannounce()
            true
        }

        R.id.share_magnet_menu -> {
            val hash = viewModel.hash
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "magnet:?xt=urn:btih:$hash")
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_magnet)))
            true
        }

        R.id.add_trackers_menu -> {
            showAddTrackersDialog()
            true
        }

        else -> false
    }

    // ---------------- per-torrent dialogs (qBitController parity) ----------------

    private fun showRenameDialog() {
        val current = viewModel.state.value.info?.name ?: return
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(current)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_torrent)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.rename(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLocationDialog() {
        val current = viewModel.state.value.properties?.savePath
            ?: viewModel.state.value.info?.savePath.orEmpty()
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(current)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.change_save_location)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val path = input?.text?.toString()?.trim().orEmpty()
                if (path.isNotEmpty()) viewModel.setLocation(path)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Per-torrent speed + share limits, qBitController TorrentLimitsDialog
     * parity: download/upload limits (KiB/s, 0 = unlimited) and share ratio /
     * seeding time / inactive seeding time limits (-2 = global default,
     * -1 = no limit) plus the action taken when a limit is reached — all
     * prefilled from the torrent's current state so a plain OK never
     * silently resets custom limits.
     */
    private fun showLimitsDialog() {
        val info = viewModel.state.value.info
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_torrent_limits, null)
        val dl = view.findViewById<TextInputEditText>(R.id.torrent_download_limit)
        val ul = view.findViewById<TextInputEditText>(R.id.torrent_upload_limit)
        val ratio = view.findViewById<TextInputEditText>(R.id.torrent_ratio_limit)
        val seedTime = view.findViewById<TextInputEditText>(R.id.torrent_seeding_time_limit)
        val inactiveSeed = view.findViewById<TextInputEditText>(R.id.torrent_inactive_seeding_time_limit)
        val action = view.findViewById<MaterialAutoCompleteTextView>(R.id.share_limit_action_dropdown)

        val props = viewModel.state.value.properties
        val dlLimit = (props?.dlLimit ?: -1L).coerceAtLeast(-1L)
        val upLimit = (props?.upLimit ?: -1L).coerceAtLeast(-1L)
        dl?.setText(limitToText(dlLimit))
        ul?.setText(limitToText(upLimit))

        // -2 = global default, -1 = no limit, n > 0 = the limit itself
        ratio?.setText(
            info?.ratioLimit?.let { if (it <= 0) it.toInt().toString() else formatLimit(it) } ?: "-2"
        )
        seedTime?.setText(info?.seedingTimeLimit?.toString() ?: "-2")
        inactiveSeed?.setText(info?.inactiveSeedingTimeLimit?.toString() ?: "-2")

        // Action taken when a limit is reached (qB 5.x requires it on save)
        val actions = listOf(
            getString(R.string.share_limit_action_default) to "Default",
            getString(R.string.qbt_ratio_act_stop) to "Stop",
            getString(R.string.qbt_ratio_act_remove) to "Remove",
            getString(R.string.qbt_ratio_act_superseeding) to "EnableSuperSeeding",
            getString(R.string.qbt_ratio_act_remove_content) to "RemoveWithContent",
        )
        val currentAction = actions.firstOrNull { it.second == info?.shareLimitAction }
            ?: actions.first()
        action?.setText(currentAction.first, false)
        action?.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, actions.map { it.first })
        )
        var selectedAction = currentAction.second
        action?.setOnItemClickListener { _, _, position, _ ->
            selectedAction = actions[position].second
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_limits_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val ratioValue = ratio?.text?.toString()?.trim()?.toDoubleOrNull() ?: -2.0
                val seedValue = seedTime?.text?.toString()?.trim()?.toIntOrNull() ?: -2
                val inactiveValue = inactiveSeed?.text?.toString()?.trim()?.toIntOrNull() ?: -2
                viewModel.setShareLimits(ratioValue, seedValue, inactiveValue, selectedAction) { e ->
                    Toast.makeText(
                        this,
                        getString(R.string.qbt_save_failed_fmt, e.message ?: ""),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                lifecycleScope.launch {
                    runCatching {
                        val dlBytes = textToLimit(dl?.text?.toString()) * 1024
                        val ulBytes = textToLimit(ul?.text?.toString()) * 1024
                        viewModel.setDownloadLimit(dlBytes)
                        viewModel.setUploadLimit(ulBytes)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Compact number rendering for prefilled share limits (2.5 not 2.5000001). */
    private fun formatLimit(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /** KiB/s text: -1 = unlimited, 0 = unlimited, n = n KiB/s. */
    private fun limitToText(bytesPerSec: Long): String =
        when {
            bytesPerSec < 0 -> "-1"
            bytesPerSec == 0L -> "-1"
            else -> (bytesPerSec / 1024).coerceAtLeast(1).toString()
        }

    private fun textToLimit(text: String?): Long {
        val v = text?.trim()?.toLongOrNull() ?: -1L
        return if (v <= 0L) -1L else v
    }

    private fun showSuperSeedingDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_switch, null)
        val sw = view.findViewById<MaterialSwitch>(R.id.dialog_switch)
        sw?.isChecked = viewModel.state.value.properties?.superSeeding
            ?: viewModel.state.value.info?.superSeeding ?: false
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.super_seeding)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.setSuperSeeding(sw?.isChecked == true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddTrackersDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_trackers)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input?.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty()) viewModel.addTracker(url)
            }
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
                viewModel.delete(deleteFiles.isChecked) { finish() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class DetailPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> InfoFragment()
            1 -> FilesFragment()
            2 -> TrackersFragment()
            3 -> PeersFragment()
            else -> PiecesFragment()
        }

        override fun getItemCount() = 5
    }

    companion object {
        private val TAB_TITLES = intArrayOf(
            R.string.tab_overview,
            R.string.tab_files,
            R.string.tab_trackers,
            R.string.tab_peers,
            R.string.tab_pieces,
        )
        private const val EXTRA_HASH = "hash"
        private const val EXTRA_NAME = "name"

        fun start(context: Context, hash: String, name: String) {
            context.startActivity(
                Intent(context, DetailActivity::class.java)
                    .putExtra(EXTRA_HASH, hash)
                    .putExtra(EXTRA_NAME, name)
            )
        }
    }
}
