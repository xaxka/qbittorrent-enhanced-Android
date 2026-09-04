package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ActivityQbSettingsBinding
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Full qBittorrent preferences editor — the Android counterpart of the
 * WebUI's Tools-Options dialog. Reads the live settings of the connected
 * qBittorrent instance (bundled engine or remote server) and writes user
 * edits back through the same API the official WebUI uses, so every setting
 * takes effect immediately, exactly like on the desktop.
 * Opened from Settings, hosted IN PLACE — no separate window.
 */
class QBSettingsFragment : Fragment() {

    private var _binding: ActivityQbSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QBSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ActivityQbSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        errorDialog = MaterialAlertDialogBuilder(requireContext())
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
                    Toast.makeText(requireContext(), R.string.qbt_no_changes, Toast.LENGTH_SHORT).show()

                success && message != null -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.qbt_saved_fmt, message.toIntOrNull() ?: 0),
                        Toast.LENGTH_SHORT,
                    ).show()
                    (activity as? MainActivity)?.popPage()
                }

                message == QBSettingsViewModel.ERR_USERNAME ->
                    Toast.makeText(requireContext(), R.string.qbt_webui_username_short, Toast.LENGTH_LONG).show()

                message == QBSettingsViewModel.ERR_PASSWORD ->
                    Toast.makeText(requireContext(), R.string.qbt_webui_password_short, Toast.LENGTH_LONG).show()

                message == null ->
                    Toast.makeText(requireContext(), R.string.qbt_not_loaded, Toast.LENGTH_SHORT).show()

                else ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.qbt_save_failed_fmt, message),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    private class QBPrefsPagerAdapter(fragment: Fragment) :
        FragmentStateAdapter(fragment) {

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
