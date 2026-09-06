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
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.DialogPeerDetailsBinding
import io.github.xixka.qbittorrent.databinding.FragmentPeersBinding
import io.github.xixka.qbittorrent.model.Peer
import io.github.xixka.qbittorrent.util.Format
import okhttp3.OkHttpClient
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

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

    /** qBC: finishes the selection when the user swipes to another tab. */
    private var unregisterPageSwipe: (() -> Unit)? = null

    private lateinit var adapter: PeersAdapter

    private var flagLoader: ImageLoader? = null

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

        // qBC PeerItem: country flags are fetched from the engine's WebUI
        // static files (images/flags/{cc}.svg) and SVG-decoded by Coil.
        // Self-signed https servers reuse the trust-all policy the API
        // client already applies.
        val serverConfig = ServiceLocator.prefs(requireContext()).serverConfig()
        val flagBase = serverConfig.baseUrl()
        // build the loader once per fragment instance: each one carries its
        // own OkHttp pool/threads, and view recreations would pile them up
        val loader = flagLoader ?: ImageLoader.Builder(requireContext())
            .components { add(SvgDecoder.Factory()) }
            .apply {
                if (serverConfig.trustAllCerts) okHttpClient(trustAllOkHttpClient())
            }
            .build()
            .also { flagLoader = it }
        adapter = PeersAdapter(
            selected = selected,
            flagLoader = loader,
            flagUrlOf = { code -> "${flagBase}images/flags/$code.svg" },
            onClick = ::onPeerClick,
            onLongClick = ::onPeerLongClick,
        )
        binding.peerList.layoutManager = LinearLayoutManager(requireContext())
        binding.peerList.adapter = adapter
        binding.peerList.setEmptyView(binding.emptyViewPeerList)
        binding.peerList.setLoadingView(null)
        binding.loadingIndicator.setVisibilityAfterHide(View.INVISIBLE)

        binding.peersRefresh.setOnRefreshListener { viewModel.refresh() }

        unregisterPageSwipe = finishSelectionOnPageSwipe(DetailActivity.TAB_PEERS) {
            actionMode?.finish()
        }

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
                // qBC: indeterminate bar during the first (natural) load
                launch {
                    viewModel.isNaturalLoading.collect { loading ->
                        if (loading == true) {
                            binding.loadingIndicator.show()
                            binding.peerList.setLoading(true)
                        } else {
                            binding.loadingIndicator.hide()
                            binding.peerList.setLoading(false)
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
        // qBC: the auto-refresh loop pauses while a selection is active
        viewModel.setSelectionActive(selected.isNotEmpty())
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
            // every exit path (back key, page swipe, dialog confirm) must
            // release the poll gate, not just item taps
            viewModel.setSelectionActive(false)
        }
    }

    private fun confirmBan(endpoints: List<String>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getQuantityString(R.plurals.torrent_peers_ban_title, endpoints.size, endpoints.size))
            .setMessage(resources.getQuantityString(R.plurals.torrent_peers_ban_desc, endpoints.size, endpoints.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.banPeers(endpoints)
                selected.clear()
                actionMode?.finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** qBC PeerDetailsDialog parity: overview / transfer / flags / files,
     *  rendered with the shared qBC-InfoRow-style aligned columns. */
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
        // qBC peer-details dialog: client fingerprint decoded from the peer id
        if (peer.peerIdClient.isNotBlank()) {
            addRow(R.string.torrent_peers_details_peer_id_client, peer.peerIdClient)
        }
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

        // Shared column alignment: one common label width for the whole
        // dialog (max label text, capped at 40%), qBC InfoRow parity.
        DetailParamRows.alignLabels(container)

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
        unregisterPageSwipe?.invoke()
        unregisterPageSwipe = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        // the loader owns an OkHttp pool + dispatcher: release it with the fragment
        flagLoader?.shutdown()
        flagLoader = null
        super.onDestroy()
    }

    /** Trust-all HTTPS for flag requests on self-signed servers — the same
     *  opt-in policy QBApiClient applies to the API calls. */
    private fun trustAllOkHttpClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}
