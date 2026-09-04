package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ActivityQbSettingsBinding
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Dynamic qBittorrent preferences editor — the Android counterpart of the
 * WebUI's Tools-Options dialog. The tabs are generated from
 * [QBPrefSchema] plus whatever unknown keys the connected instance reports,
 * so settings introduced by future qBittorrent versions appear (and stay
 * editable) without an app update. Reads the live settings of the connected
 * qBittorrent instance (bundled engine or remote server) and writes user
 * edits back through the same API the official WebUI uses, so every setting
 * takes effect immediately, exactly like on the desktop.
 * Opened from Settings, hosted IN PLACE — no separate window.
 */
class QBSettingsFragment : Fragment() {

    private var _binding: ActivityQbSettingsBinding? = null
    private val binding get() = _binding!!

    // ACTIVITY-scoped on purpose: the tab pages resolve the same instance,
    // so a load here populates every row. (A fragment-scoped viewModel was
    // the original blank-values bug.)
    private val viewModel: QBSettingsViewModel by activityViewModels()

    private var mediator: TabLayoutMediator? = null
    private var errorDialog: android.app.Dialog? = null

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

        binding.viewPager.adapter = SectionsPagerAdapter(this)

        // keep every page alive so switching tabs never loses input focus
        binding.viewPager.offscreenPageLimit = 8

        observe()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sections.collect { sections ->
                (binding.viewPager.adapter as? SectionsPagerAdapter)?.submit(sections)
                binding.viewPager.offscreenPageLimit = sections.size.coerceAtLeast(1)
                attachTabs(sections)
            }
        }

        if (savedInstanceState == null) {
            viewModel.load()
        }
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        _binding = null
        super.onDestroyView()
    }

    /** (Re)builds the tab strip for the current section list. */
    private fun attachTabs(sections: List<PrefSection>) {
        mediator?.detach()
        binding.tabLayout.removeAllTabs()
        val tabLayout: TabLayout = binding.tabLayout
        mediator = TabLayoutMediator(tabLayout, binding.viewPager) { tab, position ->
            sections.getOrNull(position)?.let { tab.setText(it.title) }
        }.also { it.attach() }
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

    private class SectionsPagerAdapter(fragment: Fragment) :
        FragmentStateAdapter(fragment) {

        private var sections: List<PrefSection> = emptyList()

        fun submit(sections: List<PrefSection>) {
            this.sections = sections
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = sections.size

        override fun createFragment(position: Int): Fragment =
            QBPrefsListFragment.newInstance(position)
    }
}
