package io.github.xixka.qbittorrent.ui.rss

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.FragmentRssArticlesBinding
import io.github.xixka.qbittorrent.databinding.ItemRssArticleBinding
import io.github.xixka.qbittorrent.model.RssArticle
import io.github.xixka.qbittorrent.ui.addtorrent.AddTorrentActivity
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Articles of one RSS node (feed OR folder — qBC opens articles for both),
 * qBC RssArticlesScreen parity: toolbar search with live filtering, mark
 * all read, refresh (reload 1s later so the engine can re-fetch), unread
 * dots, a details dialog with the NEW badge / feed / date / HTML
 * description and Download + Mark-as-read actions, and long-press multi
 * selection with a bottom bar (download selected / select all / inverse).
 */
class RssArticlesFragment : Fragment() {

    private var _binding: FragmentRssArticlesBinding? = null
    private val binding get() = _binding!!

    private val adapter = ArticleAdapter(
        onClick = { onArticleClick(it) },
        onLongClick = { toggleSelected(it.id) },
    )

    private val feedPath by lazy { arguments?.getString(ARG_PATH).orEmpty() }
    private val feedTitle by lazy { arguments?.getString(ARG_TITLE).orEmpty() }

    private var allArticles: List<RssArticle> = emptyList()
    private var searchQuery: String = ""
    private var searchMode = false
    private val selected = mutableSetOf<String>()

