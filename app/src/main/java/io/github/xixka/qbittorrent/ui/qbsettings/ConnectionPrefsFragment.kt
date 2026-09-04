package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsConnectionBinding
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.bool
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.int
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.str

/**
 * Connection tab — the WebUI Options "Connection" page: listening port,
 * UPnP port forwarding, connection limits, proxy server and (Enhanced
 * edition) automatic peer bans. The proxy type is a string since
 * qBittorrent 4.6; older servers that serialize it as a number get the
 * number back.
 */
class ConnectionPrefsFragment : QBPrefsTabFragment() {

    private var _binding: FragmentQbPrefsConnectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var proxyType: DropdownField
    private lateinit var dyndnsService: DropdownField

    /** Proxy type order/indices, mapped to names and legacy numeric codes. */
    private val proxyNames = listOf("None", "HTTP", "SOCKS5", "SOCKS4")
    private val proxyLegacyCodes = listOf(0, 1, 2, 5)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        proxyType = DropdownField(
            requireContext(),
            binding.proxyTypeDropdown,
            listOf(
                getString(R.string.qbt_proxy_none),
                getString(R.string.qbt_proxy_http),
                getString(R.string.qbt_proxy_socks5),
                getString(R.string.qbt_proxy_socks4),
            ),
        )
        dyndnsService = DropdownField(
            requireContext(),
            binding.dyndnsServiceDropdown,
            listOf(
                getString(R.string.qbt_dyndns_none),
                getString(R.string.qbt_dyndns_dyndns),
                getString(R.string.qbt_dyndns_noip),
            ),
        )
    }

    override fun populate(prefs: JsonObject) {
        binding.listenPortInput.setText(int(prefs, "listen_port", 6881).toString())
        binding.upnpSwitch.isChecked = bool(prefs, "upnp", true)
        binding.maxConnecInput.setText(int(prefs, "max_connec", 500).toString())
        binding.maxConnecPerTorrentInput.setText(int(prefs, "max_connec_per_torrent", 100).toString())
        binding.maxUploadsInput.setText(int(prefs, "max_uploads", -1).toString())
        binding.maxUploadsPerTorrentInput.setText(int(prefs, "max_uploads_per_torrent", -1).toString())
        val type = if (QBPrefBindings.isNumeric(prefs, "proxy_type")) {
            // legacy numeric enum (qBittorrent < 4.6)
            when (QBPrefBindings.enumInt(prefs, "proxy_type", 0)) {
                1 -> "HTTP"
                2 -> "SOCKS5"
                5 -> "SOCKS4"
                else -> "None"
            }
        } else {
            QBPrefBindings.enumStr(prefs, "proxy_type", "None")
        }
        proxyType.select(proxyNames.indexOf(type).takeIf { it >= 0 } ?: 0)
        binding.proxyIpInput.setText(str(prefs, "proxy_ip"))
        binding.proxyPortInput.setText(int(prefs, "proxy_port", 0).toString())
        binding.proxyAuthSwitch.isChecked = bool(prefs, "proxy_auth_enabled", false)
        binding.proxyUsernameInput.setText(str(prefs, "proxy_username"))
        binding.proxyPasswordFieldInput.setText(str(prefs, "proxy_password"))
        binding.proxyBtSwitch.isChecked = bool(prefs, "proxy_bittorrent", false)
        binding.proxyPeersSwitch.isChecked = bool(prefs, "proxy_peer_connections", false)
        binding.proxyMiscSwitch.isChecked = bool(prefs, "proxy_misc", true)
        binding.banUnknownSwitch.isChecked = bool(prefs, "auto_ban_unknown_peer", false)
        binding.banBtPlayerSwitch.isChecked = bool(prefs, "auto_ban_bt_player_peer", false)
        binding.proxyHostnameLookupSwitch.isChecked = bool(prefs, "proxy_hostname_lookup", false)
        binding.proxyRssSwitch.isChecked = bool(prefs, "proxy_rss", false)
        binding.ignoreSslErrorsSwitch.isChecked = bool(prefs, "ignore_ssl_errors", false)
        binding.randomPortSwitch.isChecked = bool(prefs, "random_port", true)
        binding.networkInterfaceInput.setText(str(prefs, "current_network_interface"))
        binding.interfaceAddressInput.setText(str(prefs, "current_interface_address"))
        binding.i2pEnabledSwitch.isChecked = bool(prefs, "i2p_enabled", false)
        binding.i2pAddressInput.setText(str(prefs, "i2p_address"))
        binding.i2pPortInput.setText(int(prefs, "i2p_port", 7656).toString())
        binding.i2pMixedSwitch.isChecked = bool(prefs, "i2p_mixed_mode", false)
        binding.i2pInboundQuantityInput.setText(int(prefs, "i2p_inbound_quantity", 3).toString())
        binding.i2pOutboundQuantityInput.setText(int(prefs, "i2p_outbound_quantity", 3).toString())
        binding.i2pInboundLengthInput.setText(int(prefs, "i2p_inbound_length", 3).toString())
        binding.i2pOutboundLengthInput.setText(int(prefs, "i2p_outbound_length", 3).toString())

        // connection tuning + DynDNS
        binding.connectionSpeedInput.setText(int(prefs, "connection_speed", 0).toString())
        binding.outgoingPortsMinInput.setText(int(prefs, "outgoing_ports_min", 0).toString())
        binding.outgoingPortsMaxInput.setText(int(prefs, "outgoing_ports_max", 0).toString())
        binding.upnpLeaseInput.setText(int(prefs, "upnp_lease_duration", 0).toString())
        binding.peerTosInput.setText(int(prefs, "peer_tos", 0).toString())
        binding.dyndnsEnabledSwitch.isChecked = bool(prefs, "dyndns_enabled", false)
        dyndnsService.select(
            when (QBPrefBindings.enumInt(prefs, "dyndns_service", -1)) {
                0 -> 1 // DynDNS
                1 -> 2 // No-IP
                else -> 0 // None
            }
        )
        binding.dyndnsDomainInput.setText(str(prefs, "dyndns_domain"))
        binding.dyndnsUsernameInput.setText(str(prefs, "dyndns_username"))
        binding.dyndnsPasswordInput.setText(str(prefs, "dyndns_password"))
    }

    override fun collectValues(out: JsonObject) {
        binding.listenPortInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..65535 }?.let { out.put("listen_port", it) }
        out.put("upnp", binding.upnpSwitch.isChecked)
        binding.maxConnecInput.text?.toString()?.trim()?.toIntOrNull()
            ?.let { out.put("max_connec", it) }
        binding.maxConnecPerTorrentInput.text?.toString()?.trim()?.toIntOrNull()
            ?.let { out.put("max_connec_per_torrent", it) }
        binding.maxUploadsInput.text?.toString()?.trim()?.toIntOrNull()
            ?.let { out.put("max_uploads", it) }
        binding.maxUploadsPerTorrentInput.text?.toString()?.trim()?.toIntOrNull()
            ?.let { out.put("max_uploads_per_torrent", it) }
        val index = proxyType.selectedOr(0)
        val sendNumeric = QBPrefBindings.isNumeric(vm.raw.value, "proxy_type")
        if (sendNumeric) {
            out.put("proxy_type", proxyLegacyCodes[index])
        } else {
            out.put("proxy_type", proxyNames[index])
        }
        out.put("proxy_ip", binding.proxyIpInput.text?.toString()?.trim().orEmpty())
        binding.proxyPortInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 0..65535 }?.let { out.put("proxy_port", it) }
        out.put("proxy_auth_enabled", binding.proxyAuthSwitch.isChecked)
        out.put("proxy_username", binding.proxyUsernameInput.text?.toString().orEmpty())
        out.put("proxy_password", binding.proxyPasswordFieldInput.text?.toString().orEmpty())
        out.put("proxy_bittorrent", binding.proxyBtSwitch.isChecked)
        out.put("proxy_peer_connections", binding.proxyPeersSwitch.isChecked)
        out.put("proxy_misc", binding.proxyMiscSwitch.isChecked)
        out.put("auto_ban_unknown_peer", binding.banUnknownSwitch.isChecked)
        out.put("auto_ban_bt_player_peer", binding.banBtPlayerSwitch.isChecked)
        out.put("proxy_hostname_lookup", binding.proxyHostnameLookupSwitch.isChecked)
        out.put("proxy_rss", binding.proxyRssSwitch.isChecked)
        out.put("ignore_ssl_errors", binding.ignoreSslErrorsSwitch.isChecked)
        out.put("random_port", binding.randomPortSwitch.isChecked)
        out.put("current_network_interface", binding.networkInterfaceInput.text?.toString()?.trim().orEmpty())
        out.put("current_interface_address", binding.interfaceAddressInput.text?.toString()?.trim().orEmpty())
        out.put("i2p_enabled", binding.i2pEnabledSwitch.isChecked)
        out.put("i2p_address", binding.i2pAddressInput.text?.toString()?.trim().orEmpty())
        binding.i2pPortInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 0..65535 }?.let { out.put("i2p_port", it) }
        out.put("i2p_mixed_mode", binding.i2pMixedSwitch.isChecked)
        binding.i2pInboundQuantityInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 0..16 }?.let { out.put("i2p_inbound_quantity", it) }
        binding.i2pOutboundQuantityInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 0..16 }?.let { out.put("i2p_outbound_quantity", it) }
        binding.i2pInboundLengthInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 0..7 }?.let { out.put("i2p_inbound_length", it) }
        binding.i2pOutboundLengthInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 0..7 }?.let { out.put("i2p_outbound_length", it) }

        binding.connectionSpeedInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("connection_speed", it) }
        binding.outgoingPortsMinInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 0..65535 }?.let { out.put("outgoing_ports_min", it) }
        binding.outgoingPortsMaxInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 0..65535 }?.let { out.put("outgoing_ports_max", it) }
        binding.upnpLeaseInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("upnp_lease_duration", it) }
        binding.peerTosInput.text?.toString()?.trim()?.toIntOrNull()
            ?.let { out.put("peer_tos", it) }
        out.put("dyndns_enabled", binding.dyndnsEnabledSwitch.isChecked)
        out.put(
            "dyndns_service",
            when (dyndnsService.selectedOr(0)) {
                1 -> 0 // DynDNS
                2 -> 1 // No-IP
                else -> -1 // None
            },
        )
        out.put("dyndns_domain", binding.dyndnsDomainInput.text?.toString()?.trim().orEmpty())
        out.put("dyndns_username", binding.dyndnsUsernameInput.text?.toString()?.trim().orEmpty())
        out.put("dyndns_password", binding.dyndnsPasswordInput.text?.toString()?.trim().orEmpty())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
