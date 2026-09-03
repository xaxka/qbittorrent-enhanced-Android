package io.github.xixka.qbittorrent.ui.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemTorrentBinding
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.TorrentStates

/**
 * Torrent cards in LibreTorrent style: name, progress bar, status line with
 * speeds, counters, and an inline pause/resume button.
 */
class TorrentListAdapter(
    private val onClick: (TorrentInfo) -> Unit,
    private val onTogglePause: (TorrentInfo, Boolean) -> Unit,
    private val onLongClick: (TorrentInfo) -> Unit,
) : ListAdapter<TorrentInfo, TorrentListAdapter.ViewHolder>(DIFF) {

    private val selected = HashSet<String>()

    fun toggleSelection(hash: String) {
        if (!selected.add(hash)) selected.remove(hash)
        currentList.indexOfFirst { it.hash == hash }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it)
        }
    }

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }

    fun selectedHashes(): List<String> = selected.toList()

    val selectedCount: () -> Int = { selected.size }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTorrentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTorrentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(t: TorrentInfo) {
            val context = binding.root.context
            val paused = t.state.endsWith("pauseddl") || t.state.endsWith("pausedup") ||
                t.state == "pausedup" || t.state == "pauseddl"

            binding.torrentName.text = t.name
            binding.progress.max = 1000
            binding.progress.progress = (t.progress.coerceIn(0.0, 1.0) * 1000).toInt()
            binding.progressPercent.text = Format.progress(t.progress)
            binding.progressPercent.isVisible(t.progress >= 1.0)

            binding.state.text = context.getString(TorrentStates.labelRes(t.state))
            binding.sizes.text = buildString {
                append(Format.size(t.completed))
                append(" / ")
                append(Format.size(t.size))
                if (t.eta in 1 until 8640000) {
                    append("  •  ")
                    append(context.getString(R.string.eta_remaining))
                    append(" ")
                    append(Format.eta(t.eta))
                }
            }
            binding.speeds.isVisible(!paused)
            binding.speeds.text = "↓ ${Format.speed(t.dlSpeed)}   ↑ ${Format.speed(t.upSpeed)}"
            binding.counters.text = buildString {
                append(context.getString(R.string.seeds))
                append(" ")
                append(t.numSeeds)
                append(" (")
                append(t.numSeedsTotal)
                append(")   ")
                append(context.getString(R.string.peers))
                append(" ")
                append(t.numLeechs)
                append(" (")
                append(t.numLeechsTotal)
                append(")")
            }
            binding.ratio.text = buildString {
                append(context.getString(R.string.ratio))
                append(" ")
                append(Format.ratio(t.ratio))
            }

            binding.pauseButton.setIconResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
            binding.pauseButton.setOnClickListener {
                onTogglePause(t, paused)
            }
            binding.progress.setIndicatorColor(
                ContextCompat.getColor(
                    context,
                    if (t.progress >= 1.0) R.color.torrent_done else R.color.torrent_active,
                )
            )

            val isSel = t.hash in selected
            binding.root.isChecked = isSel

            binding.root.setOnClickListener {
                if (selected.isNotEmpty()) {
                    onLongClick(t)
                } else {
                    onClick(t)
                }
            }
            binding.root.setOnLongClickListener {
                onLongClick(t)
                true
            }
        }
    }

    private fun View.isVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TorrentInfo>() {
            override fun areItemsTheSame(oldItem: TorrentInfo, newItem: TorrentInfo) =
                oldItem.hash == newItem.hash

            override fun areContentsTheSame(oldItem: TorrentInfo, newItem: TorrentInfo) =
                oldItem == newItem
        }
    }
}
