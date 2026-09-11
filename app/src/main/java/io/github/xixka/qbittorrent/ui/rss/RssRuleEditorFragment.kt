package io.github.xixka.qbittorrent.ui.rss

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.FragmentRssRuleEditorBinding
import io.github.xixka.qbittorrent.model.RssFeedNode
import io.github.xixka.qbittorrent.model.RssRule
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Rule editor screen — qBC EditRssRuleScreen parity: a pushed page with the
 * rule name as title, back navigation and a Save toolbar action. The form
 * mirrors upstream's field order and behaviour: checkboxes (enabled /
 * regex / smart filter / save-to-other-dir), text fields (must contain /
 * must not contain / episode filter / save to / ignore days), exposed
 * dropdowns (category from the server, add paused, torrent content
 * layout) and the bordered "apply rule to feeds" checkbox list. Saving
 * reports through a snackbar; the feeds list reloads behind the scenes.
 */
class RssRuleEditorFragment : Fragment() {

    private var _binding: FragmentRssRuleEditorBinding? = null
    private val binding get() = _binding!!

    private val ruleName by lazy { arguments?.getString(ARG_RULE_NAME).orEmpty() }

    private var rule: RssRule = RssRule()
    private var loaded = false

    private var pausedIndex = 0
    private var layoutIndex = 0
    private var selectedCategory = ""
    private val feedChecks = mutableListOf<Pair<String, MaterialCheckBox>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRssRuleEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.appBar.title = ruleName
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_rss_save_rule) {
                saveRule()
                true
            } else {
                false
            }
        }
        binding.ruleSaveOtherDir.setOnCheckedChangeListener { _, checked ->
            binding.ruleSavePathLayout.visibility = if (checked) View.VISIBLE else View.GONE
        }
        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun repository() = ServiceLocator.repository(requireContext())

    private fun snackbar(res: Int) {
        _binding ?: return
        Snackbar.make(binding.root, res, Snackbar.LENGTH_SHORT).show()
    }

    fun load() {
        lifecycleScope.launch {
            val rules = runCatching { repository().rssRules() }.getOrDefault(emptyMap())
            rule = rules[ruleName] ?: RssRule()
            if (rules.isEmpty() || !rules.containsKey(ruleName)) {
                if (loaded) snackbar(R.string.rss_rule_not_found)
            }
            loaded = true
            if (_binding == null) return@launch
            populate()
        }
    }

    private fun populate() {
        val b = binding
        b.ruleEnabled.isChecked = rule.enabled
        b.ruleUseRegex.isChecked = rule.useRegex
        b.ruleMustContain.setText(rule.mustContain)
        b.ruleMustNotContain.setText(rule.mustNotContain)
        b.ruleEpisodeFilter.setText(rule.episodeFilter)
        b.ruleSmartFilter.isChecked = rule.smartFilter
        b.ruleIgnoreDays.setText(rule.ignoreDays.toString())
        b.ruleSaveOtherDir.isChecked = !rule.savePath.isNullOrEmpty()
        b.ruleSavePath.setText(rule.savePath)
        b.ruleSavePathLayout.visibility = if (b.ruleSaveOtherDir.isChecked) View.VISIBLE else View.GONE

        selectedCategory = rule.assignedCategory
        pausedIndex = when (rule.addPaused) {
            true -> 1
            false -> 2
            else -> 0
        }
        layoutIndex = when (rule.contentLayout) {
            "Original" -> 1
            "Subfolder" -> 2
            "NoSubfolder" -> 3
            else -> 0
        }
        setupDropdown(
            dropdown = b.ruleAddPaused,
            labels = listOf(
                getString(R.string.rss_rule_use_global_settings),
                getString(R.string.rss_rule_add_paused_always),
                getString(R.string.rss_rule_add_paused_never),
            ),
            selected = pausedIndex,
        ) { pausedIndex = it }

        setupContentLayoutDropdown()
        setupCategoriesDropdown()
        loadFeeds()
    }

    private fun setupContentLayoutDropdown() {
        val labels = listOf(
            getString(R.string.rss_rule_use_global_settings),
            getString(R.string.qbt_content_layout_original),
            getString(R.string.qbt_content_layout_subfolder),
            getString(R.string.qbt_content_layout_nosubfolder),
        )
        setupDropdown(
            dropdown = binding.ruleContentLayout,
            labels = labels,
            selected = layoutIndex,
        ) { layoutIndex = it }
    }

    private fun setupDropdown(
        dropdown: MaterialAutoCompleteTextView,
        labels: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        dropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels),
        )
        dropdown.setText(labels.getOrElse(selected) { labels.first() }, false)
        dropdown.setOnItemClickListener { _, _, which, _ -> onSelect(which) }
    }

    /** qBC: category dropdown fed by the server categories; first entry
     *  is the empty category (upstream renders it as an empty string). */
    private fun setupCategoriesDropdown() {
        lifecycleScope.launch {
            val categories = runCatching { repository().categories().keys }
                .getOrDefault(emptySet())
                .sorted()
            if (_binding == null) return@launch
            val input = binding.ruleCategory
            val labels = listOf("") + categories
            input.setAdapter(
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    labels,
                ),
            )
            input.setText(
                if (selectedCategory in categories) selectedCategory else "",
                false,
            )
            input.setOnItemClickListener { _, _, which, _ ->
                selectedCategory = labels.getOrElse(which) { "" }
            }
        }
    }

    /** qBC: the "apply rule to feeds" checkbox list inside the outlined
     *  box, one checkbox per feed of the current tree. */
    private fun loadFeeds() {
        lifecycleScope.launch {
            val feeds = runCatching {
                flattenFeeds(RssTreeParser.parse(repository().rssItems(false)))
            }.getOrDefault(emptyList())
            if (_binding == null) return@launch
            val box = binding.ruleFeedsBox
            box.removeAllViews()
            feedChecks.clear()
            if (feeds.isEmpty()) {
                binding.ruleNoFeeds.visibility = View.VISIBLE
                box.visibility = View.GONE
            } else {
                binding.ruleNoFeeds.visibility = View.GONE
                box.visibility = View.VISIBLE
                feeds.forEach { (feedName, feedUrl) ->
                    val cb = MaterialCheckBox(box.context).apply {
                        text = feedName
                        isChecked = feedUrl in rule.affectedFeeds
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    }
                    box.addView(cb)
                    feedChecks.add(feedUrl to cb)
                }
            }
        }
    }

    private fun saveRule() {
        val b = binding
        val newRule = RssRule(
            enabled = b.ruleEnabled.isChecked,
            mustContain = b.ruleMustContain.text?.toString()?.trim().orEmpty(),
            mustNotContain = b.ruleMustNotContain.text?.toString()?.trim().orEmpty(),
            useRegex = b.ruleUseRegex.isChecked,
            episodeFilter = b.ruleEpisodeFilter.text?.toString()?.trim().orEmpty(),
            ignoreDays = b.ruleIgnoreDays.text?.toString()?.trim()?.toIntOrNull() ?: 0,
            addPaused = when (pausedIndex) {
                1 -> true
                2 -> false
                else -> null
            },
            assignedCategory = selectedCategory,
            savePath = if (b.ruleSaveOtherDir.isChecked) {
                b.ruleSavePath.text?.toString()?.trim().orEmpty()
            } else {
                ""
            },
            contentLayout = when (layoutIndex) {
                1 -> "Original"
                2 -> "Subfolder"
                3 -> "NoSubfolder"
                else -> null
            },
            smartFilter = b.ruleSmartFilter.isChecked,
            affectedFeeds = feedChecks.filter { it.second.isChecked }.map { it.first },
        )
        lifecycleScope.launch {
            val result = runCatching { repository().rssSetRule(ruleName, newRule) }
            snackbar(
                if (result.isSuccess) R.string.rss_rule_saved else R.string.rss_action_failed,
            )
            if (result.isSuccess) {
                (activity as? MainActivity)?.popPage()
            }
        }
    }

    /** Flattens the feed tree into (name, url) pairs for the feeds box. */
    private fun flattenFeeds(nodes: List<RssFeedNode>): List<Pair<String, String>> =
        nodes.flatMap { node ->
            if (node.isFeed) {
                if (node.url != null) listOf(node.name to node.url) else emptyList()
            } else {
                flattenFeeds(node.children)
            }
        }

    companion object {
        private const val ARG_RULE_NAME = "ruleName"

        fun newInstance(ruleName: String): RssRuleEditorFragment =
            RssRuleEditorFragment().apply {
                arguments = Bundle().apply { putString(ARG_RULE_NAME, ruleName) }
            }
    }
}
