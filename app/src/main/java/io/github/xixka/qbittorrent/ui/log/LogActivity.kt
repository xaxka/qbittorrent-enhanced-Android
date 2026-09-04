package io.github.xixka.qbittorrent.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Engine log viewer (qBitController LogScreen parity): the WebUI log feed of
 * the connected server, filtered by severity chips, in LibreTorrent styling.
 */
class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private val adapter = LogAdapter()

    private var all: List<LogEntry> = emptyList()
    private var filter = 0 // 0 = all

    private val timeFormat: DateFormat by lazy {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(
            child = binding.logList,
            sideMask = WindowInsetsSide.LEFT or WindowInsetsSide.RIGHT or WindowInsetsSide.BOTTOM,
        )
        binding.appBar.setNavigationOnClickListener { finish() }
        binding.appBar.setOnMenuItemClickListener {
            if (it.itemId == R.id.refresh_log_menu) {
                load()
                true
            } else false
        }

        binding.logList.layoutManager = LinearLayoutManager(this)
        binding.logList.adapter = adapter
        binding.logList.setEmptyView(binding.emptyView)

        binding.typeChipGroup.setOnCheckedStateChangeListener { group, _ -> onFilterChanged(group) }
        load()
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
                ServiceLocator.repository(this@LogActivity).log()
            }
            result
                .onSuccess { entries ->
                    all = entries.sortedByDescending { it.id }
                    applyFilter()
                }
                .onFailure { e ->
                    all = emptyList()
                    applyFilter()
                    MaterialAlertDialogBuilder(this@LogActivity)
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
