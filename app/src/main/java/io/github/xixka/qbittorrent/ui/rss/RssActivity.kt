package io.github.xixka.qbittorrent.ui.rss

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityRssBinding
import io.github.xixka.qbittorrent.databinding.ItemRssNodeBinding
import io.github.xixka.qbittorrent.model.RssFeedNode
import io.github.xixka.qbittorrent.model.RssRule
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch

/**
 * RSS hub (qBitController RssFeedsScreen + RssRulesScreen parity, rendered
 * with LibreTorrent's tabbed list UI):
 *  - Feeds tab: the server's subscription tree — add feed / folder,
 *    rename, move, delete, set feed URL, refresh, read articles
 *  - Rules tab: auto-download rules with the full qBC editor dialog
 */
class RssActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRssBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivityRssBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(child = binding.viewPager, sideMask = WindowInsetsSide.BOTTOM)

        binding.appBar.setNavigationOnClickListener { finish() }
        binding.viewPager.adapter = RssPagerAdapter(this)
        com.google.android.material.tabs.TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.setText(if (pos == 0) R.string.rss_tab_feeds else R.string.rss_tab_rules)
        }.attach()

        binding.rssFab.setOnClickListener { onFab() }
    }

    private fun onFab() {
        if (binding.viewPager.currentItem == 0) {
            // qBC keeps separate screens; LibreTorrent-style: a small chooser
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rss_add)
                .setItems(
                    arrayOf(
                        getString(R.string.rss_add_feed),
                        getString(R.string.rss_add_folder),
                    )
                ) { _, which ->
                    if (which == 0) showAddFeedDialog() else showAddFolderDialog()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            showEditRuleDialog(null, null)
        }
    }

    private fun repository() = ServiceLocator.repository(this)

    // ---------------- feeds ----------------

    private fun showAddFeedDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_rss_feed, null)
        val url = view.findViewById<TextInputEditText>(R.id.rss_feed_url)
        val name = view.findViewById<TextInputEditText>(R.id.rss_feed_name)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rss_add_feed)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val feedUrl = url?.text?.toString()?.trim().orEmpty()
                val feedName = name?.text?.toString()?.trim().orEmpty()
                if (feedUrl.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = runCatching {
                            repository().rssAddFeed(feedUrl, feedName)
                        }
                        Toast.makeText(
                            this@RssActivity,
                            if (result.isSuccess) R.string.rss_added else R.string.rss_action_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                        refreshFeedsTab()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddFolderDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rss_add_folder)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        runCatching { repository().rssAddFolder(name) }
                        refreshFeedsTab()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showNodeMenu(node: RssFeedNode) {
        val options = mutableListOf(
            getString(R.string.rss_open),
            getString(R.string.rss_refresh),
        )
        if (node.isFeed) options += getString(R.string.rss_edit_url)
        options += listOf(
            getString(R.string.rss_rename),
            getString(R.string.rss_move),
            getString(R.string.rss_delete),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(node.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    getString(R.string.rss_open) -> openFeed(node)
                    getString(R.string.rss_refresh) -> refreshNode(node)
                    getString(R.string.rss_edit_url) -> showSetFeedUrlDialog(node)
                    getString(R.string.rss_rename) -> showRenameNodeDialog(node)
                    getString(R.string.rss_move) -> showMoveNodeDialog(node)
                    getString(R.string.rss_delete) -> confirmDeleteNode(node)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openFeed(node: RssFeedNode) {
        startActivity(
            Intent(this, RssArticlesActivity::class.java)
                .putExtra(RssArticlesActivity.EXTRA_PATH, node.apiPath)
                .putExtra(RssArticlesActivity.EXTRA_TITLE, node.name)
                .putExtra(RssArticlesActivity.EXTRA_WITH_DATA, node.articles.isNotEmpty()),
        )
    }

    private fun refreshNode(node: RssFeedNode) {
        lifecycleScope.launch {
            val result = runCatching { repository().rssRefreshItem(node.apiPath) }
            Toast.makeText(
                this@RssActivity,
                if (result.isSuccess) R.string.rss_refreshed else R.string.rss_action_failed,
                Toast.LENGTH_SHORT,
            ).show()
            refreshFeedsTab()
        }
    }

    private fun showSetFeedUrlDialog(node: RssFeedNode) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input?.setText(node.url.orEmpty())
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rss_edit_url)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input?.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty()) {
                    lifecycleScope.launch {
                        runCatching { repository().rssSetFeedUrl(node.apiPath, url) }
                        refreshFeedsTab()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameNodeDialog(node: RssFeedNode) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(node.name)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rss_rename)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input?.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty() && newName != node.name) {
                    lifecycleScope.launch {
                        runCatching {
                            repository().rssMoveItem(node.apiPath, (node.path + newName).joinToString("\\"))
                        }
                        refreshFeedsTab()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMoveNodeDialog(node: RssFeedNode) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.hint = getString(R.string.rss_move_hint)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rss_move)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val dest = input?.text?.toString()?.trim().orEmpty()
                if (dest.isNotEmpty()) {
                    lifecycleScope.launch {
                        runCatching {
                            repository().rssMoveItem(node.apiPath, dest)
                        }
                        refreshFeedsTab()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteNode(node: RssFeedNode) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rss_delete)
            .setMessage(getString(R.string.rss_delete_confirm, node.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching { repository().rssRemoveItem(node.apiPath) }
                    refreshFeedsTab()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshFeedsTab() {
        val fragment = supportFragmentManager.fragments.firstOrNull()
            ?.childFragmentManager?.fragments?.firstOrNull { it is RssFeedsFragment } as? RssFeedsFragment
        fragment?.load()
    }

    // ---------------- rules ----------------

    /** Full qBC rule editor: new rule when [name] is null, edit otherwise. */
    fun showEditRuleDialog(name: String?, rule: RssRule?) {
        val view = layoutInflater.inflate(R.layout.dialog_rss_rule, null)
        val enabled = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.rule_enabled)
        val mustContain = view.findViewById<TextInputEditText>(R.id.rule_must_contain)
        val mustNotContain = view.findViewById<TextInputEditText>(R.id.rule_must_not_contain)
        val useRegex = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.rule_use_regex)
        val episodeFilter = view.findViewById<TextInputEditText>(R.id.rule_episode_filter)
        val smartFilter = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.rule_smart_filter)
        val ignoreDays = view.findViewById<TextInputEditText>(R.id.rule_ignore_days)
        val addPaused = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.rule_add_paused)
        val category = view.findViewById<TextInputEditText>(R.id.rule_category)
        val savePath = view.findViewById<TextInputEditText>(R.id.rule_save_path)
        val affectedFeeds = view.findViewById<TextInputEditText>(R.id.rule_affected_feeds)

        enabled?.isChecked = rule?.enabled ?: true
        mustContain?.setText(rule?.mustContain.orEmpty())
        mustNotContain?.setText(rule?.mustNotContain.orEmpty())
        useRegex?.isChecked = rule?.useRegex ?: false
        episodeFilter?.setText(rule?.episodeFilter.orEmpty())
        smartFilter?.isChecked = rule?.smartFilter ?: false
        ignoreDays?.setText((rule?.ignoreDays ?: 0).toString())
        addPaused?.isChecked = rule?.addPaused ?: false
        category?.setText(rule?.assignedCategory.orEmpty())
        savePath?.setText(rule?.savePath.orEmpty())
        affectedFeeds?.setText(rule?.affectedFeeds.joinToString("\n"))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (name == null) getString(R.string.rss_add_rule) else name)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val ruleName = name ?: promptRuleNameValue ?: ""
                if (ruleName.isBlank()) return@setPositiveButton
                val newRule = RssRule(
                    enabled = enabled?.isChecked ?: true,
                    mustContain = mustContain?.text?.toString()?.trim().orEmpty(),
                    mustNotContain = mustNotContain?.text?.toString()?.trim().orEmpty(),
                    useRegex = useRegex?.isChecked == true,
                    episodeFilter = episodeFilter?.text?.toString()?.trim().orEmpty(),
                    ignoreDays = ignoreDays?.text?.toString()?.trim()?.toIntOrNull() ?: 0,
                    addPaused = addPaused?.isChecked,
                    assignedCategory = category?.text?.toString()?.trim().orEmpty(),
                    savePath = savePath?.text?.toString()?.trim().orEmpty(),
                    contentLayout = null,
                    smartFilter = smartFilter?.isChecked == true,
                    affectedFeeds = affectedFeeds?.text?.toString()
                        ?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: rule?.affectedFeeds ?: emptyList(),
                )
                lifecycleScope.launch {
                    val result = runCatching { repository().rssSetRule(ruleName, newRule) }
                    Toast.makeText(
                        this@RssActivity,
                        if (result.isSuccess) R.string.rss_rule_saved else R.string.rss_action_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshRulesTab()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (name == null) {
            // new rule: ask for the name first, keep it in a holder field
            val nameView = layoutInflater.inflate(R.layout.dialog_input, null)
            val nameInput = nameView.findViewById<TextInputEditText>(R.id.input)
            nameInput?.hint = getString(R.string.rss_rule_name)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rss_add_rule)
                .setView(nameView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    promptRuleNameValue = nameInput?.text?.toString()?.trim().orEmpty()
                    if (promptRuleNameValue.isNullOrBlank()) {
                        Toast.makeText(this, R.string.rss_rule_name_empty, Toast.LENGTH_SHORT).show()
                    } else {
                        dialog.setTitle(promptRuleNameValue).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            dialog.show()
        }
    }

    /** Name captured by the new-rule name dialog. */
    private var promptRuleNameValue: String? = null

    private fun refreshRulesTab() {
        val fragment = supportFragmentManager.fragments.firstOrNull()
            ?.childFragmentManager?.fragments?.firstOrNull { it is RssRulesFragment } as? RssRulesFragment
        fragment?.load()
    }

    fun showRuleMenu(name: String, rule: RssRule) {
        val options = listOf(
            getString(R.string.rss_edit_rule),
            getString(R.string.rss_rename_rule),
            getString(R.string.rss_delete_rule),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showEditRuleDialog(name, rule)
                    1 -> showRenameRuleDialog(name)
                    2 -> confirmDeleteRule(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameRuleDialog(name: String) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(name)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rss_rename_rule)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input?.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty() && newName != name) {
                    lifecycleScope.launch {
                        runCatching { repository().rssRenameRule(name, newName) }
                        refreshRulesTab()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteRule(name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rss_delete_rule)
            .setMessage(getString(R.string.rss_delete_rule_confirm, name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching { repository().rssRemoveRule(name) }
                    refreshRulesTab()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- pager ----------------

    private class RssPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun createFragment(position: Int): Fragment =
            if (position == 0) RssFeedsFragment() else RssRulesFragment()

        override fun getItemCount() = 2
    }
}

/**
 * Feeds tab: the flattened tree with indentation, click = open the folder
 * (toggle) or the feed's articles; long-press = node management menu.
 */
class RssFeedsFragment : Fragment() {

    private var adapter: NodeAdapter? = null
    private var expanded = HashSet<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val list = io.github.xixka.qbittorrent.ui.customviews.EmptyRecyclerView(
            requireContext(), null,
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        val empty = io.github.xixka.qbittorrent.ui.customviews.EmptyListPlaceholder(
            requireContext(), null,
        )
        empty.setIconResource(io.github.xixka.qbittorrent.R.drawable.ic_rss_feed_24px)
        empty.setText(io.github.xixka.qbittorrent.R.string.rss_empty)
        val root = android.widget.FrameLayout(requireContext())
        root.addView(
            list,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            empty,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        adapter = NodeAdapter(
            onOpen = { node ->
                if (node.isFeed) {
                    (activity as? RssActivity)?.showNodeMenu(node) ?: run {
                        // direct open when not hosted by RssActivity
                    }
                } else {
                    if (!expanded.add(node.apiPath)) expanded.remove(node.apiPath)
                    load()
                }
            },
            onLongClick = { node -> (activity as? RssActivity)?.showNodeMenu(node) },
        )
        list.adapter = adapter
        list.setEmptyView(empty)
        return root
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    fun load() {
        val act = activity ?: return
        lifecycleScope.launch {
            val tree = runCatching {
                RssTreeParser.parse(ServiceLocator.repository(act).rssItems(true))
            }.getOrDefault(emptyList())
            val visible = mutableListOf<RssFeedNode>()
            fun addLevel(nodes: List<RssFeedNode>, depth: Int) {
                for (n in nodes) {
                    val copy = n.copy(path = n.path) // depth kept via indent map
                    visible.add(copy)
                    if (!n.isFeed && n.apiPath in expanded) addLevel(n.children, depth + 1)
                }
            }
            addLevel(tree, 0)
            adapter?.submit(visible, tree)
        }
    }

    private inner class NodeAdapter(
        private val onOpen: (RssFeedNode) -> Unit,
        private val onLongClick: (RssFeedNode) -> Unit,
    ) : ListAdapter<RssFeedNode, NodeAdapter.Holder>(DIFF) {

        private var depths = HashMap<String, Int>()

        fun submit(visible: List<RssFeedNode>, tree: List<RssFeedNode>) {
            depths.clear()
            fun mark(nodes: List<RssFeedNode>, depth: Int) {
                for (n in nodes) {
                    depths[n.apiPath] = depth
                    if (!n.isFeed) mark(n.children, depth + 1)
                }
            }
            mark(tree, 0)
            submitList(visible)
        }

        inner class Holder(private val b: ItemRssNodeBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(node: RssFeedNode) {
                b.name.text = node.name
                b.url.text = node.url ?: if (node.isFeed) "" else getString(R.string.rss_folder)
                b.url.visibility = if (b.url.text.isBlank()) View.GONE else View.VISIBLE
                b.icon.setImageResource(
                    if (node.isFeed) io.github.xixka.qbittorrent.R.drawable.ic_rss_feed_24px
                    else io.github.xixka.qbittorrent.R.drawable.ic_folder_24px,
                )
                b.unreadBadge.text = node.articles.count { !it.isRead }.toString()
                b.unreadBadge.visibility =
                    if (node.isFeed && node.articles.any { !it.isRead }) View.VISIBLE else View.GONE
                val depth = depths[node.apiPath] ?: 0
                b.root.setPadding(depth * 24 * resources.displayMetrics.density.toInt(), 0, 0, 0)
                b.card.setOnClickListener { onOpen(node) }
                b.card.setOnLongClickListener {
                    onLongClick(node)
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemRssNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RssFeedNode>() {
            override fun areItemsTheSame(oldItem: RssFeedNode, newItem: RssFeedNode) =
                oldItem.apiPath == newItem.apiPath

            override fun areContentsTheSame(oldItem: RssFeedNode, newItem: RssFeedNode) =
                oldItem == newItem
        }
    }
}

/** Rules tab: the auto-download rule list. */
class RssRulesFragment : Fragment() {

    private var adapter: RulesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val list = io.github.xixka.qbittorrent.ui.customviews.EmptyRecyclerView(
            requireContext(), null,
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        val empty = io.github.xixka.qbittorrent.ui.customviews.EmptyListPlaceholder(
            requireContext(), null,
        )
        empty.setIconResource(io.github.xixka.qbittorrent.R.drawable.ic_rule_24px)
        empty.setText(io.github.xixka.qbittorrent.R.string.rss_rules_empty)
        val root = android.widget.FrameLayout(requireContext())
        root.addView(
            list,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            empty,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        adapter = RulesAdapter(
            onClick = { (name, rule) -> (activity as? RssActivity)?.showRuleMenu(name, rule) },
        )
        list.adapter = adapter
        list.setEmptyView(empty)
        return root
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    fun load() {
        val act = activity as? RssActivity ?: return
        lifecycleScope.launch {
            val rules = runCatching {
                ServiceLocator.repository(act).rssRules()
            }.getOrDefault(emptyMap())
            adapter?.submitList(rules.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value })
        }
    }

    private class RulesAdapter(
        private val onClick: (Pair<String, RssRule>) -> Unit,
    ) : ListAdapter<Pair<String, RssRule>, RulesAdapter.Holder>(DIFF) {

        inner class Holder(private val b: ItemRssNodeBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: Pair<String, RssRule>) {
                b.name.text = item.first
                val rule = item.second
                b.url.text = rule.mustContain.ifBlank {
                    b.root.context.getString(io.github.xixka.qbittorrent.R.string.rss_rule_summary_disabled)
                }
                b.url.visibility = View.VISIBLE
                b.icon.setImageResource(io.github.xixka.qbittorrent.R.drawable.ic_rule_24px)
                b.unreadBadge.text = ""
                b.unreadBadge.visibility = View.GONE
                b.card.setOnClickListener { onClick(item) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemRssNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Pair<String, RssRule>>() {
            override fun areItemsTheSame(
                oldItem: Pair<String, RssRule>,
                newItem: Pair<String, RssRule>,
            ) = oldItem.first == newItem.first

            override fun areContentsTheSame(
                oldItem: Pair<String, RssRule>,
                newItem: Pair<String, RssRule>,
            ) = oldItem == newItem
        }
    }
}
