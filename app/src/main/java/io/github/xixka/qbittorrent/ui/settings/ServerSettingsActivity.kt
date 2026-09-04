package io.github.xixka.qbittorrent.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServerConfig
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityServerSettingsBinding
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Server connection settings — the subpage opened from the settings hub.
 *
 * In the standard (remote-control) edition this is the main configuration
 * form. In the Enhanced edition the bundled engine is the default endpoint;
 * the remote-server form only appears when the user explicitly opts in.
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
            binding.remoteSwitch.visibility = View.VISIBLE
            binding.remoteSwitchSub.visibility = View.VISIBLE
            binding.remoteSwitch.isChecked = prefs.useRemoteServer
            binding.remoteSwitch.setOnCheckedChangeListener { _, checked ->
                prefs.useRemoteServer = checked
                binding.remoteForm.visibility = if (checked) View.VISIBLE else View.GONE
                ServiceLocator.resetClient()
            }
            binding.remoteForm.visibility = if (prefs.useRemoteServer) View.VISIBLE else View.GONE
        } else {
            binding.remoteSwitch.visibility = View.GONE
            binding.remoteSwitchSub.visibility = View.GONE
            binding.remoteForm.visibility = View.VISIBLE
        }

        loadForm(prefs)

        binding.testButton.setOnClickListener { testConnection() }
        binding.saveButton.setOnClickListener { save() }
    }

    private fun loadForm(prefs: io.github.xixka.qbittorrent.data.Prefs) {
        binding.hostInput.setText(prefs.serverHost)
        // NB: TextView.setText(Int) resolves a resource ID, NOT a number —
        // passing the port directly crashed with Resources$NotFoundException.
        val portValue = if (prefs.serverPort <= 0) ServerConfig.DEFAULT_PORT else prefs.serverPort
        binding.portInput.setText(portValue.toString())
        binding.usernameInput.setText(prefs.username)
        binding.passwordInput.setText(prefs.password)
        binding.basePathInput.setText(prefs.serverBasePath)
        binding.httpsSwitch.isChecked = prefs.serverHttps
        binding.trustAllSwitch.isChecked = prefs.serverTrustAll
    }

    private fun save() {
        saveQuietly()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        saveQuietly()
        binding.testButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ServiceLocator.repository(this@ServerSettingsActivity).appVersion()
                }
            }
            binding.testButton.isEnabled = true
            result.onSuccess { version ->
                Toast.makeText(
                    this@ServerSettingsActivity,
                    getString(R.string.settings_connection_ok, version),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { e ->
                Toast.makeText(
                    this@ServerSettingsActivity,
                    getString(R.string.settings_connection_failed, e.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** Persist connection values without finishing the screen (used by "test"). */
    private fun saveQuietly() {
        val prefs = ServiceLocator.prefs(this)
        prefs.serverHost = binding.hostInput.text?.toString().orEmpty()
        prefs.serverPort = binding.portInput.text?.toString()?.toIntOrNull()
            ?: ServerConfig.DEFAULT_PORT
        prefs.username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        prefs.password = binding.passwordInput.text?.toString().orEmpty()
        prefs.serverBasePath = binding.basePathInput.text?.toString()?.trim().orEmpty()
        prefs.serverHttps = binding.httpsSwitch.isChecked
        prefs.serverTrustAll = binding.trustAllSwitch.isChecked
        ServiceLocator.resetClient()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
