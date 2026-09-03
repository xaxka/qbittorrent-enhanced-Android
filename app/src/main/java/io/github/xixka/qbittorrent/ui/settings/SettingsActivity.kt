package io.github.xixka.qbittorrent.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServerConfig
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivitySettingsBinding
import io.github.xixka.qbittorrent.qbt.LocalEngineManager
import io.github.xixka.qbittorrent.qbt.LocalEngineService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Server connection settings (address / port / credentials, default port 8080)
 * plus local engine controls for the Enhanced edition.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        load()

        binding.testButton.setOnClickListener { testConnection() }
        binding.saveButton.setOnClickListener { save() }

        if (BuildConfig.IS_ENHANCED && LocalEngineManager.isSupported(this)) {
            binding.engineSection.visibility = View.VISIBLE
            binding.engineToggleButton.setOnClickListener { toggleEngine() }
        } else {
            binding.engineSection.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        updateEngineStatus()
    }

    private fun load() {
        val prefs = ServiceLocator.prefs(this)
        val cfg = prefs.serverConfig()
        binding.hostInput.setText(cfg.host)
        binding.portInput.setText(if (cfg.port <= 0) ServerConfig.DEFAULT_PORT else cfg.port)
        binding.usernameInput.setText(cfg.username)
        binding.passwordInput.setText(prefs.password)
        binding.basePathInput.setText(cfg.basePath)
        binding.httpsSwitch.isChecked = cfg.https
        binding.trustAllSwitch.isChecked = cfg.trustAllCerts

        binding.enginePortInput.setText(prefs.enginePort)
        binding.engineSavePathInput.setText(prefs.engineSavePath)
        binding.engineLanSwitch.isChecked = prefs.engineLanAccess
        binding.engineAutoStartSwitch.isChecked = prefs.engineAutoStart
    }

    private fun save() {
        val prefs = ServiceLocator.prefs(this)
        prefs.serverHost = binding.hostInput.text?.toString().orEmpty()
        prefs.serverPort = binding.portInput.text?.toString()?.toIntOrNull()
            ?: ServerConfig.DEFAULT_PORT
        prefs.username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        prefs.password = binding.passwordInput.text?.toString().orEmpty()
        prefs.serverBasePath = binding.basePathInput.text?.toString()?.trim().orEmpty()
        prefs.serverHttps = binding.httpsSwitch.isChecked
        prefs.serverTrustAll = binding.trustAllSwitch.isChecked
        prefs.pollIntervalSec = 2

        prefs.enginePort = binding.enginePortInput.text?.toString()?.toIntOrNull()
            ?: ServerConfig.DEFAULT_PORT
        prefs.engineSavePath = binding.engineSavePathInput.text?.toString()?.trim().orEmpty()
        prefs.engineLanAccess = binding.engineLanSwitch.isChecked
        prefs.engineAutoStart = binding.engineAutoStartSwitch.isChecked

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

    /** Persist values without finishing the screen (used by “test”). */
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

    private fun toggleEngine() {
        if (LocalEngineManager.isRunning()) {
            LocalEngineService.stop(this)
        } else {
            LocalEngineService.start(this)
        }
        binding.root.postDelayed({ updateEngineStatus() }, 800)
    }

    private fun updateEngineStatus() {
        if (!BuildConfig.IS_ENHANCED) return
        binding.engineStatusText.setText(
            when (LocalEngineManager.state) {
                LocalEngineManager.State.RUNNING -> R.string.engine_status_running
                LocalEngineManager.State.STARTING -> R.string.engine_status_starting
                else -> R.string.engine_status_stopped
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
