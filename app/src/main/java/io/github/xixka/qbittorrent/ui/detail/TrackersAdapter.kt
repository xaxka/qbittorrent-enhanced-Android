package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemTrackersListBinding
import io.github.xixka.qbittorrent.model.Tracker

/**
 * Trackers list, ported from LibreTorrent's TrackerListAdapter (GPL-3.0):
 * url + colored status + message; selected rows shown as activated cards.
 */
class TrackersAdapter(
    private val isSelected: (Tracker) -> Boolean,
    private val onClick: (Tracker) -> Unit,
    private val onLongClick: (Tracker) -> Unit,
) : ListAdapter<Tracker, TrackersAdapter.ViewHolder>(DIFF) {

    class ViewHolder(
        val binding: ItemTrackersListBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemTrackersListBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        bind(holder.binding, getItem(position))

    private fun bind(binding: ItemTrackersListBinding, tracker: Tracker) {
        binding.url.text = tracker.url
        val (labelRes, colorRes) = trackerLabel(tracker)
        binding.status.text = binding.root.context.getString(labelRes)
        binding.status.setTextColor(ContextCompat.getColor(binding.root.context, colorRes))
        val msg = tracker.msg
        binding.message.text = msg
        binding.message.visibility =
            if (msg.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

        binding.card.isActivated = isSelected(tracker)
        binding.card.setOnClickListener { onClick(tracker) }
        binding.card.setOnLongClickListener {
            onLongClick(tracker)
            true
        }
    }

    /** qBittorrent tracker status codes from /torrents/trackers. */
    private fun trackerLabel(tracker: Tracker): Pair<Int, Int> = when (tracker.status) {
        0 -> R.string.tracker_disabled to R.color.md_theme_onSurfaceVariant
        1 -> R.string.tracker_not_contacted to R.color.md_theme_onSurfaceVariant
        2 -> R.string.tracker_working to R.color.colorOk
        3 -> R.string.tracker_updating to R.color.md_theme_primary
        4 -> R.string.tracker_not_working to R.color.md_theme_error
        else -> R.string.tracker_not_contacted to R.color.md_theme_onSurfaceVariant
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Tracker>() {
            override fun areItemsTheSame(oldItem: Tracker, newItem: Tracker) =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: Tracker, newItem: Tracker) = oldItem == newItem
        }
    }
}
