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
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityLogBinding
import io.github.xixka.qbittorrent.databinding.ItemSearchPluginBinding
import io.github.xixka.qbittorrent.model.SearchPlugin
import io.github.xixka.qbittorrent.ui.main.MainActivity
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch

/**
 * Search plugin manager (qBitController SearchPluginsScreen parity): enable /
 * disable, install from URL, uninstall, update all — LibreTorrent list style.
 * Pushed as an IN-PLACE sub-page of the search screen — no new window.
 */
class SearchPluginsFragment : Fragment() {

    private var _binding: ActivityLogBinding? = null
    private val binding get() = _binding!!

    private var plugins: List<SearchPlugin> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ActivityLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.typeChipGroup.visibility = View.GONE
        binding.appBar.setTitle(R.string.search_plugins)
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.inflateMenu(R.menu.search_plugins)
        binding.appBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.install_plugin_menu -> { showInstallDialog(); true }
                R.id.update_plugins_menu -> { updatePlugins(); true }
                else -> false
            }
        }
        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = PluginAdapter()
        binding.logList.setEmptyView(binding.emptyView)
        binding.emptyView.setText(R.string.search_plugins_empty)
        binding.emptyView.setIconResource(R.drawable.ic_extension_24px)
        applyWindowInsets(
            child = binding.logList,
            sideMask = WindowInsetsSide.LEFT or WindowInsetsSide.RIGHT,
        )
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
            (binding.logList.adapter as? PluginAdapter)?.submitList(plugins)
        }
    }

    private fun showInstallDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.hint = "https://…/plugin.py"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_install_plugin)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input?.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = runCatching {
                            ServiceLocator.repository(requireContext()).searchInstallPlugin(url)
                        }
                        android.widget.Toast.makeText(
                            requireContext(),
                            if (result.isSuccess) R.string.search_plugin_installed
                            else R.string.rss_action_failed,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        load()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updatePlugins() {
        lifecycleScope.launch {
            runCatching { ServiceLocator.repository(requireContext()).searchUpdatePlugins() }
            load()
        }
    }

    private fun togglePlugin(plugin: SearchPlugin, enable: Boolean) {
        lifecycleScope.launch {
            runCatching {
                ServiceLocator.repository(requireContext())
                    .searchEnablePlugin(listOf(plugin.name), enable)
            }
            load()
        }
    }

    private fun confirmUninstall(plugin: SearchPlugin) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(plugin.fullName.ifBlank { plugin.name })
            .setMessage(getString(R.string.search_uninstall_confirm, plugin.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        ServiceLocator.repository(requireContext())
                            .searchUninstallPlugin(listOf(plugin.name))
                    }
                    load()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class PluginAdapter :
        ListAdapter<SearchPlugin, PluginAdapter.Holder>(DIFF) {

        inner class Holder(private val b: ItemSearchPluginBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(plugin: SearchPlugin) {
                b.pluginName.text = plugin.fullName.ifBlank { plugin.name }
                b.pluginVersion.text = getString(R.string.search_plugin_version, plugin.version)
                b.pluginSwitch.isChecked = plugin.enabled
                b.pluginSwitch.setOnCheckedChangeListener { _, checked -> togglePlugin(plugin, checked) }
                b.card.setOnLongClickListener {
                    confirmUninstall(plugin)
                    true
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
