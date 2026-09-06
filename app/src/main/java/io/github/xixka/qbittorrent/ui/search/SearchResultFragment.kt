package io.github.xixka.qbittorrent.ui.search

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.DialogSearchDetailsBinding
import io.github.xixka.qbittorrent.databinding.FragmentSearchResultsBinding
import io.github.xixka.qbittorrent.databinding.ItemSearchResultBinding
import io.github.xixka.qbittorrent.model.SearchResultEntry
import io.github.xixka.qbittorrent.ui.addtorrent.AddTorrentActivity
import io.github.xixka.qbittorrent.ui.main.MainActivity
import io.github.xixka.qbittorrent.util.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Search results, qBitController SearchResultScreen parity: runs the engine
 * search job started from [SearchFragment] and polls until "Stopped", shows
 * the "showing X of Y" count bar, filters by name (app-bar search swap),
 * the seed/size filter dialog, sorting with reverse, per-result details
 * dialog (size / site info rows + colored seeder / leecher cards + download
 * and open-site actions), long-press multi selection with the bottom bar
 * (download selected in one addTorrent call / select all / inverse), and a
 * progress indicator while the search is still running.
 */
class SearchResultFragment : Fragment() {

    private var _binding: FragmentSearchResultsBinding? = null
    private val binding get() = _binding!!

    private val pattern by lazy { arguments?.getString(ARG_PATTERN).orEmpty() }
    private val category by lazy { arguments?.getString(ARG_CATEGORY) ?: "all" }
    private val plugins by lazy { arguments?.getString(ARG_PLUGINS) ?: "enabled" }

    private var searchJob: Job? = null
    private var searchId = -1
    private var searchRunning = false

    private var allResults: List<SearchResultEntry> = emptyList()
    private var nameQuery = ""
    private var searchMode = false
    private val selected = mutableSetOf<String>()

    private enum class Sort { NAME, SIZE, SEEDERS, LEECHERS, ENGINE }

    private var sort = Sort.NAME
    private var reverse = false

    private var filterSeedsMin: Int? = null
    private var filterSeedsMax: Int? = null
    private var filterSizeMin: Long? = null
    private var filterSizeMax: Long? = null

    private val sizeUnits = listOf(
        R.string.search_size_unit_bytes to 1L,
        R.string.search_size_unit_kib to (1L shl 10),
        R.string.search_size_unit_mib to (1L shl 20),
        R.string.search_size_unit_gib to (1L shl 30),
        R.string.search_size_unit_tib to (1L shl 40),
        R.string.search_size_unit_pib to (1L shl 50),
        R.string.search_size_unit_eib to (1L shl 60),
    )

