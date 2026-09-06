package io.github.xixka.qbittorrent.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivitySearchBinding
import io.github.xixka.qbittorrent.databinding.ItemSearchPluginSelectBinding
import io.github.xixka.qbittorrent.model.SearchPlugin
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Search start screen, qBitController SearchStartScreen parity: query and
 * category on the app bar, plugin scope radio group (enabled / all /
 * manually select) with per-plugin checkbox cards below, plugins reload on
 * pull-to-refresh, and the toolbar carries the qBC action set (plugins,
 * start). Starting a search pushes [SearchResultFragment], which runs the
 * engine job — exactly like qBC's two-screen split.
 */
class SearchFragment : Fragment() {

    private var _binding: ActivitySearchBinding? = null
    private val binding get() = _binding!!

    private var plugins: List<SearchPlugin> = emptyList()
    private val selectedPlugins = mutableSetOf<String>()

    /** (code, label) category pairs, qBC's search categories. */
    private val categories = listOf(
        "all" to R.string.search_cat_all,
        "anime" to R.string.search_cat_anime,
        "books" to R.string.search_cat_books,
        "games" to R.string.search_cat_games,
        "movies" to R.string.search_cat_movies,
        "music" to R.string.search_cat_music,
        "pictures" to R.string.search_cat_pictures,
        "software" to R.string.search_cat_software,
        "tv" to R.string.search_cat_tv,
    )

    private var categoryLabels: List<Pair<String, String>> = emptyList()

    private var pluginAdapter: PluginSelectAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ActivitySearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.search_plugins_menu -> {
                    (activity as? MainActivity)?.pushPage(SearchPluginsFragment())
                    true
                }
                R.id.search_start_menu -> {
                    startSearch()
                    true
                }
                else -> false
            }
        }

        // localized category labels
        categoryLabels = categories.map { pair ->
            pair.first to getString(pair.second)
        }
        binding.searchCategoryDropdown?.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categoryLabels.map { it.second }),
        )
        binding.searchCategoryDropdown?.setText(categoryLabels.firstOrNull()?.second ?: "", false)

        arguments?.getString(ARG_PATTERN)?.let { binding.searchPattern?.setText(it) }

        val pluginList = binding.pluginSelectList
        pluginList.layoutManager = LinearLayoutManager(requireContext())
        pluginAdapter = PluginSelectAdapter()
        pluginList.adapter = pluginAdapter

        binding.pluginScopeGroup.setOnCheckedChangeListener { _, _ -> syncPluginSelectionUi() }

        binding.swipeRefresh.setOnRefreshListener { loadPlugins() }

        // The bundled engine deliberately ships without Python, and qB's
        // search plugins only run with one — tell the user up front instead
        // of letting them run into the 409 error.
        binding.localEngineHint?.visibility =
            if (ServiceLocator.prefs(requireContext()).usingLocalEngine) View.VISIBLE else View.GONE

        loadPlugins()

        // entry from the home screen with a preset query: jump straight to
        // the results screen (qBC starts the search on the results screen)
        val preset = arguments?.getString(ARG_PATTERN)?.trim().orEmpty()
        if (preset.isNotEmpty()) {
            binding.searchPattern?.setText(preset)
            (activity as? MainActivity)?.pushPage(
                SearchResultFragment.newInstance(preset, selectedCategory(), selectedPluginScope()),
            )
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun loadPlugins() {
        lifecycleScope.launch {
            plugins = runCatching {
                ServiceLocator.repository(requireContext()).searchPlugins()
            }.getOrDefault(emptyList())
            selectedPlugins.removeAll { name -> plugins.none { it.name == name } }
            pluginAdapter?.submitList(plugins)
            binding.swipeRefresh.isRefreshing = false
            syncPluginSelectionUi()
        }
    }

    /** qBC: the plugin picker only shows for the "manually select" scope. */
    private fun syncPluginSelectionUi() {
        val manual = binding.pluginScopeSelect?.isChecked == true
        binding.pluginSelectList?.visibility = if (manual) View.VISIBLE else View.GONE
        if (manual) pluginAdapter?.notifyDataSetChanged()
    }

    private fun selectedCategory(): String {
        val text = binding.searchCategoryDropdown?.text?.toString() ?: return "all"
        return categoryLabels.firstOrNull { it.second == text }?.first ?: "all"
    }

    /** qBC pluginsParam: "enabled" / "all" / the picked names joined by "|". */
    private fun selectedPluginScope(): String {
        val scope = when {
            binding.pluginScopeEnabled?.isChecked == true -> "enabled"
            binding.pluginScopeAll?.isChecked == true -> "all"
            else -> "selected"
        }
        return if (scope == "selected") selectedPlugins.joinToString("|") else scope
    }

    private fun startSearch() {
        val pattern = binding.searchPattern?.text?.toString()?.trim().orEmpty()
        if (pattern.isEmpty()) {
            binding.searchPattern?.error = getString(R.string.rss_required)
            return
        }
        (activity as? MainActivity)?.pushPage(
            SearchResultFragment.newInstance(pattern, selectedCategory(), selectedPluginScope()),
        )
    }

    /** qBC PluginItem: checkbox card, highlighted when picked. */
    private inner class PluginSelectAdapter :
        RecyclerView.Adapter<PluginSelectAdapter.Holder>() {

        inner class Holder(private val b: ItemSearchPluginSelectBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(plugin: SearchPlugin) {
                b.pluginName.text = plugin.fullName.ifBlank { plugin.name }
                b.pluginCheck.isChecked = plugin.name in selectedPlugins
                b.card.setCardBackgroundColor(
                    if (plugin.name in selectedPlugins) {
                        com.google.android.material.color.MaterialColors.getColor(
                            b.card, com.google.android.material.R.attr.colorSecondaryContainer,
                        )
                    } else {
                        com.google.android.material.color.MaterialColors.getColor(
                            b.card, com.google.android.material.R.attr.colorSurfaceContainer,
                        )
                    },
                )
                b.card.setOnClickListener {
                    if (plugin.name in selectedPlugins) {
                        selectedPlugins.remove(plugin.name)
                    } else {
                        selectedPlugins.add(plugin.name)
                    }
                    b.pluginCheck.isChecked = plugin.name in selectedPlugins
                    b.card.setCardBackgroundColor(
                        if (plugin.name in selectedPlugins) {
                            com.google.android.material.color.MaterialColors.getColor(
                                b.card, com.google.android.material.R.attr.colorSecondaryContainer,
                            )
                        } else {
                            com.google.android.material.color.MaterialColors.getColor(
                                b.card, com.google.android.material.R.attr.colorSurfaceContainer,
                            )
                        },
                    )
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemSearchPluginSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) =
            holder.bind(plugins[position])

        override fun getItemCount() = plugins.size
    }
}
