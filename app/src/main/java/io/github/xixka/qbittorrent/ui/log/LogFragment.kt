package io.github.xixka.qbittorrent.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityLogBinding
import io.github.xixka.qbittorrent.databinding.ItemLogRowBinding
import io.github.xixka.qbittorrent.model.LogEntry
import io.github.xixka.qbittorrent.ui.main.MainActivity
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Engine log viewer (qBitController LogScreen parity): the WebUI log feed of
 * the connected server, filtered by severity chips, in LibreTorrent styling.
 * Opened from Settings, hosted IN PLACE — no separate window.
 */
class LogFragment : Fragment() {

    private var _binding: ActivityLogBinding? = null
    private val binding get() = _binding!!

    private val adapter = LogAdapter()

    private var all: List<LogEntry> = emptyList()
    private var filter = 0 // 0 = all

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
        applyWindowInsets(
            child = binding.logList,
            sideMask = WindowInsetsSide.LEFT or WindowInsetsSide.RIGHT,
        )
        binding.appBar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }
        binding.appBar.setOnMenuItemClickListener {
            if (it.itemId == R.id.refresh_log_menu) {
                load()
                true
            } else false
        }

        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = adapter
        binding.logList.setEmptyView(binding.emptyView)

        binding.typeChipGroup.setOnCheckedStateChangeListener { group, _ -> onFilterChanged(group) }
        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun onFilterChanged(group: ChipGroup) {
        filter = when (group.checkedChipId) {
            R.id.chip_normal -> 1
            R.id.chip_info -> 2
            R.id.chip_warning -> 4
            R.id.chip_critical -> 8
            else -> 0
        }
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (filter == 0) all else all.filter { it.type == filter }
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.logList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun load() {
        lifecycleScope.launch {
            val result = runCatching {
                ServiceLocator.repository(requireContext()).log()
            }
            result
                .onSuccess { entries ->
                    all = entries.sortedByDescending { it.id }
                    applyFilter()
                }
                .onFailure { e ->
                    all = emptyList()
                    applyFilter()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.error)
                        .setMessage(e.message ?: getString(R.string.error_connection))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
        }
    }

    // ---------------- adapter ----------------

    private inner class LogAdapter :
        ListAdapter<LogEntry, LogAdapter.ViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemLogRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(getItem(position))

        inner class ViewHolder(private val b: ItemLogRowBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(entry: LogEntry) {
                b.logType.setText(entry.typeRes)
                val colorRes = when {
                    entry.isCritical -> R.color.md_theme_error
                    entry.isWarning -> R.color.colorWarn
                    else -> R.color.md_theme_primary
                }
                b.logType.setTextColor(ContextCompat.getColor(b.root.context, colorRes))
                b.logTime.text = if (entry.timestamp > 0) {
                    timeFormat.format(Date(entry.timestamp * 1000))
                } else "—"
                b.logMessage.text = entry.message
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LogEntry>() {
            override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry) =
                oldItem == newItem
        }
    }
}
