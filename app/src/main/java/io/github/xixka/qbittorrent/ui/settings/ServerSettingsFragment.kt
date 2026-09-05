package io.github.xixka.qbittorrent.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServerConfig
import io.github.xixka.qbittorrent.data.ServerProfile
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityServerSettingsBinding
import io.github.xixka.qbittorrent.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Server connection manager, qBitController-style: a list of saved server
 * profiles with add / edit / delete, the active profile selected with a
 * radio row. The bundled engine is the default endpoint, so the list takes
 * effect after the "use remote server" switch is turned on.
 * Opened from Settings, hosted IN PLACE — no separate window.
 */
class ServerSettingsFragment : Fragment() {

    private var _binding: ActivityServerSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ActivityServerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { (activity as? MainActivity)?.popPage() }

        val prefs = ServiceLocator.prefs(requireContext())

        if (BuildConfig.IS_ENHANCED) {
            binding.remoteSwitch.isChecked = prefs.useRemoteServer
            binding.remoteSwitch.setOnCheckedChangeListener { _, checked ->
                prefs.useRemoteServer = checked
                ServiceLocator.resetClient()
                render()
            }
        } else {
            binding.remoteSwitch.visibility = View.GONE
            binding.remoteSwitchSub.visibility = View.GONE
        }

        binding.addButton.setOnClickListener { showProfileDialog(null) }
        // Engine mode: show how to reach the embedded WebUI from a LAN
        // browser (URL + credentials) — tapping the line copies the URL.
        binding.remoteSwitchSub.setOnClickListener {
            val prefsNow = ServiceLocator.prefs(requireContext())
            if (BuildConfig.IS_ENHANCED && !prefsNow.useRemoteServer) {
                val url = "http://${lanIpAddress() ?: "127.0.0.1"}:${prefsNow.enginePort}"
                val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("WebUI", url))
                Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
        render()
    }

