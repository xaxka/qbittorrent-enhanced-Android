package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import io.github.xixka.qbittorrent.databinding.ItemWebSeedsListBinding
import io.github.xixka.qbittorrent.model.WebSeed

/**
 * Web seeds list, qBC TorrentWebSeedsTab parity: one URL per elevated
 * card; selected rows get the secondaryContainer background.
 */
class WebSeedsAdapter(
    private val selected: Set<String>,
    private val onClick: (WebSeed) -> Unit,
    private val onLongClick: (WebSeed) -> Unit,
) : ListAdapter<WebSeed, WebSeedsAdapter.ViewHolder>(DIFF) {

    class ViewHolder(private val binding: ItemWebSeedsListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            webSeed: WebSeed,
            isSelected: Boolean,
            onClick: (WebSeed) -> Unit,
            onLongClick: (WebSeed) -> Unit,
        ) {
            binding.url.text = webSeed.url
            binding.card.setOnClickListener { onClick(webSeed) }
            binding.card.setOnLongClickListener {
                onLongClick(webSeed)
                true
            }
            binding.card.setCardBackgroundColor(
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
        ViewHolder(ItemWebSeedsListBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), getItem(position).url in selected, onClick, onLongClick)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WebSeed>() {
            override fun areItemsTheSame(oldItem: WebSeed, newItem: WebSeed) =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: WebSeed, newItem: WebSeed) = oldItem == newItem
        }
    }
}
