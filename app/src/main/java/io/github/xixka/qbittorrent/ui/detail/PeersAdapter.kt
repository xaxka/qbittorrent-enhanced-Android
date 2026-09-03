package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.databinding.ItemPeerBinding
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.util.Format

class PeersAdapter : ListAdapter<Peer, PeersAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemPeerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPeerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val peer = getItem(position)
        val binding = holder.binding

        binding.peerEndpoint.text = peer.endpoint
        binding.peerClient.text = peer.client
        binding.peerCountry.text = peer.country.orEmpty()
        binding.peerCountry.visibility =
            if (peer.country.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.peerProgress.max = 1000
        binding.peerProgress.progress = (peer.progressFraction * 1000).toInt()
        binding.peerPercent.text = Format.progress(peer.progressFraction)
        binding.peerSpeeds.text = "↓ ${Format.speed(peer.downSpeed)}  ↑ ${Format.speed(peer.upSpeed)}"
        binding.peerFlags.text = peer.flags
        binding.peerFlags.visibility =
            if (peer.flags.isBlank()) View.GONE else View.VISIBLE
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Peer>() {
            override fun areItemsTheSame(oldItem: Peer, newItem: Peer) =
                oldItem.endpoint == newItem.endpoint

            override fun areContentsTheSame(oldItem: Peer, newItem: Peer) =
                oldItem == newItem
        }
    }
}
