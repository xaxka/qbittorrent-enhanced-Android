package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.MarginLayoutParamsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
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
    private val onPriorityToggle: (TorrentFileNode, Int) -> Unit,
    private val onClick: (TorrentFileNode) -> Unit,
    private val onLongClick: (TorrentFileNode) -> Unit,
) : ListAdapter<TorrentFileNode, FilesTreeAdapter.ViewHolder>(DIFF) {

    class ViewHolder(
        private val binding: ItemQbcFileBinding,
        private val onPriorityToggle: (TorrentFileNode, Int) -> Unit,
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            node: TorrentFileNode,
            isSelected: Boolean,
            onClick: (TorrentFileNode) -> Unit,
            onLongClick: (TorrentFileNode) -> Unit,
        ) {
            val context = binding.root.context

            // Download toggle (LibreTorrent checkbox parity): checked = the
            // file/folder downloads; a folder with mixed priorities shows
            // the indeterminate state. Tapping sends NORMAL for checked and
            // DO_NOT_DOWNLOAD (0) for unchecked — for folders the engine
            // applies the priority to every descendant file.
            binding.fileCheck.setOnCheckedChangeListener(null)
            binding.fileCheck.checkedState = when (node.priority) {
                null -> MaterialCheckBox.STATE_INDETERMINATE
                0 -> MaterialCheckBox.STATE_UNCHECKED
                else -> MaterialCheckBox.STATE_CHECKED
            }
            binding.fileCheck.setOnCheckedChangeListener { _, _ ->
                val target = if (
                    binding.fileCheck.checkedState == MaterialCheckBox.STATE_CHECKED
                ) 1 else 0
                onPriorityToggle(node, target)
            }

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
                binding.typeIcon.setImageResource(R.drawable.ic_folder_24px)
            } else {
                binding.typeIcon.setImageResource(iconFor(node.name))
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
        ViewHolder(
            ItemQbcFileBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onPriorityToggle,
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = getItem(position)
        holder.bind(
            node = node,
            isSelected = node.path in selected,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }

    /** Format-based row icon, LibreTorrent FileTypeUtils parity: video,
     *  audio, image, archive, document and subtitle extensions get their
     *  own glyph; everything else falls back to the plain file icon. */
    private fun iconFor(name: String): Int {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "3gp", "avi", "divx", "flv", "m4v", "mkv", "mov", "mp4", "mpeg",
            "mpg", "ogm", "ogv", "rm", "rmvb", "vob", "webm", "wmv", "yuv",
            "ts", "m2ts" -> R.drawable.ic_movie_24px
            "aac", "ac3", "aiff", "flac", "m4a", "m4b", "m4p", "mid", "mp1",
            "mp2", "mp3", "mpc", "ogg", "opus", "ra", "ram", "wav", "wma" ->
                R.drawable.ic_music_note_24px
            "bmp", "gif", "ico", "jpeg", "jpg", "png", "psd", "raw", "svg",
            "tif", "tiff", "webp", "heic" -> R.drawable.ic_image_24px
            "7z", "bz2", "cab", "gz", "iso", "rar", "tar", "xz", "zip",
            "zst" -> R.drawable.ic_archive_24px
            "csv", "doc", "docx", "htm", "html", "md", "nfo", "odp", "ods",
            "odt", "pdf", "ppt", "pptx", "rtf", "txt", "xls", "xlsx" ->
                R.drawable.ic_description_24px
            "ass", "idx", "srt", "ssa", "sub", "vtt" -> R.drawable.ic_subtitles_24px
            else -> R.drawable.ic_file_24px
        }
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
