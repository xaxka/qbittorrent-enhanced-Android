package io.github.xixka.qbittorrent.ui.addtorrent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityAddTorrentBinding
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add a torrent by URL / magnet link or by picking a .torrent file — exactly the
 * upstream /api/v2/torrents/add parameters.
 */
class AddTorrentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTorrentBinding

    private var fileBytes: ByteArray? = null
    private var fileName: String? = null

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) readFile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAddTorrentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // keep the form clear of the navigation bar in the edge-to-edge layout
        applyWindowInsets(child = binding.addScroll, sideMask = WindowInsetsSide.BOTTOM)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.pickFileButton.setOnClickListener { pickFile.launch("*/*") }

        binding.addButton.setOnClickListener { submit() }

        handleIntent(intent)
    }

    /** Accepts shared magnet/torrent links and .torrent files from other apps. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val action = intent.action ?: return
        val data: Uri? = intent.data
        when (action) {
            Intent.ACTION_VIEW -> when {
                data?.scheme == "magnet" -> binding.urlsInput.setText(data.toString())
                data != null && isTorrentData(data) -> readFile(data)
            }

            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    binding.urlsInput.setText(text.trim())
                }
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) readFile(stream)
            }
        }
    }

    private fun isTorrentData(uri: Uri): Boolean {
        val mime = contentResolver.getType(uri)
        return mime == "application/x-bittorrent" ||
            uri.lastPathSegment?.endsWith(".torrent", ignoreCase = true) == true
    }

    private fun readFile(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (result == null || result.isEmpty()) {
                Toast.makeText(this@AddTorrentActivity, R.string.add_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            fileBytes = result
            fileName = uri.lastPathSegment ?: "torrent.torrent"
            binding.fileNameText.text = fileName
            binding.fileNameText.visibility = View.VISIBLE
        }
    }

    private fun submit() {
        val urls = binding.urlsInput.text?.toString()?.trim().orEmpty()
        if (urls.isEmpty() && fileBytes == null) {
            Toast.makeText(this, R.string.add_no_input, Toast.LENGTH_SHORT).show()
            return
        }
        if (!urls.isEmpty() && fileBytes == null &&
            !urls.startsWith("magnet:") && !urls.startsWith("http")
        ) {
            Toast.makeText(this, R.string.add_no_input, Toast.LENGTH_SHORT).show()
            return
        }

        binding.addButton.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val repo = ServiceLocator.repository(this@AddTorrentActivity)
                    repo.addTorrent(
                        urls = urls.ifEmpty { null },
                        fileBytes = fileBytes,
                        fileName = fileName ?: "torrent.torrent",
                        savePath = binding.savePathInput.text?.toString()?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        category = binding.categoryInput.text?.toString()?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        paused = binding.pausedCheck.isChecked,
                        sequential = binding.sequentialCheck.isChecked,
                    )
                    true
                }.getOrElse { false }
            }
            binding.addButton.isEnabled = true
            if (ok) {
                Toast.makeText(this@AddTorrentActivity, R.string.add_success, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@AddTorrentActivity, R.string.add_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        /** Opens the add-torrent screen with a link or a picked .torrent file. */
        fun start(context: android.content.Context, url: String? = null, uri: android.net.Uri? = null) {
            val intent = android.content.Intent(context, AddTorrentActivity::class.java)
                .setAction(android.content.Intent.ACTION_SEND)
                .setType("text/plain")
            if (url != null) intent.putExtra(android.content.Intent.EXTRA_TEXT, url)
            if (uri != null) intent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
            context.startActivity(intent)
        }
    }
}
