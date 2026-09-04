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
    private lateinit var diskIoType: DropdownField
    private lateinit var diskIoReadMode: DropdownField
    private lateinit var diskIoWriteMode: DropdownField
    private lateinit var utpMixedMode: DropdownField
    private lateinit var uploadSlotsBehavior: DropdownField
    private lateinit var uploadChokingAlgorithm: DropdownField

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
        diskIoType = DropdownField(
            requireContext(),
            binding.diskIoTypeDropdown,
            listOf(
                getString(R.string.qbt_disk_io_default),
                getString(R.string.qbt_disk_io_mmap),
                getString(R.string.qbt_disk_io_posix),
                getString(R.string.qbt_disk_io_pread),
            ),
        )
        diskIoReadMode = DropdownField(
            requireContext(),
            binding.diskIoReadModeDropdown,
            listOf(
                getString(R.string.qbt_os_cache_disable),
                getString(R.string.qbt_os_cache_enable),
            ),
        )
        diskIoWriteMode = DropdownField(
            requireContext(),
            binding.diskIoWriteModeDropdown,
            listOf(
                getString(R.string.qbt_os_cache_disable),
                getString(R.string.qbt_os_cache_enable),
            ),
        )
        utpMixedMode = DropdownField(
            requireContext(),
            binding.utpMixedModeDropdown,
            listOf(
                getString(R.string.qbt_utp_prefer_tcp),
                getString(R.string.qbt_utp_proportional),
            ),
        )
        uploadSlotsBehavior = DropdownField(
            requireContext(),
            binding.uploadSlotsBehaviorDropdown,
            listOf(
                getString(R.string.qbt_slots_fixed),
                getString(R.string.qbt_slots_rate_based),
            ),
        )
        uploadChokingAlgorithm = DropdownField(
            requireContext(),
            binding.uploadChokingAlgorithmDropdown,
            listOf(
                getString(R.string.qbt_seed_round_robin),
                getString(R.string.qbt_seed_fastest_upload),
                getString(R.string.qbt_seed_anti_leech),
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

        // disk I/O modes
        diskIoType.select(QBPrefBindings.enumInt(prefs, "disk_io_type", 0).coerceIn(0, 3))
        diskIoReadMode.select(QBPrefBindings.enumInt(prefs, "disk_io_read_mode", 0).coerceIn(0, 1))
        diskIoWriteMode.select(QBPrefBindings.enumInt(prefs, "disk_io_write_mode", 1).coerceIn(0, 1))

        // libtorrent limits
        binding.filePoolSizeInput.setText(int(prefs, "file_pool_size", 40).toString())
        binding.requestQueueSizeInput.setText(int(prefs, "request_queue_size", 500).toString())
        binding.saveStatsIntervalInput.setText(int(prefs, "save_statistics_interval", 60).toString())

        // legacy disk cache (libtorrent 1.x, kept for Enhanced parity)
        binding.diskCacheInput.setText(int(prefs, "disk_cache", -1).toString())
        binding.diskCacheTtlInput.setText(int(prefs, "disk_cache_ttl", 60).toString())
        binding.diskQueueSizeInput.setText(int(prefs, "disk_queue_size", 1024).toString())

        // socket buffers
        binding.sendBufferWatermarkInput.setText(int(prefs, "send_buffer_watermark", 500).toString())
        binding.sendBufferLowWatermarkInput.setText(
            int(prefs, "send_buffer_low_watermark", 10).toString()
        )
        binding.sendBufferWatermarkFactorInput.setText(
            int(prefs, "send_buffer_watermark_factor", 50).toString()
        )
        binding.socketBacklogSizeInput.setText(int(prefs, "socket_backlog_size", 10).toString())
        binding.socketSendBufferSizeInput.setText(
            int(prefs, "socket_send_buffer_size", 0).toString()
        )
        binding.socketReceiveBufferSizeInput.setText(
            int(prefs, "socket_receive_buffer_size", 0).toString()
        )

        // libtorrent extensions
        utpMixedMode.select(QBPrefBindings.enumInt(prefs, "utp_tcp_mixed_mode", 0).coerceIn(0, 1))
        uploadSlotsBehavior.select(
            QBPrefBindings.enumInt(prefs, "upload_slots_behavior", 0).coerceIn(0, 1)
        )
        uploadChokingAlgorithm.select(
            QBPrefBindings.enumInt(prefs, "upload_choking_algorithm", 1).coerceIn(0, 2)
        )
        binding.maxHttpAnnouncesInput.setText(
            int(prefs, "max_concurrent_http_announces", 50).toString()
        )
        binding.stopTrackerTimeoutInput.setText(int(prefs, "stop_tracker_timeout", 2).toString())
        binding.peerTurnoverInput.setText(int(prefs, "peer_turnover", 8).toString())
        binding.peerTurnoverIntervalInput.setText(
            int(prefs, "peer_turnover_interval", 300).toString()
        )
        binding.peerTurnoverCutoffInput.setText(int(prefs, "peer_turnover_cutoff", 90).toString())
        binding.bdecodeDepthLimitInput.setText(int(prefs, "bdecode_depth_limit", 100).toString())
        binding.bdecodeTokenLimitInput.setText(int(prefs, "bdecode_token_limit", 10000000).toString())
        binding.hostnameCacheTtlInput.setText(int(prefs, "hostname_cache_ttl", 300).toString())
        binding.coalesceReadWriteSwitch.isChecked = bool(prefs, "enable_coalesce_read_write", false)
        binding.multiConnectionsSwitch.isChecked =
            bool(prefs, "enable_multi_connections_from_same_ip", false)
        binding.pieceExtentAffinitySwitch.isChecked =
            bool(prefs, "enable_piece_extent_affinity", false)
        binding.uploadSuggestionsSwitch.isChecked =
            bool(prefs, "enable_upload_suggestions", false)
        binding.blockPrivilegedPortsSwitch.isChecked =
            bool(prefs, "block_peers_on_privileged_ports", false)
        binding.validateHttpsTrackerSwitch.isChecked =
            bool(prefs, "validate_https_tracker_certificate", true)
        binding.idnSupportSwitch.isChecked = bool(prefs, "idn_support_enabled", false)
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

        // disk I/O modes
        out.put("disk_io_type", diskIoType.selectedOr(0))
        out.put("disk_io_read_mode", diskIoReadMode.selectedOr(0))
        out.put("disk_io_write_mode", diskIoWriteMode.selectedOr(1))

        // libtorrent limits
        binding.filePoolSizeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("file_pool_size", it) }
        binding.requestQueueSizeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("request_queue_size", it) }
        binding.saveStatsIntervalInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("save_statistics_interval", it) }

        // legacy disk cache
        binding.diskCacheInput.text?.toString()?.trim()?.toIntOrNull()
            ?.let { out.put("disk_cache", it) }
        binding.diskCacheTtlInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("disk_cache_ttl", it) }
        binding.diskQueueSizeInput.text?.toString()?.trim()?.toLongOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("disk_queue_size", it) }

        // socket buffers
        binding.sendBufferWatermarkInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("send_buffer_watermark", it) }
        binding.sendBufferLowWatermarkInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("send_buffer_low_watermark", it) }
        binding.sendBufferWatermarkFactorInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("send_buffer_watermark_factor", it) }
        binding.socketBacklogSizeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("socket_backlog_size", it) }
        binding.socketSendBufferSizeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.let { out.put("socket_send_buffer_size", it) }
        binding.socketReceiveBufferSizeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.let { out.put("socket_receive_buffer_size", it) }

        // libtorrent extensions
        out.put("utp_tcp_mixed_mode", utpMixedMode.selectedOr(0))
        out.put("upload_slots_behavior", uploadSlotsBehavior.selectedOr(0))
        out.put("upload_choking_algorithm", uploadChokingAlgorithm.selectedOr(1))
        binding.maxHttpAnnouncesInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("max_concurrent_http_announces", it) }
        binding.stopTrackerTimeoutInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("stop_tracker_timeout", it) }
        binding.peerTurnoverInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("peer_turnover", it) }
        binding.peerTurnoverIntervalInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("peer_turnover_interval", it) }
        binding.peerTurnoverCutoffInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("peer_turnover_cutoff", it) }
        binding.bdecodeDepthLimitInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("bdecode_depth_limit", it) }
        binding.bdecodeTokenLimitInput.text?.toString()?.trim()?.toLongOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("bdecode_token_limit", it) }
        binding.hostnameCacheTtlInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("hostname_cache_ttl", it) }
        out.put("enable_coalesce_read_write", binding.coalesceReadWriteSwitch.isChecked)
        out.put(
            "enable_multi_connections_from_same_ip",
            binding.multiConnectionsSwitch.isChecked,
        )
        out.put("enable_piece_extent_affinity", binding.pieceExtentAffinitySwitch.isChecked)
        out.put("enable_upload_suggestions", binding.uploadSuggestionsSwitch.isChecked)
        out.put("block_peers_on_privileged_ports", binding.blockPrivilegedPortsSwitch.isChecked)
        out.put(
            "validate_https_tracker_certificate",
            binding.validateHttpsTrackerSwitch.isChecked,
        )
        out.put("idn_support_enabled", binding.idnSupportSwitch.isChecked)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