    /** Exits search mode on back press while the search field is open. */
    private val searchBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitSearchMode()
    }

    /** Exits selection mode on back press while articles are selected. */
    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            selected.clear()
            syncSelectionUi()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRssArticlesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appBar.title = feedTitle.ifBlank { getString(R.string.rss_all_articles) }
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.inflateMenu(R.menu.rss_articles)
        binding.appBar.setOnMenuItemClickListener { onMenuItem(it.itemId) }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, searchBackCallback,
        )
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, selectionBackCallback,
        )

        binding.articleList.layoutManager = LinearLayoutManager(requireContext())
        binding.articleList.adapter = adapter
        binding.articleList.setEmptyView(binding.emptyView)
        binding.emptyView.setText(R.string.rss_no_articles)
        binding.emptyView.setIconResource(R.drawable.ic_article_24px)

        binding.swipeRefresh.setOnRefreshListener { load() }

        binding.searchInput.addTextChangedListener { text: Editable? ->
            searchQuery = text?.toString().orEmpty()
            applyFilter()
        }

        // selection bottom bar
        binding.selectionBar.setNavigationOnClickListener {
            selected.clear()
            syncSelectionUi()
        }
        binding.selectionBar.inflateMenu(R.menu.rss_selection)
        binding.selectionBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rss_download_selected -> downloadSelected()
                R.id.action_rss_select_all -> {
                    allArticles.forEach { a -> if (a.id !in selected) selected.add(a.id) }
                    syncSelectionUi()
                }
                R.id.action_rss_select_inverse -> {
                    val next = allArticles.filter { it.id !in selected }.map { it.id }.toSet()
                    selected.clear()
                    selected.addAll(next)
                    syncSelectionUi()
                }
            }
            true
        }

        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.action_rss_search -> {
            if (searchMode) exitSearchMode() else enterSearchMode()
            true
        }
        R.id.action_rss_mark_all -> {
            markAllRead()
            true
        }
        R.id.action_rss_refresh -> {
            refreshFeed()
            true
        }
        else -> false
    }

    private fun enterSearchMode() {
        searchMode = true
        searchBackCallback.isEnabled = true
        binding.searchInput.isVisible = true
        binding.appBar.title = " "
        binding.searchInput.requestFocus()
        refreshMenuIcons()
    }

    private fun exitSearchMode() {
        searchMode = false
        searchBackCallback.isEnabled = false
        binding.searchInput.isVisible = false
        binding.searchInput.setText("")
        searchQuery = ""
        applyFilter()
        binding.appBar.title = feedTitle.ifBlank { getString(R.string.rss_all_articles) }
        refreshMenuIcons()
    }

    private fun refreshMenuIcons() {
        // qBC swaps the search icon for a close icon once a query is typed
        binding.appBar.menu.findItem(R.id.action_rss_search)?.apply {
            setIcon(
                if (searchMode && searchQuery.isNotEmpty()) R.drawable.ic_close_24px
                else R.drawable.ic_search_24px,
            )
        }
    }

    private fun load() {
        lifecycleScope.launch {
            val articles = runCatching {
                val tree = RssTreeParser.parse(ServiceLocator.repository(requireContext()).rssItems(true))
                RssTreeParser.subtreeArticles(tree, feedPath)
            }.getOrNull().orEmpty().sortedByDescending { it.date }
            allArticles = articles
            applyFilter()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun applyFilter() {
        val query = searchQuery.trim()
        val visible = if (query.isEmpty()) {
            allArticles
        } else {
            allArticles.filter { it.title.contains(query, ignoreCase = true) }
        }
        adapter.submitList(visible)
        refreshMenuIcons()
    }

    private fun onArticleClick(article: RssArticle) {
        if (selected.isNotEmpty()) {
            toggleSelected(article.id)
        } else {
            showDetailsDialog(article)
        }
    }

    private fun toggleSelected(articleId: String) {
        if (articleId in selected) selected.remove(articleId) else selected.add(articleId)
        syncSelectionUi()
    }

    private fun syncSelectionUi() {
        selectionBackCallback.isEnabled = selected.isNotEmpty()
        binding.selectionBar.isVisible = selected.isNotEmpty()
        if (selected.isNotEmpty()) {
            binding.selectionBar.title = getString(R.string.rss_selected_count, selected.size)
        }
        adapter.notifyDataSetChanged()
    }

    private fun markArticleRead(article: RssArticle, showMessage: Boolean) {
        lifecycleScope.launch {
            runCatching {
                ServiceLocator.repository(requireContext()).rssMarkAsRead(feedPath, article.id)
            }
            if (showMessage) snackbar(R.string.rss_mark_read)
            load()
        }
    }

    private fun markAllRead() {
        lifecycleScope.launch {
            runCatching {
                ServiceLocator.repository(requireContext()).rssMarkAsRead(feedPath, null)
            }
            snackbar(R.string.rss_success)
            load()
        }
    }

    /** qBC: refresh the feed, then wait a second before re-reading articles. */
    private fun refreshFeed() {
        lifecycleScope.launch {
            val result = runCatching {
                ServiceLocator.repository(requireContext()).rssRefreshItem(feedPath)
            }
            snackbar(if (result.isSuccess) R.string.rss_refreshed else R.string.rss_action_failed)
            delay(1000)
            load()
        }
    }

    /** qBC selection bar: all selected articles go to the engine in one call. */
    private fun downloadSelected() {
        val urls = allArticles
            .filter { it.id in selected }
            .mapNotNull { a -> a.torrentUrl.ifBlank { a.link }.ifBlank { null } }
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

    /** qBC DetailsDialog: title, NEW badge, feed, date, HTML description,
     *  Download (opens the add-torrent screen) + mark-as-read actions. */
    private fun showDetailsDialog(article: RssArticle) {
        val view = layoutInflater.inflate(R.layout.dialog_rss_details, null)
        val titleView = view.findViewById<android.widget.TextView>(R.id.details_title)
        val badge = view.findViewById<android.widget.TextView>(R.id.details_new_badge)
        val feedView = view.findViewById<android.widget.TextView>(R.id.details_feed)
        val dateView = view.findViewById<android.widget.TextView>(R.id.details_date)
        val descView = view.findViewById<android.widget.TextView>(R.id.details_description)

        titleView.text = article.title
        if (!article.isRead) {
            badge.backgroundTintList = ColorStateList.valueOf(
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimaryContainer),
            )
            badge.setTextColor(
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnPrimaryContainer),
            )
            badge.isVisible = true
        }
        feedView.text = feedTitle.ifBlank { feedPath }
        dateView.text = if (article.date > 0) {
            DateUtils.formatDateTime(
                requireContext(), article.date * 1000,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
            )
        } else ""
        article.description?.let { html ->
            descView.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            descView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rss_details)
            .setView(view)
            .setPositiveButton(R.string.rss_add_torrent, null)
            .setNegativeButton(R.string.rss_mark_read, null)
            .setNeutralButton(android.R.string.cancel, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = article.torrentUrl.ifBlank { article.link }
            if (url.isNotBlank()) {
                AddTorrentActivity.start(requireContext(), url)
                dialog.dismiss()
                if (!article.isRead) markArticleRead(article, showMessage = false)
            } else {
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            dialog.dismiss()
            if (!article.isRead) markArticleRead(article, showMessage = true)
        }
    }

    // ---------------- adapter ----------------

    private inner class ArticleAdapter(
        private val onClick: (RssArticle) -> Unit,
        private val onLongClick: (RssArticle) -> Unit,
    ) : ListAdapter<RssArticle, ArticleAdapter.Holder>(DIFF) {

        inner class Holder(private val b: ItemRssArticleBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(article: RssArticle) {
                b.title.text = article.title
                b.date.text = if (article.date > 0) {
                    DateUtils.formatDateTime(
                        b.root.context, article.date * 1000,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                    )
                } else ""
                b.readDot.visibility = if (article.isRead) View.GONE else View.VISIBLE
                b.readDot.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(b.root, R.attr.colorPrimary),
                )
                if (article.id in selected) {
                    b.card.setCardBackgroundColor(
                        MaterialColors.getColor(
                            b.card, com.google.android.material.R.attr.colorSecondaryContainer,
                        ),
                    )
                } else {
                    b.card.setCardBackgroundColor(
                        MaterialColors.getColor(
                            b.card, com.google.android.material.R.attr.colorSurfaceContainerLow,
                        ),
                    )
                }
                b.card.setOnClickListener { onClick(article) }
                b.card.setOnLongClickListener {
                    onLongClick(article)
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemRssArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    }

    companion object {
        private const val ARG_PATH = "path"
        private const val ARG_TITLE = "title"

        fun newInstance(path: String, title: String): RssArticlesFragment =
            RssArticlesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, path)
                    putString(ARG_TITLE, title)
                }
            }

        private val DIFF = object : DiffUtil.ItemCallback<RssArticle>() {
            override fun areItemsTheSame(oldItem: RssArticle, newItem: RssArticle) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: RssArticle, newItem: RssArticle) =
                oldItem == newItem
        }
    }
}
