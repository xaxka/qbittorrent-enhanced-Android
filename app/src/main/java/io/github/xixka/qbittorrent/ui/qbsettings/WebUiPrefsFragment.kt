package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsWebuiBinding
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.bool
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.int
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.str

/**
 * WebUI tab — the WebUI Options "WebUI" page: HTTP server binding (address,
 * port, UPnP, host-header validation) and credentials. This is also the page
 * that governs access from LAN browsers to the bundled engine.
 *
 * The server never returns the stored password, so the password field stays
 * blank: an empty field keeps the current password, a typed one changes it.
 */
class WebUiPrefsFragment : QBPrefsTabFragment() {

    private var _binding: FragmentQbPrefsWebuiBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsWebuiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun populate(prefs: JsonObject) {
        binding.webuiAddressInput.setText(str(prefs, "web_ui_address"))
        binding.webuiPortInput.setText(int(prefs, "web_ui_port", 8080).toString())
        binding.webuiUpnpSwitch.isChecked = bool(prefs, "web_ui_upnp", false)
        binding.hostValidationSwitch.isChecked =
            bool(prefs, "web_ui_host_header_validation_enabled", true)
        binding.webuiUsernameInput.setText(str(prefs, "web_ui_username", "admin"))
        binding.webuiPasswordInput.setText("")
        binding.bypassLocalAuthSwitch.isChecked = bool(prefs, "bypass_local_auth", false)
    }

    override fun collectValues(out: JsonObject) {
        out.put("web_ui_address", binding.webuiAddressInput.text?.toString()?.trim().orEmpty())
        binding.webuiPortInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..65535 }?.let { out.put("web_ui_port", it) }
        out.put("web_ui_upnp", binding.webuiUpnpSwitch.isChecked)
        out.put("web_ui_host_header_validation_enabled", binding.hostValidationSwitch.isChecked)
        // blank username/password = "keep the current one" (never wipe them)
        binding.webuiUsernameInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { out.put("web_ui_username", it) }
        binding.webuiPasswordInput.text?.toString()?.takeIf { it.isNotEmpty() }
            ?.let { out.put("web_ui_password", it) }
        out.put("bypass_local_auth", binding.bypassLocalAuthSwitch.isChecked)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
