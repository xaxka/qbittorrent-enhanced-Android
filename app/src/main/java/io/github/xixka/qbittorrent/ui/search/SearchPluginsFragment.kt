package io.github.xixka.qbittorrent.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.FragmentSearchPluginsBinding
import io.github.xixka.qbittorrent.databinding.ItemSearchPluginBinding
import io.github.xixka.qbittorrent.model.SearchPlugin
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search plugin manager, qBitController SearchPluginsScreen parity: enable
 * toggles and delete picks are STAGED locally (deleted cards dim and their
 * switch locks) and only apply when the toolbar save action runs — install
 * from URL and update-all report through snackbars, everything reloads on
 * pull-to-refresh. Pushed as an IN-PLACE sub-page of the search screen.
 */
class SearchPluginsFragment : Fragment() {

    private var _binding: FragmentSearchPluginsBinding? = null
    private val binding get() = _binding!!

    private var plugins: List<SearchPlugin> = emptyList()

    /** Staged enable states (qBC pluginsEnabledState), name → enabled. */
    private val stagedEnabled = mutableMapOf<String, Boolean>()

    /** Staged deletions (qBC pluginsToDelete). */
    private val stagedDelete = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchPluginsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.inflateMenu(R.menu.search_plugins)
        binding.appBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.save_plugins_menu -> { saveStagedChanges(); true }
                R.id.install_plugin_menu -> { showInstallDialog(); true }
                R.id.update_plugins_menu -> { updatePlugins(); true }
                else -> false
            }
        }
        binding.pluginList.layoutManager = LinearLayoutManager(requireContext())
        binding.pluginList.adapter = PluginAdapter()
        binding.pluginList.setEmptyView(binding.emptyView)
        binding.emptyView.setText(R.string.search_plugins_empty)
        binding.emptyView.setIconResource(R.drawable.ic_extension_24px)

        binding.swipeRefresh.setOnRefreshListener { load() }
        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun load() {
        lifecycleScope.launch {
            plugins = runCatching {
                ServiceLocator.repository(requireContext()).searchPlugins()
            }.getOrDefault(emptyList())
            // staged entries for gone plugins are meaningless
            stagedDelete.removeAll { name -> plugins.none { it.name == name } }
            stagedEnabled.keys.removeAll { name -> plugins.none { it.name == name } }
            (binding.pluginList.adapter as? PluginAdapter)?.submitList(plugins)
            binding.swipeRefresh.isRefreshing = false
        }
    }

    /** qBC savePlugins: apply staged enables and deletions in one go. */
    private fun saveStagedChanges() {
        if (stagedEnabled.isEmpty() && stagedDelete.isEmpty()) return
        lifecycleScope.launch {
            val repo = ServiceLocator.repository(requireContext())
            var ok = true
            val enableOn = stagedEnabled.filterValues { it }.keys.toList()
            val enableOff = stagedEnabled.filterValues { !it }.keys.toList()
            if (enableOn.isNotEmpty()) {
                ok = runCatching { repo.searchEnablePlugin(enableOn, true) }.isSuccess && ok
            }
            if (enableOff.isNotEmpty()) {
                ok = runCatching { repo.searchEnablePlugin(enableOff, false) }.isSuccess && ok
            }
            if (stagedDelete.isNotEmpty()) {
                ok = runCatching { repo.searchUninstallPlugin(stagedDelete.toList()) }.isSuccess && ok
            }
            stagedEnabled.clear()
            stagedDelete.clear()
            snackbar(if (ok) R.string.search_plugins_saved else R.string.rss_action_failed)
            load()
        }
    }

    private fun showInstallDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.hint = "https://…/plugin.py"
        // qBC's install dialog accepts MULTIPLE sources, one per line
        input?.setSingleLine(false)
        input?.minLines = 2
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_install_plugin)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val sources = input?.text?.toString()?.trim().orEmpty()
                if (sources.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = runCatching {
                            ServiceLocator.repository(requireContext()).searchInstallPlugin(sources)
                        }
                        snackbar(if (result.isSuccess) R.string.search_plugin_installed else R.string.rss_action_failed)
                        // the engine needs a moment before the new plugin shows up
                        delay(1000)
                        load()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updatePlugins() {
        lifecycleScope.launch {
            val result = runCatching {
                ServiceLocator.repository(requireContext()).searchUpdatePlugins()
            }
            snackbar(if (result.isSuccess) R.string.search_plugin_updated else R.string.rss_action_failed)
            delay(1000)
            load()
        }
    }

    private fun snackbar(res: Int) {
        _binding ?: return
        Snackbar.make(binding.root, res, Snackbar.LENGTH_SHORT).show()
    }

    private inner class PluginAdapter :
        ListAdapter<SearchPlugin, PluginAdapter.Holder>(DIFF) {

        inner class Holder(private val b: ItemSearchPluginBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(plugin: SearchPlugin) {
                val enabled = stagedEnabled[plugin.name] ?: plugin.enabled
                val deleted = plugin.name in stagedDelete

                b.pluginName.text = plugin.fullName.ifBlank { plugin.name }
                b.pluginVersion.text = plugin.version
                b.pluginUrl.text = plugin.url
                // recycle safety: detach the previous item's listener first,
                // otherwise assigning isChecked writes staged state for the
                // plugin this holder rendered before this one
                b.pluginSwitch.setOnCheckedChangeListener(null)
                b.pluginSwitch.isChecked = enabled
                b.pluginSwitch.isEnabled = !deleted
                b.pluginDelete.setImageResource(
                    if (deleted) R.drawable.ic_undo_24px else R.drawable.ic_delete_24px,
                )
                b.card.alpha = if (deleted) 0.5f else 1f

                b.pluginSwitch.setOnCheckedChangeListener { _, checked ->
                    stagedEnabled[plugin.name] = checked
                }
                b.pluginDelete.setOnClickListener {
                    if (plugin.name in stagedDelete) {
                        stagedDelete.remove(plugin.name)
                    } else {
                        stagedDelete.add(plugin.name)
                    }
                    (binding.pluginList.adapter as? PluginAdapter)?.notifyDataSetChanged()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemSearchPluginBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SearchPlugin>() {
            override fun areItemsTheSame(oldItem: SearchPlugin, newItem: SearchPlugin) =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: SearchPlugin, newItem: SearchPlugin) =
                oldItem == newItem
        }
    }
}
