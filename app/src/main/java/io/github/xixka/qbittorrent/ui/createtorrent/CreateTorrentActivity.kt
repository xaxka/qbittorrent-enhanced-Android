package io.github.xixka.qbittorrent.ui.createtorrent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.ActivityCreateTorrentBinding
import io.github.xixka.qbittorrent.databinding.DialogProgressBinding
import io.github.xixka.qbittorrent.util.Format
import io.github.xixka.qbittorrent.util.SafPaths
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.TorrentMaker
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device torrent creator. The official qBittorrent WebUI API has no
 * torrent-creation endpoint (upstream feature request #5614 is still
 * open), so this screen builds the .torrent file natively with the same
 * parameter set as the desktop creator. "Start seeding immediately"
 * hands the finished file to the engine through the official add API.
 */
class CreateTorrentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTorrentBinding

    private var sourceUri: Uri? = null
    private var sourceIsTree = false
    private var realPath: String? = null
    private var sourceName: String? = null

    /** Result of a finished hashing run, kept for the save-file step. */
    private var madeResult: TorrentMaker.Result? = null

    private var hashJob: Job? = null

    /** Hashing progress dialog, dismissed on destroy to avoid WindowLeaked. */
    private var progressDialog: AlertDialog? = null

    /** Piece size menu: Auto + fixed sizes. */
    private val pieceLabels by lazy {
        listOf(getString(R.string.create_torrent_piece_auto)) +
            listOf(16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384)
                .map { if (it >= 1024) "${it / 1024} MiB" else "$it KiB" }
    }
    private val pieceValues: List<Long?> =
        listOf<Long?>(null) + listOf(16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384)
            .map { it.toLong() * 1024 }

    private var selectedPiece: Long? = null

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) onSourcePicked(uri, isTree = false)
        }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) onSourcePicked(uri, isTree = true)
        }

    private val saveFile =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/x-bittorrent")
        ) { uri: Uri? ->
            if (uri != null) writeTorrentFile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeUtils.applyDynamicColors(this, ServiceLocator.prefs(this).dynamicColors)
        binding = ActivityCreateTorrentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(child = binding.createScroll, sideMask = WindowInsetsSide.BOTTOM)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.sourceFileButton.setOnClickListener { pickFile.launch(arrayOf("*/*")) }
        binding.sourceFolderButton.setOnClickListener { pickFolder.launch(null) }
        binding.createButton.setOnClickListener { startCreate() }

        binding.pieceSizeDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, pieceLabels)
        )
        binding.pieceSizeDropdown.setText(pieceLabels[0], false)
        binding.pieceSizeDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedPiece = pieceValues.getOrNull(position)
        }

        savedInstanceState?.let { s -> restoreState(s) }

        loadCategories()

        updateSeedingAvailability()
    }

    /** Rotation / process death must not lose the picked source, the chosen
     *  piece size (the dropdown text restores but the click listener does not
     *  re-fire, silently switching the submit to "Auto") or hours of hashing. */
    private fun restoreState(s: Bundle) {
        sourceUri = s.getString(STATE_SOURCE)?.let { Uri.parse(it) }
        sourceIsTree = s.getBoolean(STATE_IS_TREE)
        realPath = s.getString(STATE_REAL_PATH)
        sourceName = s.getString(STATE_SOURCE_NAME)
        sourceName?.let { binding.sourceNameText.text = it }
        if (s.getBoolean(STATE_HAS_PIECE)) {
            selectedPiece = s.getLong(STATE_PIECE)
            pieceValues.indexOf(selectedPiece).takeIf { it > 0 }?.let { idx ->
                binding.pieceSizeDropdown.setText(pieceLabels[idx], false)
            }
        }
        // a finished hash run is persisted to a cache file: the torrent bytes
        // can exceed the Bundle limit, and re-hashing large sources takes hours
        s.getString(STATE_MADE_FILE)?.let { path ->
            val f = File(path)
            if (f.isFile) {
                madeResult = TorrentMaker.Result(
                    bytes = runCatching { f.readBytes() }.getOrNull() ?: return@let,
                    name = s.getString(STATE_MADE_NAME).orEmpty(),
                    pieceSize = s.getLong(STATE_MADE_PIECE),
                    totalSize = s.getLong(STATE_MADE_TOTAL),
                    fileCount = s.getInt(STATE_MADE_COUNT),
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SOURCE, sourceUri?.toString())
        outState.putBoolean(STATE_IS_TREE, sourceIsTree)
        outState.putString(STATE_REAL_PATH, realPath)
        outState.putString(STATE_SOURCE_NAME, sourceName)
        outState.putBoolean(STATE_HAS_PIECE, selectedPiece != null)
        selectedPiece?.let { outState.putLong(STATE_PIECE, it) }
        madeResult?.let { made ->
            runCatching {
                val f = File(cacheDir, PENDING_TORRENT_FILE)
                f.writeBytes(made.bytes)
                outState.putString(STATE_MADE_FILE, f.absolutePath)
                outState.putString(STATE_MADE_NAME, made.name)
                outState.putLong(STATE_MADE_PIECE, made.pieceSize)
                outState.putLong(STATE_MADE_TOTAL, made.totalSize)
                outState.putInt(STATE_MADE_COUNT, made.fileCount)
            }
        }
    }

    override fun onDestroy() {
        // rotation cancels the hashing coroutine before its dismiss() line
        // runs — dismiss here so the window is not leaked
        runCatching { progressDialog?.dismiss() }
        progressDialog = null
        super.onDestroy()
    }

    /** Category dropdown options are generated live from
     *  /api/v2/torrents/categories (Web API v2 — nothing hardcoded); the
     *  field stays editable so a new category name can be typed as well. */
    private fun loadCategories() {
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                runCatching {
                    ServiceLocator.repository(this@CreateTorrentActivity)
                        .categories().keys.filter { it.isNotBlank() }.sorted()
                }.getOrDefault(emptyList())
            }
            binding.categoryInput.setAdapter(
                ArrayAdapter(this@CreateTorrentActivity, android.R.layout.simple_list_item_1, categories)
            )
        }
    }

    private fun onSourcePicked(uri: Uri, isTree: Boolean) {
        sourceUri = uri
        sourceIsTree = isTree
        val name = displayNameOf(uri, isTree) ?: uri.lastPathSegment ?: "?"
        sourceName = name
        binding.sourceNameText.text = name
        if (binding.nameInput.text?.isBlank() == true) {
            binding.nameInput.setText(name)
        }
        realPath = SafPaths.toRealPath(this, uri, isTree)
        updateSeedingAvailability()
    }

    private fun updateSeedingAvailability() {
        val uri = sourceUri
        val canSeed = uri != null && realPath != null &&
            SafPaths.engineCanRead(this, uri, sourceIsTree)
        binding.startSeedingSwitch.isEnabled = canSeed
        // The category only labels the seeded copy — without seeding there
        // is nothing to label, so the dropdown follows the switch.
        binding.categoryInput.isEnabled = canSeed
    }

    private fun displayNameOf(uri: Uri, isTree: Boolean): String? {
        val docUri = if (isTree) {
            DocumentsContract.buildDocumentUriUsingTree(
                uri, DocumentsContract.getTreeDocumentId(uri)
            )
        } else uri
        return runCatching {
            contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    private fun startCreate() {
        val uri = sourceUri ?: run {
            Toast.makeText(this, R.string.create_torrent_need_source, Toast.LENGTH_SHORT).show()
            return
        }
        val tiers = binding.trackersInput.text?.toString().orEmpty()
            .split("\n\n")
            .map { tier -> tier.lines().map { it.trim() }.filter { it.isNotEmpty() } }
            .filter { it.isNotEmpty() }
        val options = TorrentMaker.Options(
            name = binding.nameInput.text?.toString()?.trim().orEmpty(),
            trackers = tiers,
            webSeeds = binding.webSeedsInput.text?.toString().orEmpty()
                .lines().map { it.trim() }.filter { it.isNotEmpty() },
            comment = binding.commentInput.text?.toString()?.trim().orEmpty(),
            createdBy = "qBittorrent Enhanced Android ${BuildConfig.VERSION_NAME}",
            private = binding.privateSwitch.isChecked,
            pieceSize = selectedPiece,
        )
        val wantSeeding = binding.startSeedingSwitch.isEnabled && binding.startSeedingSwitch.isChecked

        val progressBinding = DialogProgressBinding.inflate(LayoutInflater.from(this))
        progressBinding.progressBar.max = 1000
        progressBinding.progressBar.progress = 0
        progressBinding.progressText.text = getString(R.string.create_torrent_hashing)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_torrent_title)
            .setView(progressBinding.root)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()
        progressDialog = dialog

        hashJob = lifecycleScope.launch {
            var lastUi = 0L
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    TorrentMaker(contentResolver).build(uri, sourceIsTree, options) { done, total ->
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 150) {
                            lastUi = now
                            runOnUiThread {
                                if (total > 0 && progressBinding.root.isAttachedToWindow) {
                                    progressBinding.progressBar.progress =
                                        ((done * 1000) / total).toInt()
                                    progressBinding.progressText.text = getString(
                                        R.string.create_torrent_hashing_fmt,
                                        (done * 100 / total).toInt(),
                                        Format.size(done), Format.size(total),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            dialog.dismiss()
            result
                .onSuccess { made ->
                    madeResult = made
                    var seeded = false
                    val path = realPath
                    if (wantSeeding && path != null) {
                        seeded = withContext(Dispatchers.IO) {
                            runCatching {
                                ServiceLocator.repository(this@CreateTorrentActivity).addTorrent(
                                    urls = null,
                                    fileBytes = made.bytes,
                                    fileName = "${made.name}.torrent",
                                    savePath = path,
                                    category = binding.categoryInput.text?.toString()?.trim()
                                        ?.takeIf { it.isNotEmpty() },
                                    skipChecking = true,
                                )
                                true
                            }.getOrDefault(false)
                        }
                    }
                    showDoneDialog(made, seeded)
                }
                .onFailure { e ->
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Toast.makeText(
                            this@CreateTorrentActivity,
                            getString(R.string.create_torrent_failed_fmt, e.message ?: "?"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
        }
        dialog.setOnDismissListener {
            // cancel button (or any dismissal) cancels the hashing job
            hashJob?.takeIf { j -> j.isActive }?.cancel()
        }
    }

    private fun showDoneDialog(made: TorrentMaker.Result, seeded: Boolean) {
        val message = StringBuilder()
            .append(getString(R.string.create_torrent_done)).append('\n')
            .append(getString(R.string.torrent_size)).append(": ")
            .append(Format.size(made.totalSize)).append('\n')
            .append(getString(R.string.create_torrent_piece_size)).append(": ")
            .append(
                if (made.pieceSize >= (1L shl 20)) "${made.pieceSize shr 20} MiB"
                else "${made.pieceSize shr 10} KiB"
            )
        if (seeded) {
            message.append('\n').append(getString(R.string.create_torrent_seeding_added))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_torrent_title)
            .setMessage(message)
            .setPositiveButton(R.string.create_torrent_save_file) { _, _ ->
                saveFile.launch("${made.name}.torrent")
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun writeTorrentFile(uri: Uri) {
        val bytes = madeResult?.bytes ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CreateTorrentActivity,
                    getString(
                        if (ok) R.string.create_torrent_saved_fmt else R.string.update_download_failed_fmt,
                        madeResult?.name ?: "torrent",
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val STATE_SOURCE = "state_source"
        private const val STATE_IS_TREE = "state_is_tree"
        private const val STATE_REAL_PATH = "state_real_path"
        private const val STATE_SOURCE_NAME = "state_source_name"
        private const val STATE_HAS_PIECE = "state_has_piece"
        private const val STATE_PIECE = "state_piece"
        private const val STATE_MADE_FILE = "state_made_file"
        private const val STATE_MADE_NAME = "state_made_name"
        private const val STATE_MADE_PIECE = "state_made_piece"
        private const val STATE_MADE_TOTAL = "state_made_total"
        private const val STATE_MADE_COUNT = "state_made_count"
        private const val PENDING_TORRENT_FILE = "pending_created.torrent"

        /** Opens the torrent creator from the FAB action sheet. */
        fun start(context: android.content.Context) {
            context.startActivity(Intent(context, CreateTorrentActivity::class.java))
        }
    }
}
