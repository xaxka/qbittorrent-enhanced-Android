package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.databinding.ItemWebSeedsListBinding
import io.github.xixka.qbittorrent.model.WebSeed

/**
 * Web seeds list, qBitController TorrentWebSeedsTab parity: one URL per
 * card; selected rows shown as activated cards.
 */
class WebSeedsAdapter(
    private val isSelected: (WebSeed) -> Boolean,
    private val onClick: (WebSeed) -> Unit,
    private val onLongClick: (WebSeed) -> Unit,
) : ListAdapter<WebSeed, WebSeedsAdapter.ViewHolder>(DIFF) {

    class ViewHolder(
        val binding: ItemWebSeedsListBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemWebSeedsListBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        bind(holder.binding, getItem(position))

    private fun bind(binding: ItemWebSeedsListBinding, webSeed: WebSeed) {
        binding.url.text = webSeed.url
        binding.card.isActivated = isSelected(webSeed)
        binding.card.setOnClickListener { onClick(webSeed) }
        binding.card.setOnLongClickListener {
            onLongClick(webSeed)
            true
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WebSeed>() {
            override fun areItemsTheSame(oldItem: WebSeed, newItem: WebSeed) =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: WebSeed, newItem: WebSeed) = oldItem == newItem
        }
    }
}
