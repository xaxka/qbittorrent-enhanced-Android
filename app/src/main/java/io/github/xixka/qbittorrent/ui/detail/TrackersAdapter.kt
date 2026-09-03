package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemTrackerBinding
import io.github.xixka.qbittorrent.model.Tracker

class TrackersAdapter(
    private val onLongClick: (Tracker) -> Unit,
) : ListAdapter<Tracker, TrackersAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemTrackerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTrackerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tracker = getItem(position)
        val binding = holder.binding
        val context = binding.root.context

        val isDht = tracker.url.startsWith("**")
        binding.trackerUrl.text = tracker.url
        binding.trackerStatus.text = trackerStatusLabel(context, tracker.status)
        if (tracker.msg.isNotBlank() && tracker.status != 2) {
            binding.trackerMsg.text = tracker.msg
            binding.trackerMsg.visibility = View.VISIBLE
        } else {
            binding.trackerMsg.visibility = View.GONE
        }

        binding.trackerStats.text =
            context.getString(R.string.tracker_stats, tracker.numSeeds, tracker.numLeeches)

        val color = when (tracker.status) {
            2 -> ContextCompat.getColor(context, R.color.colorOk)
            3 -> ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
            else -> ContextCompat.getColor(context, R.color.md_theme_error)
        }
        binding.trackerStatus.setTextColor(color)

        binding.root.setOnLongClickListener {
            if (isDht) return@setOnLongClickListener false
            onLongClick(tracker)
            true
        }
    }

    private fun trackerStatusLabel(context: android.content.Context, status: Int): String = when (status) {
        0 -> context.getString(R.string.tracker_status_disabled)
        1 -> context.getString(R.string.tracker_status_not_contacted)
        2 -> context.getString(R.string.tracker_status_working)
        3 -> context.getString(R.string.tracker_status_updating)
        4 -> context.getString(R.string.tracker_status_not_working)
        5 -> context.getString(R.string.tracker_status_unreachable)
        else -> context.getString(R.string.tracker_status_banned)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Tracker>() {
            override fun areItemsTheSame(oldItem: Tracker, newItem: Tracker) =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: Tracker, newItem: Tracker) =
                oldItem == newItem
        }
    }
}
