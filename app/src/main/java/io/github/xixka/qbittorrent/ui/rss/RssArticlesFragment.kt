package io.github.xixka.qbittorrent.ui.rss

import android.content.Intent
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
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityLogBinding
import io.github.xixka.qbittorrent.databinding.ItemRssArticleBinding
import io.github.xixka.qbittorrent.model.RssArticle
import io.github.xixka.qbittorrent.ui.addtorrent.AddTorrentActivity
import io.github.xixka.qbittorrent.ui.main.MainActivity
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Articles of one RSS feed (qBitController RssArticlesScreen parity,
 * LibreTorrent list style): unread markers, mark-read on open, add torrent
 * from the article's torrentUrl, mark all read, manual refresh. Pushed as an
 * IN-PLACE sub-page of the RSS destination — no separate window.
 */
class RssArticlesFragment : Fragment() {

    private var _binding: ActivityLogBinding? = null
    private val binding get() = _binding!!

    private val adapter = ArticleAdapter(
        onClick = { openArticle(it) },
        onLongClick = { showArticleMenu(it) },
    )

    private val feedPath by lazy { arguments?.getString(ARG_PATH).orEmpty() }
    private val feedTitle by lazy { arguments?.getString(ARG_TITLE).orEmpty() }

    private val timeFormat: DateFormat by lazy {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }

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
        // reuse the log screen skeleton: toolbar + list + empty placeholder
        binding.typeChipGroup.visibility = View.GONE
        applyWindowInsets(
            child = binding.logList,
            sideMask = WindowInsetsSide.LEFT or WindowInsetsSide.RIGHT,
        )
        binding.appBar.title = feedTitle
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.inflateMenu(R.menu.rss_articles)
        binding.appBar.setOnMenuItemClickListener { item -> onMenuItem(item.itemId) }

        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = adapter
        binding.logList.setEmptyView(binding.emptyView)
        binding.emptyView.setText(R.string.rss_no_articles)
        binding.emptyView.setIconResource(R.drawable.ic_article_24px)

        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.refresh_log_menu -> { // shared refresh action of the skeleton menu
            load()
            true
        }
        R.id.mark_all_read_menu -> {
            markAllRead()
            true
        }
        else -> false
    }

    private fun load() {
        lifecycleScope.launch {
            val articles = runCatching {
                val tree = RssTreeParser.parse(ServiceLocator.repository(requireContext()).rssItems(true))
                RssTreeParser.flatten(tree).firstOrNull { it.apiPath == feedPath }?.articles
            }.getOrNull().orEmpty().sortedByDescending { it.date }
            adapter.submitList(articles)
        }
    }

    private fun markAllRead() {
        lifecycleScope.launch {
            runCatching {
                ServiceLocator.repository(requireContext()).rssMarkAsRead(feedPath, null)
            }
            load()
        }
    }

    private fun openArticle(article: RssArticle) {
        lifecycleScope.launch {
            runCatching {
                ServiceLocator.repository(requireContext()).rssMarkAsRead(feedPath, article.id)
            }
        }
        val url = article.torrentUrl.ifBlank { article.link }
        if (url.isNotBlank()) {
            AddTorrentActivity.start(requireContext(), url)
        } else {
            showArticleMenu(article)
        }
    }

    private fun showArticleMenu(article: RssArticle) {
        val options = listOf(
            getString(R.string.rss_add_torrent),
            getString(R.string.rss_mark_read),
            getString(R.string.rss_open_link),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(article.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        val url = article.torrentUrl.ifBlank { article.link }
                        if (url.isNotBlank()) AddTorrentActivity.start(requireContext(), url)
                    }
                    1 -> lifecycleScope.launch {
                        runCatching {
                            ServiceLocator.repository(requireContext())
                                .rssMarkAsRead(feedPath, article.id)
                        }
                        load()
                    }
                    2 -> {
                        val url = article.link.ifBlank { article.torrentUrl }
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
                    timeFormat.format(Date(article.date * 1000))
                } else ""
                b.readDot.visibility = if (article.isRead) View.GONE else View.VISIBLE
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
