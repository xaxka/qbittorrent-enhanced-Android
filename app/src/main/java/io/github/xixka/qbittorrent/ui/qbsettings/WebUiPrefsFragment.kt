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
        binding.bypassSubnetSwitch.isChecked = bool(prefs, "bypass_auth_subnet_whitelist_enabled", false)
        binding.bypassSubnetInput.setText(str(prefs, "bypass_auth_subnet_whitelist"))
        binding.maxAuthFailsInput.setText(int(prefs, "web_ui_max_auth_fail_count", 5).toString())
        binding.banDurationInput.setText(int(prefs, "web_ui_ban_duration", 3600).toString())
        binding.sessionTimeoutInput.setText(int(prefs, "web_ui_session_timeout", 3600).toString())
        binding.csrfSwitch.isChecked = bool(prefs, "web_ui_csrf_protection_enabled", true)
        binding.clickjackingSwitch.isChecked = bool(prefs, "web_ui_clickjacking_protection_enabled", true)
        binding.secureCookieSwitch.isChecked = bool(prefs, "web_ui_secure_cookie_enabled", true)
        binding.domainListInput.setText(str(prefs, "web_ui_domain_list"))
        binding.altWebUiSwitch.isChecked = bool(prefs, "alternative_webui_enabled", false)
        binding.altWebUiPathInput.setText(str(prefs, "alternative_webui_path"))
        binding.reverseProxySwitch.isChecked = bool(prefs, "web_ui_reverse_proxy_enabled", false)
        binding.reverseProxiesInput.setText(str(prefs, "web_ui_reverse_proxies_list"))
        binding.customHeadersSwitch.isChecked =
            bool(prefs, "web_ui_use_custom_http_headers_enabled", false)
        binding.customHeadersInput.setText(headersToLines(prefs))
        binding.useHttpsSwitch.isChecked = bool(prefs, "use_https", false)
        binding.httpsCertPathInput.setText(str(prefs, "web_ui_https_cert_path"))
        binding.httpsKeyPathInput.setText(str(prefs, "web_ui_https_key_path"))

        // WebUI locale / refresh rate / instance name / SSRF / status extras
        binding.webUiLocaleInput.setText(str(prefs, "locale"))
        binding.webUiRefreshInput.setText(int(prefs, "refresh_interval", 1500).toString())
        binding.appInstanceNameInput.setText(str(prefs, "app_instance_name"))
        binding.ssrfMitigationSwitch.isChecked = bool(prefs, "ssrf_mitigation", true)
        binding.statusBarIpSwitch.isChecked = bool(prefs, "status_bar_external_ip", true)
        binding.performanceWarningSwitch.isChecked = bool(prefs, "performance_warning", false)
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
        out.put("bypass_auth_subnet_whitelist_enabled", binding.bypassSubnetSwitch.isChecked)
        out.put("bypass_auth_subnet_whitelist", binding.bypassSubnetInput.text?.toString()?.trim().orEmpty())
        binding.maxAuthFailsInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("web_ui_max_auth_fail_count", it) }
        binding.banDurationInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("web_ui_ban_duration", it) }
        binding.sessionTimeoutInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("web_ui_session_timeout", it) }
        out.put("web_ui_csrf_protection_enabled", binding.csrfSwitch.isChecked)
        out.put("web_ui_clickjacking_protection_enabled", binding.clickjackingSwitch.isChecked)
        out.put("web_ui_secure_cookie_enabled", binding.secureCookieSwitch.isChecked)
        out.put("web_ui_domain_list", binding.domainListInput.text?.toString()?.trim().orEmpty())
        out.put("alternative_webui_enabled", binding.altWebUiSwitch.isChecked)
        out.put("alternative_webui_path", binding.altWebUiPathInput.text?.toString()?.trim().orEmpty())
        out.put("web_ui_reverse_proxy_enabled", binding.reverseProxySwitch.isChecked)
        out.put("web_ui_reverse_proxies_list", binding.reverseProxiesInput.text?.toString()?.trim().orEmpty())
        out.put("web_ui_use_custom_http_headers_enabled", binding.customHeadersSwitch.isChecked)
        out.add("web_ui_custom_http_headers", headersToJson(binding.customHeadersInput.text?.toString().orEmpty()))
        out.put("use_https", binding.useHttpsSwitch.isChecked)
        out.put("web_ui_https_cert_path", binding.httpsCertPathInput.text?.toString()?.trim().orEmpty())
        out.put("web_ui_https_key_path", binding.httpsKeyPathInput.text?.toString()?.trim().orEmpty())

        out.put("locale", binding.webUiLocaleInput.text?.toString()?.trim().orEmpty())
        binding.webUiRefreshInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("refresh_interval", it) }
        out.put("app_instance_name", binding.appInstanceNameInput.text?.toString()?.trim().orEmpty())
        out.put("ssrf_mitigation", binding.ssrfMitigationSwitch.isChecked)
        out.put("status_bar_external_ip", binding.statusBarIpSwitch.isChecked)
        out.put("performance_warning", binding.performanceWarningSwitch.isChecked)
    }

    /** Custom HTTP headers as `Name: value` lines, one per line. */
    private fun headersToLines(prefs: JsonObject): String {
        val headers = prefs.get("web_ui_custom_http_headers")
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: return ""
        return headers.entrySet().joinToString("\n") { (name, value) -> "$name: ${value.asString}" }
    }

    private fun headersToJson(lines: String): JsonObject {
        val result = JsonObject()
        lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val name = line.substring(0, colon).trim()
                    val value = line.substring(colon + 1).trim()
                    if (name.isNotEmpty()) result.addProperty(name, value)
                }
            }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
