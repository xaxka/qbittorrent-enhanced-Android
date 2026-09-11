package io.github.xixka.qbittorrent.ui.rss

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.FragmentRssRulesBinding
import io.github.xixka.qbittorrent.databinding.ItemRssRuleBinding
import io.github.xixka.qbittorrent.model.RssRule
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * RSS downloader screen — qBC RssRulesScreen parity, a PUSHED page (not a
 * tab): back navigation, the "RSS downloader" title and the create-rule
 * action in the toolbar; below it the rule cards (name + Enabled/Disabled
 * chip + affected-feeds chip + overflow menu with rename / delete), and
 * pull-to-refresh. Tapping a rule pushes the full rule editor screen,
 * exactly like upstream navigates to EditRssRuleScreen.
 */
class RssRulesFragment : Fragment() {

    private var _binding: FragmentRssRulesBinding? = null
    private val binding get() = _binding!!

    private var adapter: RulesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRssRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_rss_add_rule) {
                showCreateRuleDialog()
                true
            } else {
                false
            }
        }

        // qBC LazyColumn parity: 12dp horizontal / 8dp top edge padding,
        // spacedBy(8.dp) between the rule cards.
        val density = resources.displayMetrics.density
        binding.ruleList.layoutManager = LinearLayoutManager(requireContext())
        binding.ruleList.setHasFixedSize(true)
        binding.ruleList.clipToPadding = false
        binding.ruleList.setPadding(
            (12 * density).toInt(),
            (8 * density).toInt(),
            (12 * density).toInt(),
            0,
        )

        adapter = RulesAdapter()
        binding.ruleList.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { load() }

        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    fun load() {
        _binding ?: return
        lifecycleScope.launch {
            val rules = runCatching {
                ServiceLocator.repository(requireContext()).rssRules()
            }.getOrDefault(emptyMap())
            if (_binding == null) return@launch
            adapter?.submitList(rules.toList())
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun snackbar(res: Int) {
        _binding ?: return
        Snackbar.make(binding.root, res, Snackbar.LENGTH_SHORT).show()
    }

    // ---------------- dialogs ----------------

    /** qBC CreateRuleDialog: single name field, OK disabled on blank. */
    private fun showCreateRuleDialog() {
        showNameDialog(
            title = getString(R.string.rss_add_rule),
            initial = "",
            errorRes = R.string.rss_rule_name_empty,
        ) { name ->
            lifecycleScope.launch {
                val result = runCatching {
                    ServiceLocator.repository(requireContext()).rssSetRule(
                        name,
                        RssRule(enabled = true),
                    )
                }
                snackbar(
                    if (result.isSuccess) R.string.rss_rule_create_success
                    else R.string.rss_action_failed
                )
                load()
            }
        }
    }

    private fun showRenameRuleDialog(name: String) {
        showNameDialog(
            title = getString(R.string.rss_rename_rule),
            initial = name,
            errorRes = R.string.rss_rule_name_empty,
        ) { newName ->
            lifecycleScope.launch {
                val result = runCatching {
                    ServiceLocator.repository(requireContext()).rssRenameRule(name, newName)
                }
                snackbar(
                    if (result.isSuccess) R.string.rss_rule_rename_success
                    else R.string.rss_action_failed
                )
                load()
            }
        }
    }

    private fun showNameDialog(
        title: String,
        initial: String,
        errorRes: Int,
        onConfirm: (String) -> Unit,
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        // qBC CreateRuleDialog: OutlinedTextField labelled "Rule name".
        view.findViewById<TextInputLayout>(R.id.inputLayout)?.hint =
            getString(R.string.rss_rule_name)
        input?.setText(initial)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input?.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                input?.error = getString(errorRes)
                return@setOnClickListener
            }
            dialog.dismiss()
            onConfirm(name)
        }
    }

    private fun confirmDeleteRule(name: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rss_delete_rule)
            .setMessage(getString(R.string.rss_delete_rule_confirm, name))
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                lifecycleScope.launch {
                    val result = runCatching {
                        ServiceLocator.repository(requireContext()).rssRemoveRule(name)
                    }
                    snackbar(
                        if (result.isSuccess) R.string.rss_rule_delete_success
                        else R.string.rss_action_failed
                    )
                    load()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------------- adapter ----------------

    private inner class RulesAdapter :
        ListAdapter<Pair<String, RssRule>, RulesAdapter.Holder>(DIFF) {

        inner class Holder(private val b: ItemRssRuleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: Pair<String, RssRule>) {
                val (name, rule) = item
                b.ruleName.text = name

                // qBC chips: Enabled = primary @15% alpha with primary text;
                // Disabled = surfaceVariant; feeds = tertiaryContainer.
                if (rule.enabled) {
                    b.chipEnabled.text = getString(R.string.rss_rule_item_enabled)
                    b.chipEnabled.setBackgroundResource(R.drawable.bg_category_chip)
                    val primary = MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorPrimary)
                    b.chipEnabled.backgroundTintList =
                        ColorStateList.valueOf((primary and 0x00FFFFFF) or (0x26 shl 24))
                    b.chipEnabled.setTextColor(primary)
                } else {
                    b.chipEnabled.text = getString(R.string.rss_rule_item_disabled)
                    b.chipEnabled.setBackgroundResource(R.drawable.bg_chip_surface_variant)
                    b.chipEnabled.setTextColor(
                        MaterialColors.getColor(
                            b.root,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                        )
                    )
                }

                if (rule.affectedFeeds.isNotEmpty()) {
                    b.chipFeeds.visibility = View.VISIBLE
                    b.chipFeedsCount.text =
                        resources.getQuantityString(
                            R.plurals.rss_rule_feeds,
                            rule.affectedFeeds.size,
                            rule.affectedFeeds.size,
                        )
                } else {
                    b.chipFeeds.visibility = View.GONE
                }

                b.card.setOnClickListener {
                    (activity as? MainActivity)?.pushPage(
                        RssRuleEditorFragment.newInstance(name)
                    )
                }

                b.ruleMenu.setOnClickListener { anchor -> showRuleMenu(name, anchor) }
            }
        }

        private fun showRuleMenu(name: String, anchor: View) {
            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.add(0, 1, 0, R.string.rss_rename_rule)
                .setIcon(R.drawable.ic_edit_24px)
            popup.menu.add(0, 2, 1, R.string.rss_delete_rule)
                .setIcon(R.drawable.ic_delete_24px)
            RssFragment.showPopupWithIcons(popup)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showRenameRuleDialog(name)
                    2 -> confirmDeleteRule(name)
                }
                true
            }
            popup.show()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemRssRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

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
