package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.MarginLayoutParamsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemQbcFileBinding
import io.github.xixka.qbittorrent.model.TorrentFileNode
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.TorrentStateColors
import java.util.Locale

/**
 * Torrent content tree, qBC TorrentFilesTab parity: folder rows expand in
 * place, priority colors the per-row progress bar, long-press starts the
 * multi-select action mode.
 */
class FilesTreeAdapter(
    private val selected: Set<String>,
    private val expanded: Set<String>,
    private val onClick: (TorrentFileNode) -> Unit,
    private val onLongClick: (TorrentFileNode) -> Unit,
    private val onToggleExpand: (TorrentFileNode) -> Unit,
) : ListAdapter<TorrentFileNode, FilesTreeAdapter.ViewHolder>(DIFF) {

    class ViewHolder(private val binding: ItemQbcFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            node: TorrentFileNode,
            isSelected: Boolean,
            isExpanded: Boolean,
            onClick: (TorrentFileNode) -> Unit,
            onLongClick: (TorrentFileNode) -> Unit,
            onToggleExpand: (TorrentFileNode) -> Unit,
        ) {
            val context = binding.root.context

            // folder-depth indentation (qBC: (level - 1) * 12dp)
            val density = context.resources.displayMetrics.density
            (binding.fileCard.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                MarginLayoutParamsCompat.setMarginStart(
                    lp,
                    ((node.level - 1).coerceAtLeast(0) * 12 * density).toInt(),
                )
                binding.fileCard.layoutParams = lp
            }

            binding.fileName.text = node.name

            if (node is TorrentFileNode.Folder) {
                binding.expandButton.visibility = View.VISIBLE
                binding.expandButton.rotation = if (isExpanded) 0f else -90f
                binding.expandButton.setOnClickListener { onToggleExpand(node) }
                binding.typeIcon.setImageResource(R.drawable.ic_folder_24px)
            } else {
                binding.expandButton.visibility = View.INVISIBLE
                binding.expandButton.rotation = 0f
                binding.typeIcon.setImageResource(R.drawable.ic_file_24px)
            }

            // priority-colored progress bar (qBC filePriority colors),
            // track = same color at 38% alpha (shared TorrentStateColors spec)
            val (priorityColor, priorityText) = when (node.priority) {
                0 -> R.color.colorFilePrioritySkipped to
                    context.getString(R.string.torrent_files_priority_do_not_download)
                1 -> R.color.colorFilePriorityNormal to
                    context.getString(R.string.torrent_files_priority_normal)
                6 -> R.color.colorFilePriorityHigh to
                    context.getString(R.string.torrent_files_priority_high)
                7 -> R.color.colorFilePriorityMaximum to
                    context.getString(R.string.torrent_files_priority_maximum)
                else -> R.color.colorFilePriorityMixed to
                    context.getString(R.string.torrent_files_priority_mixed)
            }
            val color = ContextCompat.getColor(context, priorityColor)
            binding.fileProgress.apply {
                setIndicatorColor(color)
                trackColor = TorrentStateColors.translucentTrack(color)
                setProgress((node.progress * 100).toInt().coerceIn(0, 100))
            }

            val progressPercent = if (node.progress >= 1.0) "100"
            else String.format(Locale.ROOT, "%.1f", node.progress * 100)
            binding.fileDetails.text = context.getString(
                R.string.torrent_files_details_format,
                priorityText,
                Format.size(node.downloadedSize),
                Format.size(node.size),
                progressPercent,
            )

            binding.fileCard.setOnClickListener { onClick(node) }
            binding.fileCard.setOnLongClickListener {
                onLongClick(node)
                true
            }
            // qBC: selected rows get the secondaryContainer background
            binding.fileCard.setCardBackgroundColor(
                if (isSelected) {
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
        ViewHolder(ItemQbcFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = getItem(position)
        holder.bind(
            node = node,
            isSelected = node.path in selected,
            isExpanded = node.path in expanded,
            onClick = onClick,
            onLongClick = onLongClick,
            onToggleExpand = onToggleExpand,
        )
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TorrentFileNode>() {
            override fun areItemsTheSame(oldItem: TorrentFileNode, newItem: TorrentFileNode) =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: TorrentFileNode, newItem: TorrentFileNode) =
                oldItem.path == newItem.path &&
                    oldItem.progress == newItem.progress &&
                    oldItem.priority == newItem.priority &&
                    oldItem.size == newItem.size
        }
    }
}
