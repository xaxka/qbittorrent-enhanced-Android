package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.DialogPeerDetailsBinding
import io.github.xixka.qbittorrent.databinding.FragmentPeersBinding
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.util.Format
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Peers tab, qBC TorrentPeersTab parity: peer cards, tap = details dialog,
 * long-press = selection mode (ban / select all / inverse), toolbar Add
 * Peers (hosted by the activity). The list lives in its own ViewModel that
 * polls every interval while this tab is the visible page — the counts
 * update without the old 3-second tab-switch lag.
 */
class PeersFragment : Fragment() {

    private var _binding: FragmentPeersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailPeersViewModel
        get() = (requireActivity() as DetailActivity).peersViewModel

    private val selected = LinkedHashSet<String>()
    private var actionMode: androidx.appcompat.view.ActionMode? = null

    private lateinit var adapter: PeersAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPeersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PeersAdapter(
            selected = selected,
            onClick = ::onPeerClick,
            onLongClick = ::onPeerLongClick,
        )
        binding.peerList.layoutManager = LinearLayoutManager(requireContext())
        binding.peerList.adapter = adapter
        binding.peerList.setEmptyView(binding.emptyViewPeerList)
        binding.peerList.setLoadingView(null)

        binding.peersRefresh.setOnRefreshListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.peers.collect { peers ->
                        if (peers != null) {
                            selected.retainAll { endpoint -> peers.any { it.endpoint == endpoint } }
                            adapter.submitList(peers)
                            if (selected.isEmpty()) actionMode?.finish()
                        }
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { binding.peersRefresh.isRefreshing = it }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setScreenActive(true)
    }

    override fun onPause() {
        viewModel.setScreenActive(false)
        super.onPause()
    }

    private fun onPeerClick(peer: Peer) {
        if (selected.isNotEmpty()) {
            togglePeer(peer.endpoint)
        } else {
            showPeerDetailsDialog(peer)
        }
    }

    private fun onPeerLongClick(peer: Peer) {
        togglePeer(peer.endpoint)
    }

    private fun togglePeer(endpoint: String) {
        if (!selected.add(endpoint)) selected.remove(endpoint)
        adapter.notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        if (selected.isEmpty()) {
            actionMode?.finish()
            return
        }
        if (actionMode == null) {
            actionMode = (requireActivity() as AppCompatActivity)
                .startSupportActionMode(actionModeCallback)
        }
        actionMode?.title = getString(R.string.selected_count, selected.size)
    }

    private val actionModeCallback = object : androidx.appcompat.view.ActionMode.Callback {
        override fun onCreateActionMode(
            mode: androidx.appcompat.view.ActionMode,
            menu: Menu,
        ): Boolean {
            mode.menuInflater.inflate(R.menu.torrent_detail_peers_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu) = false

        override fun onActionItemClicked(
            mode: androidx.appcompat.view.ActionMode,
            item: MenuItem,
        ): Boolean = when (item.itemId) {
            R.id.ban_peers_menu -> {
                confirmBan(selected.toList())
                true
            }

            R.id.select_all_peers_menu -> {
                adapter.currentList.forEach { selected.add(it.endpoint) }
                adapter.notifyDataSetChanged()
                onSelectionChanged()
                true
            }

            R.id.select_inverse_peers_menu -> {
                val old = selected.toSet()
                selected.clear()
                adapter.currentList.forEach {
                    if (it.endpoint !in old) selected.add(it.endpoint)
                }
                adapter.notifyDataSetChanged()
                onSelectionChanged()
                true
            }

            else -> false
        }

        override fun onDestroyActionMode(mode: androidx.appcompat.view.ActionMode) {
            selected.clear()
            adapter.notifyDataSetChanged()
            actionMode = null
        }
    }

    private fun confirmBan(endpoints: List<String>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getQuantityString(R.plurals.torrent_peers_ban_title, endpoints.size, endpoints.size))
            .setMessage(resources.getQuantityString(R.plurals.torrent_peers_ban_desc, endpoints.size, endpoints.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.banPeers(endpoints)
                selected.clear()
                actionMode?.finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** qBC PeerDetailsDialog parity: overview / transfer / flags / files. */
    private fun showPeerDetailsDialog(peer: Peer) {
        val rowsBinding = DialogPeerDetailsBinding.inflate(layoutInflater)
        val container = rowsBinding.peerDetailsRows

        fun addRow(labelRes: Int, value: String) {
            val row = layoutInflater.inflate(R.layout.item_detail_param, container, false) as LinearLayout
            row.findViewById<TextView>(R.id.param_label).setText(labelRes)
            row.findViewById<TextView>(R.id.param_value).text = value
            container.addView(row)
        }

        addRow(
            R.string.torrent_peers_details_country,
            peer.country.ifBlank { peer.countryCode.ifBlank { "—" } },
        )
        addRow(R.string.torrent_peers_details_connection, peer.connectionStatus.ifBlank { "—" })
        addRow(R.string.torrent_peers_details_client, peer.client.ifBlank { "—" })
        addRow(
            R.string.torrent_peers_details_progress,
            String.format(Locale.ROOT, "%.1f%%", peer.progressFraction * 100),
        )
        addRow(R.string.torrent_peers_details_download_speed, Format.speed(peer.downSpeed))
        addRow(R.string.torrent_peers_details_upload_speed, Format.speed(peer.upSpeed))
        addRow(R.string.torrent_peers_details_downloaded, Format.size(peer.downloaded))
        addRow(R.string.torrent_peers_details_uploaded, Format.size(peer.uploaded))
        addRow(
            R.string.torrent_peers_details_relevance,
            String.format(Locale.ROOT, "%.1f%%", peer.relevance * 100),
        )

        // flag legend (qBC flag → description mapping)
        peer.flags.forEach { flag ->
            val resId = flagLabelRes(flag) ?: return@forEach
            val row = layoutInflater.inflate(R.layout.item_detail_param, container, false) as LinearLayout
            row.findViewById<TextView>(R.id.param_label).setText(resId)
            row.findViewById<TextView>(R.id.param_value).text = flag.toString()
            container.addView(row)
        }

        addRow(R.string.torrent_peers_details_section_files, peer.files.ifBlank { "—" })

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(peer.endpoint)
            .setView(rowsBinding.root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun flagLabelRes(flag: Char): Int? = when (flag) {
        'd' -> R.string.torrent_peers_flag_interested_local_choked_peer
        'D' -> R.string.torrent_peers_flag_interested_local_unchoked_peer
        'u' -> R.string.torrent_peers_flag_interested_peer_choked_local
        'U' -> R.string.torrent_peers_flag_interested_peer_unchoked_local
        'K' -> R.string.torrent_peers_flag_not_interested_local_unchoked_peer
        '?' -> R.string.torrent_peers_flag_not_interested_peer_unchoked_local
        'O' -> R.string.torrent_peers_flag_optimistic_unchoke
        'S' -> R.string.torrent_peers_flag_peer_snubbed
        'I' -> R.string.torrent_peers_flag_incoming_connection
        'H' -> R.string.torrent_peers_flag_peer_from_dht
        'X' -> R.string.torrent_peers_flag_peer_from_pex
        'L' -> R.string.torrent_peers_flag_peer_from_lsd
        'E' -> R.string.torrent_peers_flag_encrypted_traffic
        'e' -> R.string.torrent_peers_flag_encrypted_handshake
        'P' -> R.string.torrent_peers_flag_utp
        else -> null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
