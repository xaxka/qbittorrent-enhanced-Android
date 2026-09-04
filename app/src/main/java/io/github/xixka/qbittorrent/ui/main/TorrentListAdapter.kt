package io.github.xixka.qbittorrent.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemTorrentListBinding
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.TorrentStates

/**
 * LibreTorrent-style torrent list card: tonal pause/play button, name,
 * linear progress, status + speed line, counters + peers, error chip.
 */
class TorrentListAdapter(
    private val onClick: (TorrentInfo) -> Unit,
    private val onTogglePause: (TorrentInfo, Boolean) -> Unit,
    private val onLongClick: (TorrentInfo) -> Unit,
) : ListAdapter<TorrentInfo, TorrentListAdapter.ViewHolder>(DIFF) {

    private val selected = HashSet<String>()

    fun isSelected(hash: String) = hash in selected
    fun selectedHashes() = selected.toList()
    fun selectedCount() = selected.size

    fun toggleSelection(hash: String): Int {
        if (!selected.add(hash)) selected.remove(hash)
        notifyDataSetChanged()
        return selected.size
    }

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }

    fun selectAll(all: List<TorrentInfo>) {
        selected.clear()
        all.forEach { selected.add(it.hash) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTorrentListBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTorrentListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(t: TorrentInfo) {
            binding.name.text = t.name
            binding.progress.progress = (t.progress * 100).toInt()
            binding.status.text = binding.root.context.getString(TorrentStates.labelRes(t.state))

            binding.downloadUploadSpeed.text = buildString {
                append("↓ ${Format.speed(t.dlSpeed)} | ↑ ${Format.speed(t.upSpeed)}")
            }
            binding.downloadCounter.text = buildString {
                append("${Format.size(t.completed)} / ${Format.size(t.size)}")
                if (t.progress < 1.0 && t.eta in 1..8639999) append(" • ${Format.duration(t.eta)}")
            }
            binding.peers.text = "${t.numSeeds}/${t.numLeechsTotal}"

            // TorrentInfo.isPaused matches the API state case-insensitively
            // ("pausedDL" / "stoppedUP" / …). A previous case-sensitive
            // startsWith("pauseddl") here never matched the real API values,
            // so the button always believed the torrent was running: tapping
            // it on a paused torrent re-sent pause and it could never resume.
            (binding.pauseButton as MaterialButton).isChecked = !t.isPaused
            binding.pauseButton.setOnClickListener {
                onTogglePause(t, t.isPaused)
            }

            val error = t.state.startsWith("error") || t.state.startsWith("missing")
            binding.errorContainer.visibility = if (error) android.view.View.VISIBLE else android.view.View.GONE
            if (error) {
                binding.error.text = if (t.state.startsWith("missing")) {
                    binding.root.context.getString(R.string.state_missing_files)
                } else {
                    binding.root.context.getString(R.string.state_error)
                }
            }

            binding.card.isChecked = t.hash in selected
            binding.card.setOnClickListener {
                if (selected.isNotEmpty()) {
                    toggleSelection(t.hash)
                    onLongClick(t)
                    binding.card.isChecked = t.hash in selected
                } else {
                    onClick(t)
                }
            }
            binding.card.setOnLongClickListener {
                toggleSelection(t.hash)
                onLongClick(t)
                binding.card.isChecked = t.hash in selected
                true
            }
        }
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