    private val searchBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitSearchMode()
    }

    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            selected.clear()
            syncSelectionUi()
        }
    }

    private val adapter = ResultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // keep the engine-side search job across rotation: stopAndDeleteSearch
        // is skipped for config changes, the new instance resumes polling
        searchId = savedInstanceState?.getInt(STATE_SEARCH_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appBar.title = pattern.ifBlank { getString(R.string.search_engine_title) }
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.inflateMenu(R.menu.search_results)
        binding.appBar.setOnMenuItemClickListener { onMenuItem(it.itemId) }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, searchBackCallback,
        )
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, selectionBackCallback,
        )

        binding.resultList.layoutManager = LinearLayoutManager(requireContext())
        binding.resultList.adapter = adapter
        binding.resultList.setEmptyView(binding.emptyView)
        binding.emptyView.setText(R.string.search_running)

        binding.swipeRefresh.setOnRefreshListener { pollResults() }

        binding.searchInput.addTextChangedListener { text ->
            nameQuery = text?.toString().orEmpty()
            applyPipeline()
        }

        // selection bottom bar
        binding.selectionBar.setNavigationOnClickListener {
            selected.clear()
            syncSelectionUi()
        }
        binding.selectionBar.inflateMenu(R.menu.search_selection)
        binding.selectionBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search_download_selected -> downloadSelected()
                R.id.action_search_select_all -> {
                    allResults.forEach { r -> selected.add(r.key()) }
                    syncSelectionUi()
                }
                R.id.action_search_select_inverse -> {
                    val next = allResults.map { it.key() }.filter { it !in selected }
                    selected.clear()
                    selected.addAll(next)
                    syncSelectionUi()
                }
            }
            true
        }

        if (searchId < 0) {
            startSearch()
        } else {
            // restored after rotation: the engine job is still alive, resume
            // polling instead of silently restarting the search from scratch
            searchRunning = true
            syncRunningState()
            pollResults()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SEARCH_ID, searchId)
    }

    override fun onDestroy() {
        // qBC stops and deletes the engine job when the screen goes away —
        // but a rotation keeps the job: the recreated instance resumes it
        if (activity?.isChangingConfigurations != true) {
            stopAndDeleteSearch()
        }
        searchJob?.cancel()
        _binding = null
        super.onDestroy()
    }

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.search_filter_menu -> {
            if (searchMode) exitSearchMode() else enterSearchMode()
            true
        }
        R.id.search_filter_dialog_menu -> {
            showFilterDialog()
            true
        }
        R.id.search_sort_menu -> {
            showSortMenu()
            true
        }
        R.id.search_stop_menu -> {
            if (searchRunning) stopSearch()
            true
        }
        else -> false
    }

    // ---------------- search lifecycle ----------------

    private fun startSearch() {
        lifecycleScope.launch {
            val start = runCatching {
                ServiceLocator.repository(requireContext()).searchStart(pattern, category, plugins)
            }
            start
                .onSuccess { response ->
                    searchId = response.id
                    searchRunning = true
                    syncRunningState()
                    pollResults()
                }
                .onFailure { e ->
                    val message = e.message ?: getString(R.string.search_start_failed)
                    val localEngine = ServiceLocator.prefs(requireContext()).usingLocalEngine
                    val pythonMissing = message.contains("python", ignoreCase = true) ||
                        (localEngine && e is io.github.xixka.qbittorrent.api.QBApiException && e.code == 409)
                    binding.emptyView.setText(
                        if (pythonMissing) R.string.search_python_missing else R.string.search_no_results,
                    )
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.search_engine_title)
                        .setMessage(
                            if (pythonMissing) getString(R.string.search_python_missing) else message,
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
        }
    }

    private fun pollResults() {
        if (searchId < 0) return
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            while (isActive && searchId >= 0) {
                val b = _binding
                if (b == null) break // view already torn down
                // poll only while the screen is visible: no background traffic
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    val results = runCatching {
                        ServiceLocator.repository(requireContext()).searchResults(searchId)
                    }.getOrNull()
                    if (results != null) {
                        allResults = results.results
                        applyPipeline()
                        b.resultCount.isVisible = true
                        b.resultCount.text = getString(
                            R.string.search_result_showing_count,
                            adapter.itemCount,
                            allResults.size,
                        )
                        if (results.status.equals("Stopped", ignoreCase = true)) {
                            searchRunning = false
                            syncRunningState()
                            b.emptyView.setText(
                                if (allResults.isEmpty()) R.string.search_no_results else R.string.search_done,
                            )
                            break
                        }
                    }
                }
                delay(2000)
            }
            _binding?.swipeRefresh?.isRefreshing = false
        }
    }

    private fun stopSearch() {
        val id = searchId
        lifecycleScope.launch {
            runCatching { ServiceLocator.repository(requireContext()).searchStop(id) }
            snackbar(R.string.search_result_stop_done)
        }
    }

    /** qBC: leaving the screen stops and deletes the engine-side job. */
    private fun stopAndDeleteSearch() {
        val id = searchId
        if (id < 0) return
        searchId = -1
        // lifecycleScope dies with this destroy — run the cleanup with
        // NonCancellable (it survives the cancelled scope) and against the
        // application context, or the engine-side job would leak
        val appContext = requireContext().applicationContext
        lifecycleScope.launch(NonCancellable) {
            runCatching { ServiceLocator.repository(appContext).searchStop(id) }
            runCatching { ServiceLocator.repository(appContext).searchDelete(id) }
        }
    }

    private fun syncRunningState() {
        binding.appBar.menu.findItem(R.id.search_stop_menu)?.isEnabled = searchRunning
        binding.appBar.menu.findItem(R.id.search_stop_menu)?.icon?.alpha =
            if (searchRunning) 255 else 128
    }

    // ---------------- filter / sort / selection ----------------

    private fun enterSearchMode() {
        searchMode = true
        searchBackCallback.isEnabled = true
        binding.searchInput.isVisible = true
        binding.appBar.title = " "
        binding.searchInput.requestFocus()
    }

    private fun exitSearchMode() {
        searchMode = false
        searchBackCallback.isEnabled = false
        binding.searchInput.isVisible = false
        binding.searchInput.setText("")
        nameQuery = ""
        applyPipeline()
        binding.appBar.title = pattern.ifBlank { getString(R.string.search_engine_title) }
    }

    /** qBC sort dropdown: radio options + a reverse checkbox. */
    private fun showSortMenu() {
        val anchor = binding.appBar.findViewById<View>(R.id.search_sort_menu) ?: return
        PopupMenu(requireContext(), anchor).apply {
            menu.add(1, 1, 0, R.string.search_result_sort_name).setCheckable(true)
            menu.add(1, 2, 1, R.string.search_result_sort_size).setCheckable(true)
            menu.add(1, 3, 2, R.string.search_result_sort_seeders).setCheckable(true)
            menu.add(1, 4, 3, R.string.search_result_sort_leechers).setCheckable(true)
            menu.add(1, 5, 4, R.string.search_result_sort_engine).setCheckable(true)
            menu.setGroupCheckable(1, true, true)
            menu.findItem(
                when (sort) {
                    Sort.NAME -> 1
                    Sort.SIZE -> 2
                    Sort.SEEDERS -> 3
                    Sort.LEECHERS -> 4
                    Sort.ENGINE -> 5
                },
            ).isChecked = true
            menu.add(0, 6, 5, R.string.search_result_sort_reverse).setCheckable(true)
            menu.findItem(6).isChecked = reverse
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> sort = Sort.NAME
                    2 -> sort = Sort.SIZE
                    3 -> sort = Sort.SEEDERS
                    4 -> sort = Sort.LEECHERS
                    5 -> sort = Sort.ENGINE
                    6 -> reverse = !reverse
                }
                applyPipeline()
                true
            }
        }.show()
    }

    private fun showFilterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_search_filter, null)
        val seedsMin = view.findViewById<TextInputEditText>(R.id.filter_seeds_min)
        val seedsMax = view.findViewById<TextInputEditText>(R.id.filter_seeds_max)
        val sizeMin = view.findViewById<TextInputEditText>(R.id.filter_size_min)
        val sizeMax = view.findViewById<TextInputEditText>(R.id.filter_size_max)
        val minUnit = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(
            R.id.filter_size_min_unit,
        )
        val maxUnit = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(
            R.id.filter_size_max_unit,
        )

        seedsMin?.setText(filterSeedsMin?.toString().orEmpty())
        seedsMax?.setText(filterSeedsMax?.toString().orEmpty())
        sizeMin?.setText(
            filterSizeMin?.toString().orEmpty(),
        )
        sizeMax?.setText(filterSizeMax?.toString().orEmpty())

        val unitLabels = sizeUnits.map { getString(it.first) }
        val unitAdapter = android.widget.ArrayAdapter(
            requireContext(), android.R.layout.simple_list_item_1, unitLabels,
        )
        minUnit?.setAdapter(unitAdapter)
        maxUnit?.setAdapter(unitAdapter)
        var minUnitIndex = 3 // GiB, matching qBC's default
        var maxUnitIndex = 3
        minUnit?.setText(unitLabels[minUnitIndex], false)
        maxUnit?.setText(unitLabels[maxUnitIndex], false)
        minUnit?.setOnItemClickListener { _, _, position, _ -> minUnitIndex = position }
        maxUnit?.setOnItemClickListener { _, _, position, _ -> maxUnitIndex = position }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_result_filter)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.search_filter_reset, null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            filterSeedsMin = seedsMin?.text?.toString()?.trim()?.toIntOrNull()
            filterSeedsMax = seedsMax?.text?.toString()?.trim()?.toIntOrNull()
            filterSizeMin = sizeMin?.text?.toString()?.trim()?.toLongOrNull()
                ?.let { v -> sizeUnits[minUnitIndex].second * v }
            filterSizeMax = sizeMax?.text?.toString()?.trim()?.toLongOrNull()
                ?.let { v -> sizeUnits[maxUnitIndex].second * v }
            dialog.dismiss()
            applyPipeline()
        }
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            filterSeedsMin = null
            filterSeedsMax = null
            filterSizeMin = null
            filterSizeMax = null
            seedsMin?.setText("")
            seedsMax?.setText("")
            sizeMin?.setText("")
            sizeMax?.setText("")
            snackbar(R.string.rss_success)
        }
    }

    private fun passesFilter(entry: SearchResultEntry): Boolean {
        filterSeedsMin?.let { if ((entry.seeders ?: -1) < it) return false }
        filterSeedsMax?.let { if ((entry.seeders ?: Int.MAX_VALUE) > it) return false }
        filterSizeMin?.let { if ((entry.fileSize ?: -1L) < it) return false }
        filterSizeMax?.let { if ((entry.fileSize ?: Long.MAX_VALUE) > it) return false }
        return true
    }

    private fun applyPipeline() {
        val query = nameQuery.trim()
        var list = allResults.filter { r ->
            (query.isEmpty() || r.fileName.contains(query, ignoreCase = true)) && passesFilter(r)
        }
        list = when (sort) {
            Sort.NAME -> list.sortedBy { it.fileName.lowercase(Locale.ROOT) }
            Sort.SIZE -> list.sortedBy { it.fileSize ?: -1L }
            Sort.SEEDERS -> list.sortedBy { it.seeders ?: -1 }
            Sort.LEECHERS -> list.sortedBy { it.leechers ?: -1 }
            Sort.ENGINE -> list.sortedBy { it.siteUrl.lowercase(Locale.ROOT) }
        }
        if (reverse) list = list.asReversed()
        adapter.submitList(list)
        binding.resultCount.isVisible = allResults.isNotEmpty()
        binding.resultCount.text = getString(
            R.string.search_result_showing_count, list.size, allResults.size,
        )
    }

    private fun syncSelectionUi() {
        selectionBackCallback.isEnabled = selected.isNotEmpty()
        binding.selectionBar.isVisible = selected.isNotEmpty()
        if (selected.isNotEmpty()) {
            binding.selectionBar.title = getString(R.string.search_selected_count, selected.size)
        }
        adapter.notifyDataSetChanged()
    }

    private fun downloadSelected() {
        val urls = allResults
            .filter { it.key() in selected }
            .mapNotNull { r -> r.fileUrl.ifBlank { null } }
        if (urls.isEmpty()) {
            snackbar(R.string.rss_action_failed)
            return
        }
        lifecycleScope.launch {
            val result = runCatching {
                ServiceLocator.repository(requireContext()).addTorrent(urls.joinToString("\n"))
            }
            snackbar(if (result.isSuccess) R.string.rss_success else R.string.rss_action_failed)
            if (result.isSuccess) {
                selected.clear()
                syncSelectionUi()
            }
        }
    }

    private fun snackbar(res: Int) {
        _binding ?: return
        Snackbar.make(binding.root, res, Snackbar.LENGTH_SHORT).show()
    }

    /** qBC DetailsDialog: name, size + site info rows, peer info cards,
     *  Download (add-torrent screen) and Open-site actions. */
    private fun showDetailsDialog(entry: SearchResultEntry) {
        val view = layoutInflater.inflate(R.layout.dialog_search_details, null)
        val details = DialogSearchDetailsBinding.bind(view)
        details.detailsName.text = entry.fileName
        details.detailsSize.text = getString(
            R.string.search_result_size,
            entry.fileSize?.takeIf { it > 0 }?.let { Format.size(it) } ?: "-",
        )
        details.detailsSite.text = getString(
            R.string.search_result_site,
            entry.siteUrl.removePrefix("https://").removePrefix("http://").ifBlank { "-" },
        )
        details.detailsSeeders.text = entry.seeders?.toString() ?: "-"
        details.detailsLeechers.text = entry.leechers?.toString() ?: "-"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_result_details)
            .setView(view)
            .setPositiveButton(R.string.search_add_torrent, null)
            .setNegativeButton(R.string.search_open_site, null)
            .setNeutralButton(android.R.string.cancel, null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.dismiss()
            if (entry.fileUrl.isNotBlank()) {
                AddTorrentActivity.start(requireContext(), entry.fileUrl)
            }
        }
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            dialog.dismiss()
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(entry.descriptionLink)))
            }
        }
    }

    // ---------------- adapter ----------------

    private fun SearchResultEntry.key(): String = fileUrl

    private inner class ResultAdapter :
        ListAdapter<SearchResultEntry, ResultAdapter.Holder>(DIFF) {

        inner class Holder(private val b: ItemSearchResultBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(entry: SearchResultEntry) {
                // qBC highlights the filter query inside the file name
                val query = nameQuery.trim()
                if (query.isEmpty()) {
                    b.fileName.text = entry.fileName
                } else {
                    b.fileName.text = SpannableStringBuilder(entry.fileName).apply {
                        val index = entry.fileName.lowercase(Locale.ROOT)
                            .indexOf(query.lowercase(Locale.ROOT))
                        if (index >= 0) {
                            setSpan(
                                BackgroundColorSpan(
                                    MaterialColors.getColor(
                                        b.root, R.attr.colorPrimary,
                                    ),
                                ),
                                index, index + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }
                    }
                }
                b.fileSize.text = entry.fileSize?.takeIf { it > 0 }?.let { Format.size(it) } ?: "-"
                b.fileSite.text = entry.siteUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .ifBlank { "-" }
                b.seeders.text = entry.seeders?.toString() ?: "-"
                b.leechers.text = entry.leechers?.toString() ?: "-"

                val picked = entry.key() in selected
                b.card.setCardBackgroundColor(
                    if (picked) {
                        MaterialColors.getColor(
                            b.card, com.google.android.material.R.attr.colorSecondaryContainer,
                        )
                    } else {
                        MaterialColors.getColor(
                            b.card, com.google.android.material.R.attr.colorSurfaceContainerLow,
                        )
                    },
                )
                b.card.setOnClickListener {
                    if (selected.isNotEmpty()) {
                        toggleSelected(entry.key())
                    } else {
                        showDetailsDialog(entry)
                    }
                }
                b.card.setOnLongClickListener {
                    toggleSelected(entry.key())
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    }

    private fun toggleSelected(key: String) {
        if (key in selected) selected.remove(key) else selected.add(key)
        syncSelectionUi()
    }

    companion object {
        private const val ARG_PATTERN = "pattern"
        private const val ARG_CATEGORY = "category"
        private const val ARG_PLUGINS = "plugins"
        private const val STATE_SEARCH_ID = "search_id"

        fun newInstance(pattern: String, category: String, plugins: String): SearchResultFragment =
            SearchResultFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATTERN, pattern)
                    putString(ARG_CATEGORY, category)
                    putString(ARG_PLUGINS, plugins)
                }
            }

        private val DIFF = object : DiffUtil.ItemCallback<SearchResultEntry>() {
            override fun areItemsTheSame(oldItem: SearchResultEntry, newItem: SearchResultEntry) =
                oldItem.fileUrl == newItem.fileUrl && oldItem.fileName == newItem.fileName

            override fun areContentsTheSame(oldItem: SearchResultEntry, newItem: SearchResultEntry) =
                oldItem == newItem
        }
    }
}
