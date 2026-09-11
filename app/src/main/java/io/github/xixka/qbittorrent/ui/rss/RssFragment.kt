package io.github.xixka.qbittorrent.ui.rss

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuCompat
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
import io.github.xixka.qbittorrent.databinding.ActivityRssBinding
import io.github.xixka.qbittorrent.databinding.ItemRssNodeBinding
import io.github.xixka.qbittorrent.model.RssFeedNode
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RSS tab root — qBC RssFeedsScreen parity, feeds ONLY (no tabs, no pager):
 *  - toolbar: add (dropdown with feed / folder), refresh all and the
 *    double-arrow-down "rules" action that pushes the RSS downloader
 *    screen ([RssRulesFragment]) like upstream.
 *  - list: the subscription tree as elevated cards indented by depth
 *    (level * 12dp) with the synthetic root "/" (uniqueId "0-/",
 *    expanded by default) as the first card; expand arrows, folder /
 *    rss icons tinted primary, per-node overflow menus with icons
 *    (rename / edit URL / move / delete, add feed / add folder for
 *    folders, add-only for the level-0 root).
 *  - move mode with the bottom bar ("select destination folder").
 *  - tapping any node — feed OR folder — opens its articles screen.
 */
class RssFragment : Fragment() {

    private var _binding: ActivityRssBinding? = null
    private val binding get() = _binding!!

    private var adapter: NodeAdapter? = null

    /** Guards against stacking the 8dp gap decoration on view re-creation. */
    private var spacingDecorationAdded = false

    /** Node ids whose children are shown; the root "0-/" starts expanded. */
    private val expanded = mutableSetOf<String>()

    private var rootNode: RssFeedNode? = null
    private var movingNode: RssFeedNode? = null

    /** Cancels move mode on system back, qBC parity. */
    private val moveBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = cancelMoveMode()
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
        binding.appBar.setTitle(R.string.rss_title)
        binding.appBar.inflateMenu(R.menu.rss_feeds)
        binding.appBar.setOnMenuItemClickListener { item -> onMenuItem(item.itemId) }
        binding.moveBar.setNavigationOnClickListener { cancelMoveMode() }

        val list = binding.feedList
        list.layoutManager = LinearLayoutManager(requireContext())
        list.clipToPadding = false
        // qBC LazyColumn parity: contentPadding = (start 12, end 12, top 8)
        // and spacedBy(8.dp) between the cards.
        val density = resources.displayMetrics.density
        list.setPadding(
            (12 * density).toInt(),
            (8 * density).toInt(),
            (12 * density).toInt(),
            (8 * density).toInt(),
        )
        if (!spacingDecorationAdded) {
            spacingDecorationAdded = true
            addItemDecorationSpacing(list, density)
        }

