package io.github.xixka.qbittorrent.ui.rss

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityRssBinding
import io.github.xixka.qbittorrent.databinding.ItemRssNodeBinding
import io.github.xixka.qbittorrent.model.RssFeedNode
import io.github.xixka.qbittorrent.model.RssRule
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * RSS hub — qBitController RssFeedsScreen parity, rendered with this app's
 * tabbed shell (feeds + rules):
 *  - Feeds tab: the subscription tree as elevated cards with expand arrows
 *    and per-node overflow menus (rename / edit URL / move / delete /
 *    add-into-folder), qBC's move mode (tap a folder in the tree to move
 *    the picked item there), refresh-all, and pull-to-refresh.
 *  - Rules tab: the auto-download rules with the qBC editor dialog.
 * Everything reports through snackbars; the toolbar carries the qBC action
 * set per tab (add / refresh all / rules, add-rule), no FAB.
 */
class RssFragment : Fragment() {

    private var _binding: ActivityRssBinding? = null
    private val binding get() = _binding!!

    /** The item being moved (qBC movingItemId); null = move mode off. */
    private var movingNode: RssFeedNode? = null

    private val moveBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitMoveMode()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ActivityRssBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // the toolbar arrow returns to the torrent list (bottom-nav back)
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.goHome() }
        binding.appBar.inflateMenu(R.menu.rss_feeds)
        binding.appBar.setOnMenuItemClickListener { onMenuItem(it.itemId) }

        binding.moveBar.setNavigationOnClickListener { exitMoveMode() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, moveBackCallback,
        )

        binding.viewPager.adapter = RssPagerAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = switchMenus(position)
        })
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.setText(if (pos == 0) R.string.rss_tab_feeds else R.string.rss_tab_rules)
        }.attach()
        switchMenus(0)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun switchMenus(tab: Int) {
        binding.appBar.menu.findItem(R.id.action_rss_add)?.isVisible = tab == 0
        binding.appBar.menu.findItem(R.id.action_rss_refresh_all)?.isVisible = tab == 0
        binding.appBar.menu.findItem(R.id.action_rss_rules)?.isVisible = tab == 0
        binding.appBar.menu.findItem(R.id.action_rss_add_rule)?.isVisible = tab == 1
    }

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.action_rss_add -> {
            showAddMenu(binding.appBar.findViewById(R.id.action_rss_add))
            true
        }
        R.id.action_rss_refresh_all -> {
            refreshAll()
            true
        }
        R.id.action_rss_rules -> {
            binding.viewPager.currentItem = 1
            true
        }
        R.id.action_rss_add_rule -> {
            showEditRuleDialog(null, null)
            true
        }
        else -> false
    }

    /** qBC's add dropdown: header-less anchor menu with the two choices. */
    private fun showAddMenu(anchor: View?) {
        anchor ?: return
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.rss_add_feed)
            menu.add(0, 2, 1, R.string.rss_add_folder)
            setOnMenuItemClickListener { item ->
                if (item.itemId == 1) showAddFeedDialog(null) else showAddFolderDialog(null)
                true
            }
        }.show()
    }

    private fun snackbar(res: Int) {
        _binding ?: return
        Snackbar.make(binding.root, res, Snackbar.LENGTH_SHORT).show()
    }

    private fun repository() = ServiceLocator.repository(requireContext())

    /** qBC refreshAllFeeds: an empty itemPath refreshes every feed. */
    private fun refreshAll() {
        lifecycleScope.launch {
            val result = runCatching { repository().rssRefreshItem("") }
            snackbar(if (result.isSuccess) R.string.rss_refresh_all_done else R.string.rss_action_failed)
            refreshFeedsTab()
        }
    }

    private fun refreshFeedsTab() {
        val fragment = childFragmentManager.fragments.firstOrNull { it is RssFeedsFragment } as? RssFeedsFragment
        fragment?.load()
    }

    private fun refreshRulesTab() {
        val fragment = childFragmentManager.fragments.firstOrNull { it is RssRulesFragment } as? RssRulesFragment
        fragment?.load()
    }

    // ---------------- move mode (qBC) ----------------

    fun startMoveMode(node: RssFeedNode) {
        movingNode = node
        moveBackCallback.isEnabled = true
        binding.moveBar.visibility = View.VISIBLE
        refreshFeedsTab()
    }

    private fun exitMoveMode() {
        if (movingNode == null) return
        movingNode = null
        moveBackCallback.isEnabled = false
        binding.moveBar.visibility = View.GONE
        refreshFeedsTab()
    }

    fun inMoveMode() = movingNode != null

    fun isMoving(node: RssFeedNode): Boolean {
        val moving = movingNode ?: return false
        return moving.apiPath == node.apiPath && moving.name == node.name
    }

    /** qBC: tapping a folder while moving drops the picked item into it. */
    fun completeMove(target: RssFeedNode) {
        val item = movingNode ?: return
        val from = item.apiPath
        val to = (target.path + item.name).joinToString("\\")
        movingNode = null
        moveBackCallback.isEnabled = false
        binding.moveBar.visibility = View.GONE
        lifecycleScope.launch {
            val result = runCatching { repository().rssMoveItem(from, to) }
            snackbar(if (result.isSuccess) R.string.rss_success else R.string.rss_action_failed)
            refreshFeedsTab()
        }
    }

    // ---------------- feed / folder dialogs ----------------

    private fun showAddFeedDialog(parentPath: List<String>?) {
        val view = layoutInflater.inflate(R.layout.dialog_rss_feed, null)
        val url = view.findViewById<TextInputEditText>(R.id.rss_feed_url)
        val name = view.findViewById<TextInputEditText>(R.id.rss_feed_name)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rss_add_feed)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val feedUrl = url?.text?.toString()?.trim().orEmpty()
            if (feedUrl.isEmpty()) {
                url?.error = getString(R.string.rss_required)
                return@setOnClickListener
            }
            val feedName = name?.text?.toString()?.trim().orEmpty()
            // qBC: the item path is parent + (name or url), "\"-joined
            val itemPath = (parentPath.orEmpty() + feedName.ifBlank { feedUrl }).joinToString("\\")
            dialog.dismiss()
            lifecycleScope.launch {
                val result = runCatching { repository().rssAddFeed(feedUrl, itemPath) }
                snackbar(if (result.isSuccess) R.string.rss_added else R.string.rss_action_failed)
                refreshFeedsTab()
            }
        }
    }

    private fun showAddFolderDialog(parentPath: List<String>?) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rss_add_folder)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input?.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                input?.error = getString(R.string.rss_required)
                return@setOnClickListener
            }
            val itemPath = (parentPath.orEmpty() + name).joinToString("\\")
            dialog.dismiss()
            lifecycleScope.launch {
                runCatching { repository().rssAddFolder(itemPath) }
                snackbar(R.string.rss_success)
                refreshFeedsTab()
            }
        }
    }

    fun showNodeMenu(node: RssFeedNode, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            // qBC gates rename / move / delete to nested items (level > 0);
            // root-level folders only offer "add into".
            if (node.path.isNotEmpty()) {
                menu.add(0, 1, 0, if (node.isFeed) R.string.rss_rename_feed else R.string.rss_rename_folder)
                if (node.isFeed) menu.add(0, 2, 1, R.string.rss_edit_url)
                menu.add(0, 3, 2, if (node.isFeed) R.string.rss_move_feed else R.string.rss_move_folder)
                menu.add(0, 4, 3, if (node.isFeed) R.string.rss_delete_feed else R.string.rss_delete_folder)
                if (!node.isFeed) menu.add(0, 5, 4, R.string.rss_add_feed)
                if (!node.isFeed) menu.add(0, 6, 5, R.string.rss_add_folder)
            } else if (!node.isFeed) {
                menu.add(0, 5, 0, R.string.rss_add_feed)
                menu.add(0, 6, 1, R.string.rss_add_folder)
            } else {
                menu.add(0, 2, 0, R.string.rss_edit_url)
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showRenameNodeDialog(node)
                    2 -> showEditUrlDialog(node)
                    3 -> startMoveMode(node)
                    4 -> confirmDeleteNode(node)
                    5 -> showAddFeedDialog(node.path + node.name)
                    6 -> showAddFolderDialog(node.path + node.name)
                }
                true
            }
        }.show()
    }

    private fun showEditUrlDialog(node: RssFeedNode) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input?.setText(node.url.orEmpty())
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rss_edit_url)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newUrl = input?.text?.toString()?.trim().orEmpty()
            if (newUrl.isEmpty()) {
                input?.error = getString(R.string.rss_required)
                return@setOnClickListener
            }
            dialog.dismiss()
            lifecycleScope.launch {
                val result = runCatching { repository().rssSetFeedUrl(node.apiPath, newUrl) }
                snackbar(if (result.isSuccess) R.string.rss_success else R.string.rss_action_failed)
                refreshFeedsTab()
            }
        }
    }

    private fun showRenameNodeDialog(node: RssFeedNode) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setText(node.name)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (node.isFeed) R.string.rss_rename_feed else R.string.rss_rename_folder)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = input?.text?.toString()?.trim().orEmpty()
            if (newName.isEmpty()) {
                input?.error = getString(R.string.rss_required)
                return@setOnClickListener
            }
            if (newName != node.name) {
                dialog.dismiss()
                lifecycleScope.launch {
                    val from = node.apiPath
                    val to = (node.path + newName).joinToString("\\")
                    val result = runCatching { repository().rssMoveItem(from, to) }
                    snackbar(if (result.isSuccess) R.string.rss_success else R.string.rss_action_failed)
                    refreshFeedsTab()
                }
            } else {
                dialog.dismiss()
            }
        }
    }

    private fun confirmDeleteNode(node: RssFeedNode) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (node.isFeed) R.string.rss_delete_feed else R.string.rss_delete_folder)
            .setMessage(getString(R.string.rss_delete_confirm, node.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val result = runCatching { repository().rssRemoveItem(node.apiPath) }
                    snackbar(if (result.isSuccess) R.string.rss_success else R.string.rss_action_failed)
                    refreshFeedsTab()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        affectedFeeds?.setText(rule?.affectedFeeds.orEmpty().joinToString("\n"))

        val dialog = MaterialAlertDialogBuilder(requireContext())
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
                    snackbar(if (result.isSuccess) R.string.rss_rule_saved else R.string.rss_action_failed)
                    refreshRulesTab()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (name == null) {
            // new rule: ask for the name first, keep it in a holder field
            val nameView = layoutInflater.inflate(R.layout.dialog_input, null)
            val nameInput = nameView.findViewById<TextInputEditText>(R.id.input)
            nameInput?.hint = getString(R.string.rss_rule_name)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.rss_add_rule)
                .setView(nameView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    promptRuleNameValue = nameInput?.text?.toString()?.trim().orEmpty()
                    if (promptRuleNameValue.isNullOrBlank()) {
                        snackbar(R.string.rss_rule_name_empty)
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

    fun showRuleMenu(name: String, rule: RssRule) {
        val options = listOf(
            getString(R.string.rss_edit_rule),
            getString(R.string.rss_rename_rule),
            getString(R.string.rss_delete_rule),
        )
        MaterialAlertDialogBuilder(requireContext())
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
        MaterialAlertDialogBuilder(requireContext())
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
        MaterialAlertDialogBuilder(requireContext())
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

    private class RssPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment =
            if (position == 0) RssFeedsFragment() else RssRulesFragment()

        override fun getItemCount() = 2
    }
}

/**
 * Feeds tab, qBC FeedItem parity: elevated cards indented by depth
 * (level * 12dp), expand arrows (48dp placeholder for childless folders),
 * per-node overflow menu, move-mode highlight, click = open articles of
 * the node (feeds AND folders — qBC navigates for both).
 */
class RssFeedsFragment : Fragment() {

    private var adapter: NodeAdapter? = null
    private var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout? = null
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
        list.clipToPadding = false
        val empty = io.github.xixka.qbittorrent.ui.customviews.EmptyListPlaceholder(
            requireContext(), null,
        )
        empty.setIconResource(io.github.xixka.qbittorrent.R.drawable.ic_rss_feed_24px)
        empty.setText(io.github.xixka.qbittorrent.R.string.rss_empty)
        val content = android.widget.FrameLayout(requireContext())
        content.addView(
            list,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        content.addView(
            empty,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val swipe = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(requireContext())
        swipe.addView(
            content,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        swipe.setOnRefreshListener { load() }
        swipeRefresh = swipe
        adapter = NodeAdapter()
        list.adapter = adapter
        list.setEmptyView(empty)
        return swipe
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
            val depths = HashMap<String, Int>()
            fun addLevel(nodes: List<RssFeedNode>, depth: Int) {
                for (n in nodes) {
                    visible.add(n)
                    depths[n.apiPath] = depth
                    if (!n.isFeed && n.apiPath in expanded) addLevel(n.children, depth + 1)
                }
            }
            addLevel(tree, 0)
            adapter?.submit(visible, depths)
            swipeRefresh?.isRefreshing = false
        }
    }

    private fun openArticles(node: RssFeedNode) {
        (activity as? MainActivity)?.pushPage(
            RssArticlesFragment.newInstance(node.apiPath, node.name),
        )
    }

    private inner class NodeAdapter :
        ListAdapter<RssFeedNode, NodeAdapter.Holder>(DIFF) {

        private var depths = HashMap<String, Int>()

        fun submit(visible: List<RssFeedNode>, depths: HashMap<String, Int>) {
            this.depths = depths
            submitList(visible)
        }

        inner class Holder(private val b: ItemRssNodeBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(node: RssFeedNode) {
                val host = parentFragment as? RssFragment
                val depth = depths[node.apiPath] ?: 0
                val density = resources.displayMetrics.density

                // qBC: tree depth = card start padding (level * 12dp), and the
                // moving item is highlighted with the secondary container color
                b.card.setPadding((depth * 12 * density).toInt(), 0, 0, 0)
                if (host?.isMoving(node) == true) {
                    b.card.setCardBackgroundColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            b.card, com.google.android.material.R.attr.colorSecondaryContainer,
                        ),
                    )
                } else {
                    b.card.setCardBackgroundColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            b.card, com.google.android.material.R.attr.colorSurfaceContainerLow,
                        ),
                    )
                }

                b.name.text = node.name
                b.url.visibility = View.GONE
                b.icon.setImageResource(
                    if (node.isFeed) io.github.xixka.qbittorrent.R.drawable.ic_rss_feed_24px
                    else io.github.xixka.qbittorrent.R.drawable.ic_folder_24px,
                )

                // expand arrow only when the folder has children; a fixed
                // 48dp placeholder keeps rows aligned otherwise (qBC)
                val hasChildren = !node.isFeed && node.children.isNotEmpty()
                val isExpanded = node.apiPath in expanded
                b.expandArrow.visibility = if (hasChildren) View.VISIBLE else View.GONE
                b.arrowSpacer.visibility = if (hasChildren) View.GONE else View.VISIBLE
                b.expandArrow.rotation = if (isExpanded) 0f else -90f
                b.expandArrow.setOnClickListener {
                    if (node.apiPath in expanded) expanded.remove(node.apiPath) else expanded.add(node.apiPath)
                    load()
                }

                b.nodeMenu.setOnClickListener { v -> host?.showNodeMenu(node, v) }

                b.card.setOnClickListener {
                    if (host?.inMoveMode() == true) {
                        // qBC: while moving, tapping a folder drops the item
                        if (!node.isFeed) host?.completeMove(node)
                    } else {
                        openArticles(node)
                    }
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
            onClick = { (name, rule) -> (parentFragment as? RssFragment)?.showRuleMenu(name, rule) },
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
                b.expandArrow.visibility = View.GONE
                b.arrowSpacer.visibility = View.GONE
                b.nodeMenu.visibility = View.GONE
                b.card.setPadding(0, 0, 0, 0)
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
