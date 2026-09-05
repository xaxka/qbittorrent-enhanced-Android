package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemQbcTrackerBinding
import io.github.xixka.qbittorrent.model.Tracker

/**
 * Trackers list, qBC TrackerItem parity: url header, four swarm stat
 * columns and the tracker message banner. Built-in trackers (tier marker
 * "**" prefix in the url) are not selectable, matching qBC.
 */
class TrackersAdapter(
    private val selected: Set<String>,
    private val onClick: (Tracker) -> Unit,
    private val onLongClick: (Tracker) -> Unit,
) : ListAdapter<Tracker, TrackersAdapter.ViewHolder>(DIFF) {

    class ViewHolder(private val binding: ItemQbcTrackerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            tracker: Tracker,
            isSelectable: Boolean,
            isSelected: Boolean,
            onClick: (Tracker) -> Unit,
            onLongClick: (Tracker) -> Unit,
        ) {
            binding.url.text = tracker.url
            // qBC NullableIntSerializer parity: -1 = "not announced yet",
            // rendered as an em dash (the engine used to print literal -1)
            binding.statPeers.text =
                tracker.numPeers.takeIf { it >= 0 }?.toString() ?: "—"
            binding.statSeeds.text =
                tracker.numSeeds.takeIf { it >= 0 }?.toString() ?: "—"
            binding.statLeeches.text =
                tracker.numLeeches.takeIf { it >= 0 }?.toString() ?: "—"
            binding.statDownloaded.text =
                tracker.numDownloaded.takeIf { it >= 0 }?.toString() ?: "—"

            val msg = tracker.msg
            binding.message.visibility =
                if (msg.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.message.text =
                binding.root.context.getString(R.string.torrent_trackers_message, msg)

            if (isSelectable) {
                binding.card.setOnClickListener { onClick(tracker) }
                binding.card.setOnLongClickListener {
                    onLongClick(tracker)
                    true
                }
            } else {
                binding.card.setOnClickListener(null)
                binding.card.setOnLongClickListener(null)
            }

            binding.card.setCardBackgroundColor(
                if (isSelected && isSelectable) {
                    MaterialColors.getColor(
                        binding.root,
                        com.google.android.material.R.attr.colorSecondaryContainer,
                    )
                } else {
                    MaterialColors.getColor(
                        binding.root,
                        com.google.android.material.R.attr.colorSurfaceContainerLow,
                    )
                },
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemQbcTrackerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tracker = getItem(position)
        holder.bind(
            tracker = tracker,
            isSelectable = !tracker.isBuiltIn,
            isSelected = tracker.url in selected,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Tracker>() {
            override fun areItemsTheSame(oldItem: Tracker, newItem: Tracker) =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: Tracker, newItem: Tracker) = oldItem == newItem
        }
    }
}