        adapter = NodeAdapter(
            onClick = { onNodeClick(it) },
            onToggleExpand = { toggleExpand(it) },
            onMenu = { node, anchor -> showNodeMenu(node, anchor) },
        )
        list.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
            load()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, moveBackCallback)

        if (savedInstanceState == null) {
            expanded.add(ROOT_ID)
        }
        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_EXPANDED, ArrayList(expanded))
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getStringArrayList(KEY_EXPANDED)?.let { expanded.addAll(it) }
    }

    // ---------------- data ----------------

    private fun repository() = ServiceLocator.repository(requireContext())

    /** Loads the tree and submits the flattened expanded view. */
    fun load() {
        lifecycleScope.launch {
            val root = runCatching {
                RssTreeParser.parseTree(repository().rssItems(true))
            }.getOrNull()
            if (_binding == null) return@launch
            rootNode = root
            submitNodes()
        }
    }

    /** qBC processNodes: depth-first walk skipping collapsed folders. */
    private fun submitNodes() {
        val root = rootNode ?: return
        val result = mutableListOf<RssFeedNode>()
        val stack = ArrayDeque<RssFeedNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            result.add(node)
            if (node.uniqueId in expanded && node.children.isNotEmpty()) {
                node.children.asReversed().forEach { stack.add(it) }
            }
        }
        adapter?.submitList(result)
    }

    private fun toggleExpand(node: RssFeedNode) {
        if (node.isFeed || node.children.isEmpty()) return
        if (node.uniqueId in expanded) expanded.remove(node.uniqueId) else expanded.add(node.uniqueId)
        submitNodes()
    }

    private fun onNodeClick(node: RssFeedNode) {
        val moving = movingNode
        if (moving != null) {
            // qBC: tapping a folder while moving drops the item there
            if (!node.isFeed) completeMove(node)
        } else {
            openArticles(node)
        }
    }

    private fun openArticles(node: RssFeedNode) {
        (activity as? MainActivity)?.pushPage(
            RssArticlesFragment.newInstance(node.apiPath, node.name),
        )
    }

    // ---------------- toolbar ----------------

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
            (activity as? MainActivity)?.pushPage(RssRulesFragment())
            true
        }
        else -> false
    }

    /** qBC's add dropdown, anchored at the toolbar action. */
    private fun showAddMenu(anchor: View?) {
        anchor ?: return
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, ID_ADD_FEED, 0, R.string.rss_add_feed).setIcon(R.drawable.ic_rss_feed_24px)
        popup.menu.add(0, ID_ADD_FOLDER, 1, R.string.rss_add_folder).setIcon(R.drawable.ic_folder_24px)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == ID_ADD_FEED) showAddFeedDialog(emptyList()) else showAddFolderDialog(emptyList())
            true
        }
        showPopupWithIcons(popup)
        popup.show()
    }

    private fun snackbar(res: Int) {
        _binding ?: return
        Snackbar.make(binding.root, res, Snackbar.LENGTH_SHORT).show()
    }

    /** qBC refreshAllFeeds: an empty itemPath refreshes every feed; the
     *  server fetch is async, so the tree is re-read a second later —
     *  reloading immediately only shows the stale pre-fetch state. */
    private fun refreshAll() {
        lifecycleScope.launch {
            val result = runCatching { repository().rssRefreshItem("") }
            snackbar(if (result.isSuccess) R.string.rss_refresh_all_done else R.string.rss_action_failed)
            delay(1000)
            load()
        }
    }

    // ---------------- move mode (qBC) ----------------

    fun startMoveMode(node: RssFeedNode) {
        movingNode = node
        moveBackCallback.isEnabled = true
        binding.moveBar.visibility = View.VISIBLE
        submitNodes()
    }

    private fun cancelMoveMode() {
        movingNode = null
        moveBackCallback.isEnabled = false
        binding.moveBar.visibility = View.GONE
        submitNodes()
    }

    private fun completeMove(destFolder: RssFeedNode) {
        val moving = movingNode ?: return
        val from = moving.apiPath
        val to = (destFolder.path + destFolder.name + moving.name).joinToString("\\")
        movingNode = null
        moveBackCallback.isEnabled = false
        binding.moveBar.visibility = View.GONE
        lifecycleScope.launch {
            val result = runCatching { repository().rssMoveItem(from, to) }
            snackbar(if (result.isSuccess) R.string.rss_success else R.string.rss_action_failed)
            load()
        }
    }

    // ---------------- node overflow menu ----------------

    /**
     * qBC FeedItem dropdown, icons included: level > 0 nodes offer
     * rename / edit URL (feeds) / move / delete, folders add feed +
     * add folder below a divider; the level-0 root "/" offers ONLY
     * add feed / add folder.
     */
    fun showNodeMenu(node: RssFeedNode, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        if (node.level > 0) {
            popup.menu.add(1, ID_RENAME, 0, menuLabel(R.string.rss_rename_feed, R.string.rss_rename_folder, node))
                .setIcon(R.drawable.ic_edit_24px)
            if (node.isFeed) {
                popup.menu.add(1, ID_EDIT_URL, 1, R.string.rss_edit_url)
                    .setIcon(R.drawable.ic_link_24px)
            }
            popup.menu.add(1, ID_MOVE, 2, menuLabel(R.string.rss_move_feed, R.string.rss_move_folder, node))
                .setIcon(R.drawable.ic_drive_file_move_24px)
            popup.menu.add(1, ID_DELETE, 3, menuLabel(R.string.rss_delete_feed, R.string.rss_delete_folder, node))
                .setIcon(R.drawable.ic_delete_24px)
            if (!node.isFeed) {
                popup.menu.add(2, ID_ADD_FEED_INTO, 4, R.string.rss_add_feed)
                    .setIcon(R.drawable.ic_rss_feed_24px)
                popup.menu.add(2, ID_ADD_FOLDER_INTO, 5, R.string.rss_add_folder)
                    .setIcon(R.drawable.ic_folder_24px)
                MenuCompat.setGroupDividerEnabled(popup.menu, true)
            }
        } else {
            popup.menu.add(0, ID_ADD_FEED_INTO, 0, R.string.rss_add_feed)
                .setIcon(R.drawable.ic_rss_feed_24px)
            popup.menu.add(0, ID_ADD_FOLDER_INTO, 1, R.string.rss_add_folder)
                .setIcon(R.drawable.ic_folder_24px)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_RENAME -> showRenameNodeDialog(node)
                ID_EDIT_URL -> showEditUrlDialog(node)
                ID_MOVE -> startMoveMode(node)
                ID_DELETE -> confirmDeleteNode(node)
                ID_ADD_FEED_INTO -> showAddFeedDialog(node.path + node.name)
                ID_ADD_FOLDER_INTO -> showAddFolderDialog(node.path + node.name)
            }
            true
        }
        showPopupWithIcons(popup)
        popup.show()
    }

    private fun menuLabel(feedRes: Int, folderRes: Int, node: RssFeedNode) =
        getString(if (node.isFeed) feedRes else folderRes)

    // ---------------- feed / folder dialogs ----------------

    private fun showAddFeedDialog(parentPath: List<String>) {
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
            val itemPath = (parentPath + feedName.ifBlank { feedUrl }).joinToString("\\")
            dialog.dismiss()
            lifecycleScope.launch {
                val result = runCatching { repository().rssAddFeed(feedUrl, itemPath) }
                snackbar(if (result.isSuccess) R.string.rss_added else R.string.rss_action_failed)
                load()
            }
        }
    }

    private fun showAddFolderDialog(parentPath: List<String>) {
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
            val itemPath = (parentPath + name).joinToString("\\")
            dialog.dismiss()
            lifecycleScope.launch {
                runCatching { repository().rssAddFolder(itemPath) }
                snackbar(R.string.rss_success)
                load()
            }
        }
    }

    /** qBC rename: a moveItem to the same parent with the new name. */
    private fun showRenameNodeDialog(node: RssFeedNode) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.inputType = InputType.TYPE_CLASS_TEXT
        input?.setText(node.name)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(menuLabel(R.string.rss_rename_feed, R.string.rss_rename_folder, node))
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
                    load()
                }
            } else {
                dialog.dismiss()
            }
        }
    }

    /** qBC edit feed URL: setFeedUrl with the node's path. */
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
                snackbar(if (result.isSuccess) R.string.rss_success_feed_url else R.string.rss_action_failed)
                load()
            }
        }
    }

    private fun confirmDeleteNode(node: RssFeedNode) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(menuLabel(R.string.rss_delete_feed, R.string.rss_delete_folder, node))
            .setMessage(getString(R.string.rss_delete_confirm, node.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val result = runCatching { repository().rssRemoveItem(node.apiPath) }
                    snackbar(if (result.isSuccess) R.string.rss_success else R.string.rss_action_failed)
                    load()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- list ----------------

    /**
     * qBC FeedItem parity: elevated card indented by depth (level * 12dp),
     * expand arrow (48dp, or a 48dp placeholder for childless folders and
     * feeds), rss / folder icon tinted primary, name, overflow menu;
     * move mode highlights the moving card.
     */
    private inner class NodeAdapter(
        private val onClick: (RssFeedNode) -> Unit,
        private val onToggleExpand: (RssFeedNode) -> Unit,
        private val onMenu: (RssFeedNode, View) -> Unit,
    ) : ListAdapter<RssFeedNode, NodeAdapter.Holder>(DIFF) {

        inner class Holder(private val b: ItemRssNodeBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: RssFeedNode) {
                val density = b.root.context.resources.displayMetrics.density
                (b.card.layoutParams as RecyclerView.LayoutParams).apply {
                    val indent = (item.level * 12 * density).toInt()
                    if (marginStart != indent) {
                        marginStart = indent
                        b.card.requestLayout()
                    }
                }
                // move mode: qBC highlights the moving card
                b.card.setStrokeWidth(0)
                val moving = movingNode?.apiPath == item.apiPath
                if (moving) {
                    b.card.setCardBackgroundColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            b.root, com.google.android.material.R.attr.colorSecondaryContainer,
                        )
                    )
                } else {
                    b.card.setCardBackgroundColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            b.root, com.google.android.material.R.attr.colorSurfaceContainerLow,
                        )
                    )
                }

                val showArrow = !item.isFeed && item.children.isNotEmpty()
                b.expandArrow.visibility = if (showArrow) View.VISIBLE else View.GONE
                b.arrowSpacer.visibility = if (showArrow) View.GONE else View.VISIBLE
                if (showArrow) {
                    b.expandArrow.setImageResource(
                        if (item.uniqueId in expanded) {
                            R.drawable.ic_keyboard_arrow_down_24px
                        } else {
                            R.drawable.ic_keyboard_arrow_right_24px
                        }
                    )
                    b.expandArrow.setOnClickListener { onToggleExpand(item) }
                }

                b.icon.setImageResource(
                    if (item.isFeed) R.drawable.ic_rss_feed_24px else R.drawable.ic_folder_24px
                )
                b.name.text = item.name
                b.url.visibility = View.GONE

                b.card.setOnClickListener { onClick(item) }
                b.nodeMenu.setOnClickListener { onMenu(item, it) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemRssNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    }

    // ---------------- helpers ----------------

    /** 8dp gap between the cards, qBC spacedBy(8.dp) parity. */
    private fun addItemDecorationSpacing(list: RecyclerView, density: Float) {
        val gap = (8 * density).toInt()
        list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                outRect.bottom = gap
            }
        })
    }

    companion object {
        /** qBC uniqueId of the synthetic tree root shown as the first card. */
        const val ROOT_ID = "0-/"

        private const val KEY_EXPANDED = "expanded"
        private const val ID_ADD_FEED = 1
        private const val ID_ADD_FOLDER = 2
        private const val ID_RENAME = 3
        private const val ID_EDIT_URL = 4
        private const val ID_MOVE = 5
        private const val ID_DELETE = 6
        private const val ID_ADD_FEED_INTO = 7
        private const val ID_ADD_FOLDER_INTO = 8

        private val DIFF = object : DiffUtil.ItemCallback<RssFeedNode>() {
            override fun areItemsTheSame(oldItem: RssFeedNode, newItem: RssFeedNode) =
                oldItem.apiPath == newItem.apiPath

            override fun areContentsTheSame(oldItem: RssFeedNode, newItem: RssFeedNode) =
                oldItem == newItem
        }

        /**
         * PopupMenu keeps its icons hidden by default; qBC's dropdown menus
         * show leading icons, so force them on (framework reflection, best
         * effort — falls back to plain text when unavailable).
         */
        fun showPopupWithIcons(popup: PopupMenu) {
            try {
                val field = popup.javaClass.getDeclaredField("mPopup")
                field.isAccessible = true
                val menuHelper = field.get(popup)
                val cls = Class.forName(menuHelper.javaClass.name)
                val method = cls.getDeclaredMethod(
                    "setForceShowIcon",
                    Boolean::class.javaPrimitiveType,
                )
                method.invoke(menuHelper, true)
            } catch (_: Exception) {
                // icons stay hidden — menus still work
            }
        }
    }
}
