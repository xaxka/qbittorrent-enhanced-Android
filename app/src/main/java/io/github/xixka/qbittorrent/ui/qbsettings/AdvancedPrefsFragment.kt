package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsAdvancedBinding
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.bool
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.int
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.str

/**
 * Advanced tab — the WebUI Options "Advanced" page: libtorrent thread counts,
 * resume-data storage, memory limits, excluded file names, peer resolution
 * behavior and the engine's log-file settings.
 */
class AdvancedPrefsFragment : QBPrefsTabFragment() {

    private var _binding: FragmentQbPrefsAdvancedBinding? = null
    private val binding get() = _binding!!

    private lateinit var resumeStorage: DropdownField
    private lateinit var logAgeType: DropdownField

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsAdvancedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resumeStorage = DropdownField(
            requireContext(),
            binding.resumeStorageDropdown,
            listOf(
                getString(R.string.qbt_resume_storage_sqlite),
                getString(R.string.qbt_resume_storage_legacy),
            ),
        )
        logAgeType = DropdownField(
            requireContext(),
            binding.fileLogAgeTypeDropdown,
            listOf(
                getString(R.string.qbt_log_age_days),
                getString(R.string.qbt_log_age_months),
                getString(R.string.qbt_log_age_years),
            ),
        )
    }

    override fun populate(prefs: JsonObject) {
        binding.asyncIoThreadsInput.setText(int(prefs, "async_io_threads", 10).toString())
        binding.hashingThreadsInput.setText(int(prefs, "hashing_threads", 2).toString())
        binding.checkingMemInput.setText(int(prefs, "checking_memory_use", 16).toString())
        binding.announceAllSwitch.isChecked = bool(prefs, "announce_to_all_trackers", false)
        binding.announceAllTiersSwitch.isChecked = bool(prefs, "announce_to_all_tiers", false)
        resumeStorage.select(
            if (str(prefs, "resume_data_storage_type", "SQLite") == "Legacy") 1 else 0
        )
        binding.saveResumeIntervalInput.setText(int(prefs, "save_resume_data_interval", 60).toString())
        binding.memoryWorkingSetInput.setText(int(prefs, "memory_working_set_limit", 512).toString())
        binding.torrentFileSizeLimitInput.setText(
            int(prefs, "torrent_file_size_limit", 100).toString()
        )
        binding.excludedNamesSwitch.isChecked = bool(prefs, "excluded_file_names_enabled", false)
        binding.excludedNamesInput.setText(str(prefs, "excluded_file_names"))
        binding.pythonPathInput.setText(str(prefs, "python_executable_path"))
        binding.confirmDeletionSwitch.isChecked = bool(prefs, "confirm_torrent_deletion", true)
        binding.confirmRecheckSwitch.isChecked = bool(prefs, "confirm_torrent_recheck", true)
        binding.deleteContentSwitch.isChecked = bool(prefs, "delete_torrent_content_files", false)
        binding.resolveCountriesSwitch.isChecked = bool(prefs, "resolve_peer_countries", true)
        binding.resolveHostNamesSwitch.isChecked = bool(prefs, "resolve_peer_host_names", false)
        binding.fileLogSwitch.isChecked = bool(prefs, "file_log_enabled", true)
        binding.fileLogPathInput.setText(str(prefs, "file_log_path"))
        binding.fileLogBackupSwitch.isChecked = bool(prefs, "file_log_backup_enabled", false)
        binding.fileLogMaxSizeInput.setText(int(prefs, "file_log_max_size", 65).toString())
        binding.fileLogDeleteOldSwitch.isChecked = bool(prefs, "file_log_delete_old", true)
        binding.fileLogAgeInput.setText(int(prefs, "file_log_age", 1).toString())
        logAgeType.select(QBPrefBindings.enumInt(prefs, "file_log_age_type", 1).coerceIn(0, 2))
    }

    override fun collectValues(out: JsonObject) {
        binding.asyncIoThreadsInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..1024 }?.let { out.put("async_io_threads", it) }
        binding.hashingThreadsInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..1024 }?.let { out.put("hashing_threads", it) }
        binding.checkingMemInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 16..4096 }?.let { out.put("checking_memory_use", it) }
        out.put("announce_to_all_trackers", binding.announceAllSwitch.isChecked)
        out.put("announce_to_all_tiers", binding.announceAllTiersSwitch.isChecked)
        out.put(
            "resume_data_storage_type",
            if (resumeStorage.selectedOr(0) == 1) "Legacy" else "SQLite",
        )
        binding.saveResumeIntervalInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("save_resume_data_interval", it) }
        binding.memoryWorkingSetInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 256.. Int.MAX_VALUE }?.let { out.put("memory_working_set_limit", it) }
        binding.torrentFileSizeLimitInput.text?.toString()?.trim()?.toLongOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("torrent_file_size_limit", it) }
        out.put("excluded_file_names_enabled", binding.excludedNamesSwitch.isChecked)
        out.put("excluded_file_names", binding.excludedNamesInput.text?.toString()?.trim().orEmpty())
        out.put("python_executable_path", binding.pythonPathInput.text?.toString()?.trim().orEmpty())
        out.put("confirm_torrent_deletion", binding.confirmDeletionSwitch.isChecked)
        out.put("confirm_torrent_recheck", binding.confirmRecheckSwitch.isChecked)
        out.put("delete_torrent_content_files", binding.deleteContentSwitch.isChecked)
        out.put("resolve_peer_countries", binding.resolveCountriesSwitch.isChecked)
        out.put("resolve_peer_host_names", binding.resolveHostNamesSwitch.isChecked)
        out.put("file_log_enabled", binding.fileLogSwitch.isChecked)
        out.put("file_log_path", binding.fileLogPathInput.text?.toString()?.trim().orEmpty())
        out.put("file_log_backup_enabled", binding.fileLogBackupSwitch.isChecked)
        binding.fileLogMaxSizeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..1024 * 1024 }?.let { out.put("file_log_max_size", it) }
        out.put("file_log_delete_old", binding.fileLogDeleteOldSwitch.isChecked)
        binding.fileLogAgeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..3650 }?.let { out.put("file_log_age", it) }
        out.put("file_log_age_type", logAgeType.selectedOr(1))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
