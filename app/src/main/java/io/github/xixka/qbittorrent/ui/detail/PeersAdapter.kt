package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemQbcPeerBinding
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.util.Format
import java.util.Locale

/**
 * Peers list, qBC PeerItem parity: card with ip:port + client, progress /
 * download / upload stat columns and the raw connection flags.
 */
class PeersAdapter(
    private val selected: Set<String>,
    private val onClick: (Peer) -> Unit,
    private val onLongClick: (Peer) -> Unit,
) : ListAdapter<Peer, PeersAdapter.ViewHolder>(DIFF) {

    class ViewHolder(private val binding: ItemQbcPeerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            peer: Peer,
            isSelected: Boolean,
            onClick: (Peer) -> Unit,
            onLongClick: (Peer) -> Unit,
        ) {
            binding.peerEndpoint.text = peer.endpoint
            binding.peerClient.text = peer.client
            // qBC: localized country NAME when the engine reports the code
            if (peer.countryCode.isNotBlank()) {
                binding.peerCountryCode.visibility = android.view.View.VISIBLE
                binding.peerCountryCode.text = countryName(peer.countryCode)
            } else {
                binding.peerCountryCode.visibility = android.view.View.GONE
            }
            // qBC card row: "Connection: BT/µTP/Web" under the header
            if (peer.connectionStatus.isNotBlank()) {
                binding.peerConnection.visibility = android.view.View.VISIBLE
                binding.peerConnection.text = binding.root.context.getString(
                    R.string.peer_connection_fmt, peer.connectionStatus,
                )
            } else {
                binding.peerConnection.visibility = android.view.View.GONE
            }

            binding.peerProgress.text = String.format(
                Locale.ROOT,
                "%.1f%%",
                (peer.progressFraction * 100).coerceIn(0.0, 100.0),
            )
            binding.peerDownloadSpeed.text = Format.speed(peer.downSpeed)
            binding.peerUploadSpeed.text = Format.speed(peer.upSpeed)

            if (peer.flags.isBlank()) {
                binding.peerFlags.visibility = android.view.View.GONE
            } else {
                binding.peerFlags.visibility = android.view.View.VISIBLE
                binding.peerFlags.text = binding.root.context.getString(
                    R.string.torrent_peers_flags, peer.flags,
                )
            }

            binding.peerCard.setOnClickListener { onClick(peer) }
            binding.peerCard.setOnLongClickListener {
                onLongClick(peer)
                true
            }
            // qBC: selected cards get the secondaryContainer background
            binding.peerCard.setCardBackgroundColor(
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
        ViewHolder(ItemQbcPeerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), getItem(position).endpoint in selected, onClick, onLongClick)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Peer>() {
            override fun areItemsTheSame(oldItem: Peer, newItem: Peer) =
                oldItem.endpoint == newItem.endpoint

            override fun areContentsTheSame(oldItem: Peer, newItem: Peer) = oldItem == newItem
        }
    }
}

/**
 * qBC: localized country name from the engine's ISO-3166 alpha-2 code.
 * Falls back to the raw code when the locale has no entry for it.
 */
private fun countryName(code: String): String {
    val name = Locale("", code).displayCountry
    return if (name.equals(code, ignoreCase = true)) code else name
}