    /** First site-local IPv4 of the device (for the LAN WebUI URL). */
    private fun lanIpAddress(): String? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /** Engine-mode caption: LAN WebUI address + login credentials. */
    private fun engineCaption(): String {
        val prefsNow = ServiceLocator.prefs(requireContext())
        val url = "http://${lanIpAddress() ?: "127.0.0.1"}:${prefsNow.enginePort}"
        return getString(R.string.engine_webui_access_fmt, url, prefsNow.engineUsername, prefsNow.enginePassword)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render() {
        val prefs = ServiceLocator.prefs(requireContext())
        val profiles = prefs.serverProfiles()
        val list = binding.profileList
        list.removeAllViews()
        binding.emptyView.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        if (BuildConfig.IS_ENHANCED && !prefs.useRemoteServer) {
            // engine mode: the caption doubles as the LAN WebUI access info
            binding.remoteSwitchSub.text = engineCaption()
        } else {
            binding.remoteSwitchSub.setText(R.string.settings_remote_switch_sub)
        }
        if (!BuildConfig.IS_ENHANCED || prefs.useRemoteServer) {
            profiles.forEach { profile -> list.addView(profileRow(profile)) }
        } else {
            // engine mode: still show the profiles so they can be managed,
            // but activation happens through the remote-server switch
            profiles.forEach { profile -> list.addView(profileRow(profile, selectable = false)) }
        }
    }

    /** One list row; tapping selects the active profile. */
    private fun profileRow(profile: ServerProfile, selectable: Boolean = true): View {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_server_profile, binding.profileList, false)
        row.findViewById<TextView>(R.id.profileName).text = profile.displayName()
        row.findViewById<TextView>(R.id.profileHost).text =
            "${if (profile.https) "https" else "http"}://${profile.host}:${profile.port}" +
                profile.basePath
        val radio = row.findViewById<RadioButton>(R.id.profileActive)
        radio.isChecked = ServiceLocator.prefs(requireContext()).activeServer()?.id == profile.id
        row.setOnClickListener {
            if (selectable) {
                val prefs = ServiceLocator.prefs(requireContext())
                if (prefs.activeServer()?.id != profile.id) {
                    prefs.switchServer(profile.id)
                }
                ServiceLocator.resetClient()
                render()
            }
        }
        row.findViewById<ImageButton>(R.id.profileEdit).setOnClickListener {
            showProfileDialog(profile)
        }
        row.findViewById<ImageButton>(R.id.profileDelete).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(profile.displayName())
                .setMessage(R.string.server_delete_confirm)
                .setPositiveButton(R.string.rss_delete) { _, _ ->
                    val prefs = ServiceLocator.prefs(requireContext())
                    prefs.deleteServerProfile(profile.id)
                    if (prefs.activeServer() == null) {
                        // last remote profile gone: fall back to the bundled engine
                        prefs.useRemoteServer = false
                    }
                    ServiceLocator.resetClient()
                    render()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        return row
    }

    /**
     * qBC AddEditServerScreen-style add/edit dialog: a single URL field
     * (scheme, host, port and optional sub-path are parsed from it), two
     * section headers, credentials with a password toggle, trust-all and a
     * "test configuration" button that validates the typed URL inline
     * (isError on the field) before saving or testing.
     */
    private fun showProfileDialog(existing: ServerProfile?) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_server_profile, null)
        val name = view.findViewById<TextInputEditText>(R.id.nameInput)
        val urlLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.urlInputLayout)
        val urlInput = view.findViewById<TextInputEditText>(R.id.urlInput)
        val username = view.findViewById<TextInputEditText>(R.id.usernameInput)
        val password = view.findViewById<TextInputEditText>(R.id.passwordInput)
        val trustAll = view.findViewById<MaterialSwitch>(R.id.trustAllSwitch)

        urlLayout.helperText = getString(
            R.string.settings_server_url_examples,
            "\n\u2022 192.168.1.20:8080\n\u2022 https://example.com\n\u2022 https://example.com/qbittorrent",
        )

        existing?.let {
            name.setText(it.name)
            urlInput.setText(urlOf(it))
            username.setText(it.username)
            password.setText(it.password)
            trustAll.isChecked = it.trustAllCerts
        }

        fun configFromUrl(): ServerConfig? {
            urlLayout.error = null
            val config = parseServerUrl(urlInput.text?.toString().orEmpty())
            if (config == null) urlLayout.error = getString(R.string.settings_server_invalid_url)
            return config
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.server_add else R.string.server_edit)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                val config = configFromUrl() ?: return@setOnClickListener
                val prefs = ServiceLocator.prefs(requireContext())
                val profile = ServerProfile(
                    id = existing?.id ?: System.currentTimeMillis(),
                    name = name.text?.toString()?.trim().orEmpty(),
                    host = config.host,
                    port = config.port,
                    https = config.https,
                    basePath = config.basePath,
                    username = username.text?.toString()?.trim().orEmpty(),
                    password = password.text?.toString().orEmpty(),
                    trustAllCerts = trustAll.isChecked,
                )
                prefs.saveServerProfile(profile)
                if (prefs.activeServer() == null) {
                    prefs.activeServerId = profile.id
                }
                if (!BuildConfig.IS_ENHANCED) prefs.useRemoteServer = true
                ServiceLocator.resetClient()
                dialog.dismiss()
                render()
                Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }

        // test the connection with the values currently typed in the dialog
        view.findViewById<View>(R.id.testButton).setOnClickListener {
            val config = configFromUrl() ?: return@setOnClickListener
            it.isEnabled = false
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { ServiceLocator.testRepository(requireContext(), config).appVersion() }
                }
                it.isEnabled = true
                result
                    .onSuccess { version ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.settings_connection_ok, version),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .onFailure { e ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.settings_connection_failed, e.message ?: ""),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            }
        }
    }

    /**
     * Parses a qBC-style server URL into a [ServerConfig]: the scheme is
     * optional (http assumed), the port falls back to 8080/443, and any
     * path component becomes the base path (reverse-proxy deployments).
     * Returns null when the URL cannot be understood — the caller shows
     * the invalid-URL error on the field.
     */
    private fun parseServerUrl(raw: String): ServerConfig? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = android.net.Uri.parse(withScheme)
        val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty() || host.contains(" ")) return null
        val port = when {
            uri.port > 0 -> uri.port
            scheme == "https" -> 443
            else -> ServerConfig.DEFAULT_PORT
        }
        if (port !in 1..65535) return null
        val basePath = uri.path?.trim('/').orEmpty()
        return ServerConfig(
            host = host,
            port = port,
            https = scheme == "https",
            basePath = basePath,
        )
    }

    /** Inverse of [parseServerUrl] for editing: scheme://host[:port][/path],
     *  with redundant default ports omitted. */
    private fun urlOf(p: ServerProfile): String = buildString {
        append(if (p.https) "https://" else "http://")
        append(p.host)
        val defaultPort = if (p.https) 443 else ServerConfig.DEFAULT_PORT
        if (p.port != defaultPort && p.port in 1..65535) append(':').append(p.port)
        val path = p.basePath.trim('/')
        if (path.isNotEmpty()) append('/').append(path)
    }

}
