package io.github.xixka.qbittorrent.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivitySearchBinding
import io.github.xixka.qbittorrent.databinding.ItemSearchResultBinding
import io.github.xixka.qbittorrent.model.SearchResultEntry
import io.github.xixka.qbittorrent.model.SearchPlugin
import io.github.xixka.qbittorrent.ui.addtorrent.AddTorrentActivity
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Engine-side torrent search (qBitController SearchStartScreen +
 * SearchResultScreen parity): starts a /search/start job with pattern,
 * category and plugin scope, polls /search/results until the engine reports
 * "Stopped", and hands the picked result's fileUrl to the add-torrent sheet.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val adapter = ResultAdapter(
        onClick = { entry ->
            if (entry.fileUrl.isNotBlank()) {
                AddTorrentActivity.start(this, entry.fileUrl)
            } else if (entry.descriptionLink.isNotBlank()) {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(entry.descriptionLink)))
                }
            }
        },
        onLongClick = { entry -> showResultMenu(entry) },
    )

    private var searchJob: Job? = null
    private var searchId = -1

    /** (code, label) category pairs, qBC's search categories. */
    private val categories = listOf(
        "all" to "All categories",
        "movies" to "Movies",
        "tv" to "TV shows",
        "music" to "Music",
        "games" to "Games",
        "anime" to "Anime",
        "software" to "Software",
        "pictures" to "Pictures",
        "books" to "Books",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(
            child = binding.resultList,
            sideMask = WindowInsetsSide.LEFT or WindowInsetsSide.RIGHT or WindowInsetsSide.BOTTOM,
        )
        binding.appBar.setNavigationOnClickListener { finish() }
        binding.appBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.search_plugins_menu -> {
                    startActivity(Intent(this, SearchPluginsActivity::class.java))
                    true
                }
                R.id.search_stop_menu -> {
                    stopSearch()
                    true
                }
                else -> false
            }
        }

        binding.resultList.layoutManager = LinearLayoutManager(this)
        binding.resultList.adapter = adapter
        binding.resultList.setEmptyView(binding.emptyView)

        val patternInput = findViewById<TextInputEditText>(R.id.search_pattern)
        val categoryDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.search_category_dropdown)
        val pluginsDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.search_plugins_dropdown)

        // localized labels
        val labels = categories.map { pair ->
            pair.first to getString(labelResFor(pair.first))
        }
        val labelList = labels.map { it.second }
        categoryDropdown?.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labelList)
        )
        categoryDropdown?.setText(labelList.firstOrNull() ?: "All", false)
        pluginsDropdown?.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                listOf(getString(R.string.search_plugins_enabled), getString(R.string.search_plugins_all)),
            )
        )
        pluginsDropdown?.setText(getString(R.string.search_plugins_enabled), false)

        intent.getStringExtra(EXTRA_PATTERN)?.let { patternInput?.setText(it) }

        binding.searchStartButton.setOnClickListener {
            val pattern = patternInput?.text?.toString()?.trim().orEmpty()
            if (pattern.isNotEmpty()) startSearch(pattern, selectedCategory(labels), selectedPluginScope())
        }
        patternInput?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val pattern = v.text.toString().trim()
                if (pattern.isNotEmpty()) startSearch(pattern, selectedCategory(labels), selectedPluginScope())
                true
            } else false
        }

    }

    private fun labelResFor(code: String): Int = when (code) {
        "movies" -> R.string.search_cat_movies
        "tv" -> R.string.search_cat_tv
        "music" -> R.string.search_cat_music
        "games" -> R.string.search_cat_games
        "anime" -> R.string.search_cat_anime
        "software" -> R.string.search_cat_software
        "pictures" -> R.string.search_cat_pictures
        "books" -> R.string.search_cat_books
        else -> R.string.search_cat_all
    }

    private fun selectedCategory(labels: List<Pair<String, String>>): String {
        val dropdown = findViewById<MaterialAutoCompleteTextView>(R.id.search_category_dropdown)
        val text = dropdown?.text?.toString() ?: return "all"
        return labels.firstOrNull { it.second == text }?.first ?: "all"
    }

    private fun selectedPluginScope(): String {
        val dropdown = findViewById<MaterialAutoCompleteTextView>(R.id.search_plugins_dropdown)
        return if (dropdown?.text?.toString() == getString(R.string.search_plugins_all)) "all" else "enabled"
    }

    private fun startSearch(pattern: String, category: String, plugins: String) {
        stopSearch()
        adapter.submitList(emptyList())
        binding.emptyView.setText(R.string.search_running)
        lifecycleScope.launch {
            val start = runCatching {
                ServiceLocator.repository(this@SearchActivity).searchStart(pattern, category, plugins)
            }
            start
                .onSuccess { response ->
                    searchId = response.id
                    pollResults()
                }
                .onFailure { e ->
                    binding.emptyView.setText(R.string.search_no_results)
                    MaterialAlertDialogBuilder(this@SearchActivity)
                        .setTitle(R.string.search_engine_title)
                        .setMessage(e.message ?: getString(R.string.search_start_failed))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
        }
    }

    private fun pollResults() {
        if (searchId < 0) return
        searchJob = lifecycleScope.launch {
            while (isActive && searchId >= 0) {
                val results = runCatching {
                    ServiceLocator.repository(this@SearchActivity).searchResults(searchId)
                }.getOrNull()
                if (results != null) {
                    adapter.submitList(results.results)
                    if (results.status.equals("Stopped", ignoreCase = true)) {
                        binding.emptyView.setText(
                            if (results.results.isEmpty()) R.string.search_no_results else R.string.search_done,
                        )
                        break
                    }
                }
                delay(2000)
            }
        }
    }

    private fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        val id = searchId
        if (id >= 0) {
            searchId = -1
            lifecycleScope.launch {
                runCatching { ServiceLocator.repository(this@SearchActivity).searchStop(id) }
                runCatching { ServiceLocator.repository(this@SearchActivity).searchDelete(id) }
            }
        }
    }

    private fun showResultMenu(entry: SearchResultEntry) {
        val options = listOf(
            getString(R.string.search_add_torrent),
            getString(R.string.search_open_site),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.fileName)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> if (entry.fileUrl.isNotBlank()) AddTorrentActivity.start(this, entry.fileUrl)
                    1 -> runCatching {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(entry.descriptionLink)),
                        )
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        stopSearch()
        super.onDestroy()
    }

    // ---------------- adapter ----------------

    private class ResultAdapter(
        private val onClick: (SearchResultEntry) -> Unit,
        private val onLongClick: (SearchResultEntry) -> Unit,
    ) : ListAdapter<SearchResultEntry, ResultAdapter.Holder>(DIFF) {

        class Holder(private val b: ItemSearchResultBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(entry: SearchResultEntry, onClick: (SearchResultEntry) -> Unit, onLongClick: (SearchResultEntry) -> Unit) {
                b.fileName.text = entry.fileName
                b.fileSite.text = entry.siteUrl.removePrefix("https://").removePrefix("http://")
                b.fileSize.text = entry.fileSize?.takeIf { it > 0 }?.let { Format.size(it) } ?: ""
                b.seedersLeechers.text = buildString {
                    entry.seeders?.let { append("↑ $it ") }
                    entry.leechers?.let { append("↓ $it") }
                }
                b.card.setOnClickListener { onClick(entry) }
                b.card.setOnLongClickListener {
                    onLongClick(entry)
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) =
            holder.bind(getItem(position), onClick, onLongClick)
    }

    companion object {
        const val EXTRA_PATTERN = "pattern"

        fun start(context: android.content.Context, pattern: String? = null) {
            context.startActivity(
                Intent(context, SearchActivity::class.java)
                    .putExtra(EXTRA_PATTERN, pattern)
            )
        }

        private val DIFF = object : DiffUtil.ItemCallback<SearchResultEntry>() {
            override fun areItemsTheSame(oldItem: SearchResultEntry, newItem: SearchResultEntry) =
                oldItem.fileUrl == newItem.fileUrl && oldItem.fileName == newItem.fileName

            override fun areContentsTheSame(oldItem: SearchResultEntry, newItem: SearchResultEntry) =
                oldItem == newItem
        }
    }
}
