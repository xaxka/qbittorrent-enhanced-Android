package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import androidx.core.view.WindowCompat
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ActivityQbSettingsBinding
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch

/**
 * Full qBittorrent preferences editor — the Android counterpart of the
 * WebUI's Tools-Options dialog. Reads the live settings of the connected
 * qBittorrent instance (bundled engine or remote server) and writes user
 * edits back through the same API the official WebUI uses, so every setting
 * takes effect immediately, exactly like on the desktop.
 */
class QBSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQbSettingsBinding

    private val viewModel: QBSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Material You dynamic colors (default on, Android 12+)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivityQbSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // keep the form pages clear of the navigation bar in the edge-to-edge layout
        applyWindowInsets(child = binding.viewPager, sideMask = WindowInsetsSide.BOTTOM)

        binding.appBar.setNavigationOnClickListener { finish() }
        binding.appBar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.save_qb_prefs_menu) {
                save()
                true
            } else {
                false
            }
        }

        binding.viewPager.adapter = QBPrefsPagerAdapter(this)
        // keep all seven pages alive so every tab contributes to "save"
        binding.viewPager.offscreenPageLimit = 6
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.attach()

        observe()

        if (savedInstanceState == null) {
            viewModel.load()
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loading.collect { loading ->
                        binding.loadingOverlay.visibility =
                            if (loading && viewModel.raw.value == null) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.error.collect { message ->
                        if (message != null) showError(message)
                    }
                }
            }
        }
    }

    private var errorDialog: android.app.Dialog? = null

    private fun showError(message: String) {
        errorDialog?.dismiss()
        errorDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.qbt_settings_title)
            .setMessage(getString(R.string.qbt_load_failed_fmt, message))
            .setPositiveButton(R.string.retry) { _, _ -> viewModel.retry() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save() {
        viewModel.save { success, message ->
            when {
                success && message == QBSettingsViewModel.NO_CHANGES ->
                    Toast.makeText(this, R.string.qbt_no_changes, Toast.LENGTH_SHORT).show()

                success && message != null -> {
                    Toast.makeText(
                        this,
                        getString(R.string.qbt_saved_fmt, message.toIntOrNull() ?: 0),
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }

                message == QBSettingsViewModel.ERR_USERNAME ->
                    Toast.makeText(this, R.string.qbt_webui_username_short, Toast.LENGTH_LONG).show()

                message == QBSettingsViewModel.ERR_PASSWORD ->
                    Toast.makeText(this, R.string.qbt_webui_password_short, Toast.LENGTH_LONG).show()

                message == null ->
                    Toast.makeText(this, R.string.qbt_not_loaded, Toast.LENGTH_SHORT).show()

                else ->
                    Toast.makeText(
                        this,
                        getString(R.string.qbt_save_failed_fmt, message),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    private class QBPrefsPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> DownloadsPrefsFragment()
            1 -> SpeedPrefsFragment()
            2 -> BitTorrentPrefsFragment()
            3 -> ConnectionPrefsFragment()
            4 -> WebUiPrefsFragment()
            5 -> RssPrefsFragment()
            else -> AdvancedPrefsFragment()
        }

        override fun getItemCount() = TAB_TITLES.size
    }

    companion object {
        private val TAB_TITLES = intArrayOf(
            R.string.qbt_tab_downloads,
            R.string.qbt_tab_speed,
            R.string.qbt_tab_bittorrent,
            R.string.qbt_tab_connection,
            R.string.qbt_tab_webui,
            R.string.qbt_tab_rss,
            R.string.qbt_tab_advanced,
        )
    }
}
