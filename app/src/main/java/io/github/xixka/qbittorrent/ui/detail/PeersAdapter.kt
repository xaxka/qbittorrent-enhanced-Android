package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.xixka.qbittorrent.databinding.ItemPeersListBinding
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.util.Format
import java.util.Locale

/**
 * Peers list, ported from LibreTorrent's PeerListAdapter (GPL-3.0).
 */
class PeersAdapter : ListAdapter<Peer, PeersAdapter.ViewHolder>(DIFF) {

    class ViewHolder(
        private val binding: ItemPeersListBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(peer: Peer) {
            val ctx = itemView.context
            binding.ip.text = peer.ip
            binding.port.text = ctx.getString(
                io.github.xixka.qbittorrent.R.string.peer_port_fmt, peer.port
            )
            // progress arrives 0..1 on older servers and 0..100 on qB 5.1+;
            // the tolerant fraction + clamp keeps the progress bar valid
            binding.progress.setProgress(
                (peer.progressFraction * 100).toInt().coerceIn(0, 100),
            )
            binding.progress.max = 100
            binding.relevance.text = String.format(
                Locale.ROOT,
                "%s %.1f%%",
                ctx.getString(io.github.xixka.qbittorrent.R.string.relevance),
                peer.relevance * 100,
            )
            binding.connectionType.text = ctx.getString(
                io.github.xixka.qbittorrent.R.string.peer_connection_fmt,
                peer.connectionStatus.ifEmpty { "BT" },
            )
            binding.upDownSpeed.text =
                "↓ ${Format.speed(peer.downSpeed)} • ↑ ${Format.speed(peer.upSpeed)}"
            binding.totalDownloadUpload.text =
                "↓ ${Format.size(peer.downloaded)} • ↑ ${Format.size(peer.uploaded)}"
            binding.client.text = ctx.getString(
                io.github.xixka.qbittorrent.R.string.peer_client_fmt, peer.client
            )
            // qBitController parity: country + connection flags
            binding.peerCountry.text = peer.country.ifBlank { peer.countryCode }
            binding.peerFlags.text = if (peer.flags.isBlank()) "" else
                ctx.getString(io.github.xixka.qbittorrent.R.string.peer_flags_fmt, peer.flags)
            binding.progress.visibility = View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemPeersListBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Peer>() {
            override fun areItemsTheSame(oldItem: Peer, newItem: Peer) =
                oldItem.ip == newItem.ip && oldItem.port == newItem.port

            override fun areContentsTheSame(oldItem: Peer, newItem: Peer) = oldItem == newItem
        }
    }
}
