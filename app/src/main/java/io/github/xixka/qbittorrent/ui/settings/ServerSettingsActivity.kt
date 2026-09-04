package io.github.xixka.qbittorrent.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Server connection manager, qBitController-style: a list of saved server
 * profiles with add / edit / delete, the active profile selected with a
 * radio row. In the Enhanced edition the bundled engine is the default
 * endpoint, so the list only takes effect after the "use remote server"
 * switch is turned on.
 */
class ServerSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivityServerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(child = binding.serverScroll, sideMask = WindowInsetsSide.BOTTOM)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = ServiceLocator.prefs(this)

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
        render()
    }

    private fun render() {
        val prefs = ServiceLocator.prefs(this)
        val profiles = prefs.serverProfiles()
        val list = binding.profileList
        list.removeAllViews()
        binding.emptyView.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        if (!BuildConfig.IS_ENHANCED || prefs.useRemoteServer) {
            profiles.forEach { profile -> list.addView(profileRow(profile)) }
        } else {
            // engine mode: still show the profiles so they can be managed,
            // but activation happens through the switch in the drawer
            profiles.forEach { profile -> list.addView(profileRow(profile, selectable = false)) }
        }
    }

    /** One list row; tapping selects the active profile. */
    private fun profileRow(profile: ServerProfile, selectable: Boolean = true): View {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_server_profile, binding.profileList, false)
        row.findViewById<TextView>(R.id.profileName).text = profile.displayName()
        row.findViewById<TextView>(R.id.profileHost).text =
            "${if (profile.https) "https" else "http"}://${profile.host}:${profile.port}" +
                profile.basePath
        val radio = row.findViewById<RadioButton>(R.id.profileActive)
        radio.isChecked = ServiceLocator.prefs(this).activeServer()?.id == profile.id
        row.setOnClickListener {
            if (selectable) {
                val prefs = ServiceLocator.prefs(this)
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
            MaterialAlertDialogBuilder(this)
                .setTitle(profile.displayName())
                .setMessage(R.string.server_delete_confirm)
                .setPositiveButton(R.string.rss_delete) { _, _ ->
                    val prefs = ServiceLocator.prefs(this)
                    prefs.deleteServerProfile(profile.id)
                    if (prefs.activeServer() == null) {
                        // last remote profile gone: fall back to the bundled
                        // engine (Enhanced) or an empty config (standard)
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
     * qBC-style add/edit dialog: name, host, port, credentials, base path,
     * https, trust-all + a "test connection" button that tries the profile
     * against the server before saving.
     */
    private fun showProfileDialog(existing: ServerProfile?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_server_profile, null)
        val name = view.findViewById<TextInputEditText>(R.id.nameInput)
        val host = view.findViewById<TextInputEditText>(R.id.hostInput)
        val port = view.findViewById<TextInputEditText>(R.id.portInput)
        val username = view.findViewById<TextInputEditText>(R.id.usernameInput)
        val password = view.findViewById<TextInputEditText>(R.id.passwordInput)
        val basePath = view.findViewById<TextInputEditText>(R.id.basePathInput)
        val https = view.findViewById<MaterialSwitch>(R.id.httpsSwitch)
        val trustAll = view.findViewById<MaterialSwitch>(R.id.trustAllSwitch)

        existing?.let {
            name.setText(it.name)
            host.setText(it.host)
            port.setText(it.port.toString())
            username.setText(it.username)
            password.setText(it.password)
            basePath.setText(it.basePath)
            https.isChecked = it.https
            trustAll.isChecked = it.trustAllCerts
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.server_add else R.string.server_edit)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        // validated save: host is mandatory, port must be 1..65535
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                val hostValue = host.text?.toString()?.trim().orEmpty()
                val portValue = port.text?.toString()?.trim()?.toIntOrNull()
                if (hostValue.isEmpty()) {
                    host.error = getString(R.string.settings_host_hint)
                    return@setOnClickListener
                }
                if (portValue == null || portValue !in 1..65535) {
                    port.error = "1 - 65535"
                    return@setOnClickListener
                }
                val prefs = ServiceLocator.prefs(this)
                val profile = ServerProfile(
                    id = existing?.id ?: System.currentTimeMillis(),
                    name = name.text?.toString()?.trim().orEmpty(),
                    host = hostValue,
                    port = portValue,
                    https = https.isChecked,
                    basePath = basePath.text?.toString()?.trim().orEmpty(),
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
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }

        // test the connection with the values currently typed in the dialog
        view.findViewById<View>(R.id.testButton).setOnClickListener {
            val hostValue = host.text?.toString()?.trim().orEmpty()
            val portValue = port.text?.toString()?.trim()?.toIntOrNull()
                ?: ServerConfig.DEFAULT_PORT
            val config = ServerConfig(
                host = hostValue,
                port = portValue,
                https = https.isChecked,
                basePath = basePath.text?.toString()?.trim().orEmpty(),
                username = username.text?.toString()?.trim().orEmpty(),
                password = password.text?.toString().orEmpty(),
                trustAllCerts = trustAll.isChecked,
            )
            it.isEnabled = false
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { ServiceLocator.testRepository(this@ServerSettingsActivity, config).appVersion() }
                }
                it.isEnabled = true
                result
                    .onSuccess { version ->
                        Toast.makeText(
                            this@ServerSettingsActivity,
                            getString(R.string.settings_connection_ok, version),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .onFailure { e ->
                        Toast.makeText(
                            this@ServerSettingsActivity,
                            getString(R.string.settings_connection_failed, e.message ?: ""),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
