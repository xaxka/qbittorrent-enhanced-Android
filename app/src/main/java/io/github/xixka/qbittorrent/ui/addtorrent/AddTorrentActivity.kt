package io.github.xixka.qbittorrent.ui.addtorrent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.chip.Chip
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.databinding.ActivityAddTorrentBinding
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add a torrent by URL / magnet link or .torrent file with the full
 * parameter set of the qBittorrent WebUI add dialog (qBitController
 * parity): name, category, save path, content layout, stop condition,
 * per-torrent speed/share limits, start-stopped, skip checking, sequential
 * download, first/last piece priority and automatic torrent management.
 *
 * Dropdown defaults (categories, save path, "start stopped", layout, stop
 * condition) mirror the connected instance's own preferences, exactly like
 * the WebUI does when it opens its add dialog.
 */
class AddTorrentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTorrentBinding

    private var fileBytes: ByteArray? = null
    private var fileName: String? = null

    /** contentLayout values accepted by /api/v2/torrents/add. */
    private val contentLayoutValues = listOf("Original", "Subfolder", "NoSubfolder")

    /** stopCondition values accepted by /api/v2/torrents/add. */
    private val stopConditionValues = listOf("None", "MetadataReceived", "FilesChecked")

    private var selectedContentLayout = 0
    private var selectedStopCondition = 0

    /** Fields the user already changed: async server defaults must never
     *  overwrite a choice the user made while the request was in flight. */
    private var layoutTouched = false
    private var stopTouched = false
    private var pausedTouched = false

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) readFile(uri)
        }

    /** Live values the add form is prefilled from (official API). */
    private data class Defaults(
        val categories: List<String>,
        val categoryMeta: Map<String, QBCategory>,
        val tags: List<String>,
        val savePath: String,
        val prefs: JsonObject?,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivityAddTorrentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // keep the form clear of the navigation bar in the edge-to-edge layout
        applyWindowInsets(child = binding.addScroll, sideMask = WindowInsetsSide.BOTTOM)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupDropdowns()
        savedInstanceState?.let { s -> restoreInstanceState(s) }
        binding.pickFileButton.setOnClickListener { pickFile.launch("*/*") }
        binding.addButton.setOnClickListener { submit() }

        handleIntent(intent)
        loadServerDefaults()
    }

    private fun setupDropdowns() {
        binding.layoutDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                listOf(
                    getString(R.string.content_layout_original),
                    getString(R.string.content_layout_subfolder),
                    getString(R.string.content_layout_no_subfolder),
                ),
            )
        )
        binding.layoutDropdown.setOnItemClickListener { _, _, position, _ ->
            layoutTouched = true
            selectedContentLayout = position
        }

        binding.stopConditionDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                listOf(
                    getString(R.string.stop_condition_none),
                    getString(R.string.stop_condition_metadata),
                    getString(R.string.stop_condition_files_checked),
                ),
            )
        )
        binding.stopConditionDropdown.setOnItemClickListener { _, _, position, _ ->
            stopTouched = true
            selectedStopCondition = position
        }

        binding.pausedSwitch.setOnCheckedChangeListener { _, _ -> pausedTouched = true }
    }

    private fun restoreInstanceState(s: Bundle) {
        selectedContentLayout = s.getInt(STATE_LAYOUT, 0)
        selectedStopCondition = s.getInt(STATE_STOP, 0)
        binding.layoutDropdown.setText(
            listOf(
                getString(R.string.content_layout_original),
                getString(R.string.content_layout_subfolder),
                getString(R.string.content_layout_no_subfolder),
            )[selectedContentLayout],
            false,
        )
        binding.stopConditionDropdown.setText(
            listOf(
                getString(R.string.stop_condition_none),
                getString(R.string.stop_condition_metadata),
                getString(R.string.stop_condition_files_checked),
            )[selectedStopCondition],
            false,
        )
        s.getString(STATE_FILE_NAME)?.let { name ->
            val f = java.io.File(cacheDir, PENDING_ADD_TORRENT_FILE)
            if (f.isFile) {
                val bytes = runCatching { f.readBytes() }.getOrNull()
                if (bytes != null) {
                    fileBytes = bytes
                    fileName = name
                    binding.fileNameText.text = fileName
                    binding.fileNameText.visibility = View.VISIBLE
                }
            }
        }
    }

    /** Categories + tags + defaults from the connected instance, like the
     *  WebUI / qBC AddTorrent dialog: every offered option is generated
     *  live from the Web API, nothing is hardcoded. */
    private fun loadServerDefaults() {
        lifecycleScope.launch {
            val defaults = withContext(Dispatchers.IO) {
                runCatching {
                    val repo = ServiceLocator.repository(this@AddTorrentActivity)
                    val meta = repo.categories()
                    Defaults(
                        meta.keys.filter { it.isNotBlank() }.sorted(),
                        meta,
                        repo.tags(),
                        repo.defaultSavePath(),
                        repo.appPreferences(),
                    )
                }.getOrNull() ?: Defaults(emptyList(), emptyMap(), emptyList(), "", null)
            }
            renderTagChips(defaults.tags)
            if (defaults.categories.isNotEmpty()) {
                binding.categoryInput.setAdapter(
                    ArrayAdapter(
                        this@AddTorrentActivity,
                        android.R.layout.simple_list_item_1,
                        defaults.categories,
                    )
                )
                // Selecting a category switches the save path to that
                // category's own path — exactly what the WebUI does (the
                // paths come live from the categories API).
                binding.categoryInput.setOnItemClickListener { _, _, position, _ ->
                    val path = defaults.categoryMeta[defaults.categories.getOrNull(position)]?.savePath
                    if (!path.isNullOrBlank()) {
                        binding.savePathInput.setText(path)
                    }
                }
            }
            if (defaults.savePath.isNotBlank() && binding.savePathInput.text?.isBlank() == true) {
                binding.savePathInput.setText(defaults.savePath)
            }
            defaults.prefs?.let { p ->
                // "Start torrent" checkbox mirrors the server preference, like the WebUI —
                // but only when the user has not flipped it while loading
                if (!pausedTouched) {
                    binding.pausedSwitch.isChecked =
                        p.get("add_stopped_enabled")?.asBoolean == true
                }
                if (!layoutTouched) {
                    val layout = p.get("torrent_content_layout")?.takeIf { it.isJsonPrimitive }?.asString
                    selectedContentLayout = layout.let {
                        when (it?.lowercase()) {
                            "subfolder" -> 1
                            "nosubfolder" -> 2
                            else -> 0
                        }
                    }
                    binding.layoutDropdown.setText(
                        listOf(
                            getString(R.string.content_layout_original),
                            getString(R.string.content_layout_subfolder),
                            getString(R.string.content_layout_no_subfolder),
                        )[selectedContentLayout],
                        false,
                    )
                }
                if (!stopTouched) {
                    val stop = p.get("torrent_stop_condition")?.takeIf { it.isJsonPrimitive }?.asString
                    selectedStopCondition = when (stop?.lowercase()) {
                        "metadatareceived" -> 1
                        "fileschecked" -> 2
                        else -> 0
                    }
                    binding.stopConditionDropdown.setText(
                        listOf(
                            getString(R.string.stop_condition_none),
                            getString(R.string.stop_condition_metadata),
                            getString(R.string.stop_condition_files_checked),
                        )[selectedStopCondition],
                        false,
                    )
                }
            }
        }
    }

    /** Multi-select tag chips generated live from /torrents/tags
     *  (qBC AddTorrent parity); the section stays hidden until the server
     *  list arrives and is GONE again when it is empty. */
    private fun renderTagChips(tags: List<String>) {
        val group = binding.tagsChipGroup
        group.removeAllViews()
        binding.tagsSection.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
        tags.forEach { tag ->
            val chip = layoutInflater.inflate(R.layout.item_tag_chip, group, false) as Chip
            chip.text = tag
            // Filter-style chip: stays unselected until tapped; the checked
            // state IS the selection (read back at submit time).
            chip.isCheckable = true
            chip.isChecked = false
            group.addView(chip)
        }
    }

    /** Tags currently checked in the tag chip group (may be empty). */
    private fun selectedTags(): List<String> {
        val group = binding.tagsChipGroup
        return (0 until group.childCount)
            .mapNotNull { group.getChildAt(it) as? Chip }
            .filter { it.isChecked }
            .mapNotNull { it.text?.toString()?.trim() }
            .filter { it.isNotEmpty() }
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
        if (urls.isNotEmpty() && fileBytes == null &&
            !urls.lineSequence()
                .filter { it.isNotBlank() } // a stray blank line must not reject the whole batch
                .all { it.startsWith("magnet:") || it.startsWith("http") }
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
                        tags = selectedTags(),
                        paused = binding.pausedSwitch.isChecked,
                        sequential = binding.sequentialSwitch.isChecked,
                        skipChecking = binding.skipCheckingSwitch.isChecked,
                        firstLastPiece = binding.firstLastSwitch.isChecked,
                        autoTmm = binding.autoTmmSwitch.isChecked,
                        stopCondition = stopConditionValues[selectedStopCondition],
                        contentLayout = contentLayoutValues[selectedContentLayout],
                        rename = binding.nameInput.text?.toString()?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        dlLimit = binding.dlLimitInput.text?.toString()?.trim()
                            ?.toLongOrNull()?.takeIf { it >= 0 }?.let { it * 1024 },
                        upLimit = binding.upLimitInput.text?.toString()?.trim()
                            ?.toLongOrNull()?.takeIf { it >= 0 }?.let { it * 1024 },
                        ratioLimit = binding.ratioLimitInput.text?.toString()?.trim()
                            ?.toDoubleOrNull()?.takeIf { it >= 0.0 },
                        seedingTimeLimit = binding.seedingTimeInput.text?.toString()?.trim()
                            ?.toLongOrNull()?.takeIf { it >= 0 },
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_LAYOUT, selectedContentLayout)
        outState.putInt(STATE_STOP, selectedStopCondition)
        // the picked .torrent goes through a cache file: it can exceed the
        // Bundle limit, and losing it means re-picking the file
        fileBytes?.let { bytes ->
            runCatching {
                java.io.File(cacheDir, PENDING_ADD_TORRENT_FILE).writeBytes(bytes)
                outState.putString(STATE_FILE_NAME, fileName ?: "torrent.torrent")
            }
        }
    }

    companion object {
        private const val STATE_LAYOUT = "state_layout"
        private const val STATE_STOP = "state_stop"
        private const val STATE_FILE_NAME = "state_file_name"
        private const val PENDING_ADD_TORRENT_FILE = "pending_add.torrent"
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
