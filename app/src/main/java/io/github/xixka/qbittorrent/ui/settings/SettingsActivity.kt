package io.github.xixka.qbittorrent.ui.settings

import android.content.Intent
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
import io.github.xixka.qbittorrent.databinding.ActivitySettingsBinding
import io.github.xixka.qbittorrent.qbt.LocalEngineManager
import io.github.xixka.qbittorrent.qbt.LocalEngineService
import io.github.xixka.qbittorrent.ui.qbsettings.QBSettingsActivity
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings hub.
 *
 * The Enhanced edition behaves like a native qBittorrent app: the bundled
 * engine is the default and the full parameter editor is one tap away — no
 * engine URLs or ports to fill in. Server-connection fields exist too, but
 * stay hidden until the user explicitly switches to a remote server. The
 * standard (remote-control) edition shows the connection form up front.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // keep the form clear of the navigation bar in the edge-to-edge layout
        applyWindowInsets(child = binding.settingsScroll, sideMask = WindowInsetsSide.BOTTOM)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ---- qBittorrent preferences (live engine/server settings) ----
        binding.qbSettingsCard.setOnClickListener {
            startActivity(Intent(this, QBSettingsActivity::class.java))
        }

        // ---- app settings ----
        val prefs = ServiceLocator.prefs(this)
        binding.pollInput.setText(prefs.pollIntervalSec.toString())

        // ---- engine group (Enhanced edition only) ----
        if (BuildConfig.IS_ENHANCED && LocalEngineManager.isSupported(this)) {
            binding.engineSection.visibility = View.VISIBLE
            binding.engineAutoStartSwitch.isChecked = prefs.engineAutoStart
            binding.engineAutoStartSwitch.setOnCheckedChangeListener { _, checked ->
                prefs.engineAutoStart = checked
            }
            binding.engineToggleButton.setOnClickListener { toggleEngine() }
        } else {
            binding.engineSection.visibility = View.GONE
        }

        // ---- server connection ----
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

        loadRemoteForm()

        binding.testButton.setOnClickListener { testConnection() }
        binding.saveButton.setOnClickListener { save() }
    }

    override fun onResume() {
        super.onResume()
        updateEngineStatus()
    }

    private fun loadRemoteForm() {
        val prefs = ServiceLocator.prefs(this)
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
        val prefs = ServiceLocator.prefs(this)
        binding.pollInput.text?.toString()?.trim()?.toIntOrNull()?.let {
            prefs.pollIntervalSec = it.coerceIn(1, 60)
        }

        if (binding.remoteForm.visibility == View.VISIBLE) {
            saveRemoteForm(prefs)
        }

        ServiceLocator.resetClient()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        saveQuietly()
        binding.testButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ServiceLocator.repository(this@SettingsActivity).appVersion()
                }
            }
            binding.testButton.isEnabled = true
            result.onSuccess { version ->
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_connection_ok, version),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { e ->
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_connection_failed, e.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** Persist connection values without finishing the screen (used by "test"). */
    private fun saveQuietly() {
        saveRemoteForm(ServiceLocator.prefs(this))
        ServiceLocator.resetClient()
    }

    private fun saveRemoteForm(prefs: io.github.xixka.qbittorrent.data.Prefs) {
        prefs.serverHost = binding.hostInput.text?.toString().orEmpty()
        prefs.serverPort = binding.portInput.text?.toString()?.toIntOrNull()
            ?: ServerConfig.DEFAULT_PORT
        prefs.username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        prefs.password = binding.passwordInput.text?.toString().orEmpty()
        prefs.serverBasePath = binding.basePathInput.text?.toString()?.trim().orEmpty()
        prefs.serverHttps = binding.httpsSwitch.isChecked
        prefs.serverTrustAll = binding.trustAllSwitch.isChecked
    }

    private fun toggleEngine() {
        if (LocalEngineManager.isRunning()) {
            LocalEngineService.stop(this)
        } else {
            LocalEngineService.start(this)
        }
        binding.root.postDelayed({ updateEngineStatus() }, 800)
    }

    private fun updateEngineStatus() {
        if (!BuildConfig.IS_ENHANCED || binding.engineSection.visibility != View.VISIBLE) return
        binding.engineStatusText.setText(
            when (LocalEngineManager.state) {
                LocalEngineManager.State.RUNNING -> R.string.engine_status_running
                LocalEngineManager.State.STARTING -> R.string.engine_status_starting
                else -> R.string.engine_status_stopped
            }
        )
        binding.engineToggleButton.setText(
            if (LocalEngineManager.isRunning()) R.string.settings_engine_stop
            else R.string.settings_engine_start
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
