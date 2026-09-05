package io.github.xixka.qbittorrent.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import com.google.android.material.color.MaterialColors
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ItemQbcPeerBinding
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.util.Format
import java.util.Locale

/**
 * Peers list, qBC PeerItem parity: card with a country flag image +
 * ip:port + client header, four stat columns (progress / download /
 * upload / connection) and the raw connection-flags banner.
 *
 * Flags come from the engine's own WebUI static files
 * (`images/flags/{cc}.svg`, the exact URL qBitController builds), decoded
 * by [coil.decode.SvgDecoder] through the [flagLoader] the tab owns; the
 * URL itself is produced by [flagUrlOf] from the active server config.
 */
class PeersAdapter(
    private val selected: Set<String>,
    private val flagLoader: ImageLoader,
    private val flagUrlOf: (String) -> String,
    private val onClick: (Peer) -> Unit,
    private val onLongClick: (Peer) -> Unit,
) : ListAdapter<Peer, PeersAdapter.ViewHolder>(DIFF) {

    class ViewHolder(
        private val binding: ItemQbcPeerBinding,
        private val flagLoader: ImageLoader,
        private val flagUrlOf: (String) -> String,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            peer: Peer,
            isSelected: Boolean,
            onClick: (Peer) -> Unit,
            onLongClick: (Peer) -> Unit,
        ) {
            binding.peerEndpoint.text = peer.endpoint
            binding.peerClient.text = peer.client

            // qBC PeerItem: 24dp flag image in front of the endpoint, loaded
            // from the engine's images/flags/{cc}.svg. The localized country
            // name stays as the accessibility description.
            val code = peer.countryCode.trim()
            if (code.isNotBlank()) {
                binding.peerFlag.visibility = View.VISIBLE
                binding.peerFlag.contentDescription = countryName(code)
                binding.peerFlag.load(flagUrlOf(code.lowercase(Locale.ROOT)), flagLoader)
            } else {
                binding.peerFlag.visibility = View.GONE
                binding.peerFlag.setImageDrawable(null)
            }

            binding.peerConnection.text = peer.connectionStatus

            binding.peerProgress.text = String.format(
                Locale.ROOT,
                "%.1f%%",
                (peer.progressFraction * 100).coerceIn(0.0, 100.0),
            )
            binding.peerDownloadSpeed.text = Format.speed(peer.downSpeed)
            binding.peerUploadSpeed.text = Format.speed(peer.upSpeed)

            // qBC flags banner: raw flags joined by spaces inside a rounded
            // secondaryContainer surface with a flag icon.
            if (peer.flags.isBlank()) {
                binding.peerFlagsBubble.visibility = View.GONE
            } else {
                binding.peerFlagsBubble.visibility = View.VISIBLE
                binding.peerFlags.text = peer.flags
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
        ViewHolder(
            ItemQbcPeerBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            flagLoader,
            flagUrlOf,
        )

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
