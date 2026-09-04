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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
