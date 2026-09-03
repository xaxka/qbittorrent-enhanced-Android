package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemTorrentDownloadableFileBinding
import io.github.xixka.qbittorrent.model.TorrentFile
import io.github.xixka.qbittorrent.util.Format

/**
 * File list, ported from LibreTorrent's FileListAdapter (GPL-3.0):
 * selectable rows with a folder/file icon, name, path and size.
 */
class FilesAdapter(
    private val onSelect: (TorrentFile) -> Unit,
    private val onClick: (TorrentFile) -> Unit,
) : ListAdapter<TorrentFile, FilesAdapter.ViewHolder>(DIFF) {

    private val selected = HashSet<Int>()

    fun isSelected(index: Int) = index in selected

    fun toggleSelection(index: Int) {
        if (!selected.add(index)) selected.remove(index)
    }

    fun selectAll() {
        selected.clear()
        currentList.forEach { selected.add(it.index) }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }

    fun selectedIndexes(): List<Int> = selected.toList()

    fun selectedCount() = selected.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTorrentDownloadableFileBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = getItem(position)
        holder.bind(file)
    }

    inner class ViewHolder(
        private val binding: ItemTorrentDownloadableFileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: TorrentFile) {
            binding.name.text = file.name.substringAfterLast('/')
            binding.path.visibility = if (file.name.contains('/')) {
                binding.path.text = file.name.substringBeforeLast('/') + "/"
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            binding.size.text = Format.size(file.size)
            val isFolder = file.name.endsWith("/")
            binding.icon.setImageResource(
                if (isFolder) R.drawable.ic_folder_24px else R.drawable.ic_file_24px,
            )
            binding.selected.isChecked = file.index in selected

            binding.card.setOnClickListener {
                if (selected.isNotEmpty()) {
                    onSelect(file)
                    binding.selected.isChecked = file.index in selected
                } else {
                    onClick(file)
                }
            }
            binding.card.setOnLongClickListener {
                onSelect(file)
                binding.selected.isChecked = file.index in selected
                true
            }
            binding.selected.setOnClickListener {
                onSelect(file)
                binding.selected.isChecked = file.index in selected
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TorrentFile>() {
            override fun areItemsTheSame(oldItem: TorrentFile, newItem: TorrentFile) =
                oldItem.index == newItem.index

            override fun areContentsTheSame(oldItem: TorrentFile, newItem: TorrentFile) =
                oldItem == newItem
        }
    }
}
