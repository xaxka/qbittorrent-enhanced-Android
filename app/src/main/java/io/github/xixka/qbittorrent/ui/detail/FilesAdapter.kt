package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemFileBinding
import io.github.xixka.qbittorrent.model.TorrentFile
import io.github.xixka.qbittorrent.util.Format

class FilesAdapter(
    private val onClick: (TorrentFile) -> Unit,
) : ListAdapter<TorrentFile, FilesAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = getItem(position)
        val binding = holder.binding

        binding.fileName.text = file.name
        binding.fileSize.text = Format.size(file.size)
        binding.fileProgress.max = 1000
        binding.fileProgress.progress = (file.progressFraction * 1000).toInt()
        binding.filePercent.text = Format.progress(file.progressFraction)
        binding.filePriority.text = priorityLabel(binding.root.context, file.priority)
        if (file.isSkipped) {
            binding.fileName.setTextColor(
                ContextCompat.getColor(binding.root.context, R.color.md_theme_onSurfaceVariant)
            )
        } else {
            binding.fileName.setTextColor(
                ContextCompat.getColor(binding.root.context, R.color.md_theme_onSurface)
            )
        }

        binding.root.setOnClickListener { onClick(file) }
    }

    private fun priorityLabel(context: android.content.Context, priority: Int): String = when {
        priority <= 0 -> context.getString(R.string.priority_skip)
        priority in 2..5 -> context.getString(R.string.priority_mixed)
        priority == 6 -> context.getString(R.string.priority_high)
        priority >= 7 -> context.getString(R.string.priority_maximum)
        else -> context.getString(R.string.priority_normal)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TorrentFile>() {
            override fun areItemsTheSame(oldItem: TorrentFile, newItem: TorrentFile) =
                oldItem.index == newItem.index && oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: TorrentFile, newItem: TorrentFile) =
                oldItem == newItem
        }
    }
}
