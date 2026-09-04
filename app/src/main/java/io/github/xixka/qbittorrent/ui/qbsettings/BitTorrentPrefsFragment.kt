package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsBittorrentBinding
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.bool
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.double
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.int
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.str

/**
 * BitTorrent tab — the WebUI Options "BitTorrent" page: privacy settings
 * (DHT/PEX/LSD/encryption/anonymous mode), queueing limits and share-ratio
 * (seeding) limits.
 */
class BitTorrentPrefsFragment : QBPrefsTabFragment() {

    private var _binding: FragmentQbPrefsBittorrentBinding? = null
    private val binding get() = _binding!!

    private lateinit var encryption: DropdownField
    private lateinit var ratioAction: DropdownField

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsBittorrentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        encryption = DropdownField(
            requireContext(),
            binding.encryptionDropdown,
            listOf(
                getString(R.string.qbt_encryption_prefer),
                getString(R.string.qbt_encryption_require),
                getString(R.string.qbt_encryption_disable),
            ),
        )
        ratioAction = DropdownField(
            requireContext(),
            binding.ratioActDropdown,
            listOf(
                getString(R.string.qbt_ratio_act_stop),
                getString(R.string.qbt_ratio_act_remove),
                getString(R.string.qbt_ratio_act_superseeding),
                getString(R.string.qbt_ratio_act_remove_content),
            ),
        )
    }

    override fun populate(prefs: JsonObject) {
        binding.dhtSwitch.isChecked = bool(prefs, "dht", true)
        binding.pexSwitch.isChecked = bool(prefs, "pex", true)
        binding.lsdSwitch.isChecked = bool(prefs, "lsd", true)
        binding.anonymousSwitch.isChecked = bool(prefs, "anonymous_mode", false)
        encryption.select(QBPrefBindings.enumInt(prefs, "encryption", 0).coerceIn(0, 2))
        binding.queueingSwitch.isChecked = bool(prefs, "queueing_enabled", true)
        binding.maxActiveDownloadsInput.setText(int(prefs, "max_active_downloads", 3).toString())
        binding.maxActiveTorrentsInput.setText(int(prefs, "max_active_torrents", 5).toString())
        binding.maxActiveUploadsInput.setText(int(prefs, "max_active_uploads", 3).toString())
        binding.maxActiveCheckingInput.setText(int(prefs, "max_active_checking_torrents", 1).toString())
        binding.dontCountSlowSwitch.isChecked = bool(prefs, "dont_count_slow_torrents", false)
        binding.maxRatioEnabledSwitch.isChecked = bool(prefs, "max_ratio_enabled", false)
        binding.maxRatioInput.setText(formatRatio(double(prefs, "max_ratio", -1.0)))
        binding.maxSeedingTimeEnabledSwitch.isChecked = bool(prefs, "max_seeding_time_enabled", false)
        binding.maxSeedingTimeInput.setText(int(prefs, "max_seeding_time", 1440).toString())
        ratioAction.select(QBPrefBindings.enumInt(prefs, "max_ratio_act", 0).coerceIn(0, 3))
        binding.addTrackersEnabledSwitch.isChecked = bool(prefs, "add_trackers_enabled", false)
        binding.addTrackersInput.setText(str(prefs, "add_trackers"))
        binding.slowDlRateInput.setKiB(int(prefs, "slow_torrent_dl_rate_threshold", 2))
        binding.slowUlRateInput.setKiB(int(prefs, "slow_torrent_ul_rate_threshold", 2))
        binding.slowInactiveTimerInput.setText(int(prefs, "slow_torrent_inactive_timer", 60).toString())
        binding.reannounceAddrSwitch.isChecked = bool(prefs, "reannounce_when_address_changed", true)
        binding.maxInactiveSeedingEnabledSwitch.isChecked =
            bool(prefs, "max_inactive_seeding_time_enabled", false)
        binding.maxInactiveSeedingTimeInput.setText(int(prefs, "max_inactive_seeding_time", 60).toString())
        binding.embeddedTrackerSwitch.isChecked = bool(prefs, "enable_embedded_tracker", false)
        binding.embeddedTrackerPortInput.setText(int(prefs, "embedded_tracker_port", 9000).toString())
        binding.embeddedTrackerFwdSwitch.isChecked = bool(prefs, "embedded_tracker_port_forwarding", false)
        binding.ipFilterEnabledSwitch.isChecked = bool(prefs, "ip_filter_enabled", false)
        binding.ipFilterPathInput.setText(str(prefs, "ip_filter_path"))
        binding.ipFilterTrackersSwitch.isChecked = bool(prefs, "ip_filter_trackers", false)
        binding.bannedIpsInput.setText(str(prefs, "banned_IPs"))
        binding.shadowBanSwitch.isChecked = bool(prefs, "shadow_ban_enabled", false)
        binding.shadowBannedIpsInput.setText(str(prefs, "shadow_banned_IPs"))
    }

    override fun collectValues(out: JsonObject) {
        out.put("dht", binding.dhtSwitch.isChecked)
        out.put("pex", binding.pexSwitch.isChecked)
        out.put("lsd", binding.lsdSwitch.isChecked)
        out.put("anonymous_mode", binding.anonymousSwitch.isChecked)
        out.put("encryption", encryption.selectedOr(0))
        out.put("queueing_enabled", binding.queueingSwitch.isChecked)
        binding.maxActiveDownloadsInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("max_active_downloads", it) }
        binding.maxActiveTorrentsInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("max_active_torrents", it) }
        binding.maxActiveUploadsInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("max_active_uploads", it) }
        binding.maxActiveCheckingInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("max_active_checking_torrents", it) }
        out.put("dont_count_slow_torrents", binding.dontCountSlowSwitch.isChecked)
        out.put("max_ratio_enabled", binding.maxRatioEnabledSwitch.isChecked)
        binding.maxRatioInput.text?.toString()?.trim()?.toDoubleOrNull()
            ?.takeIf { it >= 0.0 }?.let { out.put("max_ratio", it) }
        out.put("max_seeding_time_enabled", binding.maxSeedingTimeEnabledSwitch.isChecked)
        binding.maxSeedingTimeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("max_seeding_time", it) }
        out.put("max_ratio_act", ratioAction.selectedOr(0))
        out.put("add_trackers_enabled", binding.addTrackersEnabledSwitch.isChecked)
        out.put("add_trackers", binding.addTrackersInput.text?.toString()?.trim().orEmpty())
        binding.slowDlRateInput.toKiBBytes()?.let { out.put("slow_torrent_dl_rate_threshold", it) }
        binding.slowUlRateInput.toKiBBytes()?.let { out.put("slow_torrent_ul_rate_threshold", it) }
        binding.slowInactiveTimerInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it > 0 }?.let { out.put("slow_torrent_inactive_timer", it) }
        out.put("reannounce_when_address_changed", binding.reannounceAddrSwitch.isChecked)
        out.put("max_inactive_seeding_time_enabled", binding.maxInactiveSeedingEnabledSwitch.isChecked)
        binding.maxInactiveSeedingTimeInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it >= 0 }?.let { out.put("max_inactive_seeding_time", it) }
        out.put("enable_embedded_tracker", binding.embeddedTrackerSwitch.isChecked)
        binding.embeddedTrackerPortInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..65535 }?.let { out.put("embedded_tracker_port", it) }
        out.put("embedded_tracker_port_forwarding", binding.embeddedTrackerFwdSwitch.isChecked)
        out.put("ip_filter_enabled", binding.ipFilterEnabledSwitch.isChecked)
        out.put("ip_filter_path", binding.ipFilterPathInput.text?.toString()?.trim().orEmpty())
        out.put("ip_filter_trackers", binding.ipFilterTrackersSwitch.isChecked)
        out.put("banned_IPs", binding.bannedIpsInput.text?.toString()?.trim().orEmpty())
        out.put("shadow_ban_enabled", binding.shadowBanSwitch.isChecked)
        out.put("shadow_banned_IPs", binding.shadowBannedIpsInput.text?.toString()?.trim().orEmpty())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatRatio(value: Double): String =
        if (value < 0.0) "1.0" else if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun com.google.android.material.textfield.TextInputEditText.setKiB(bytesPerSec: Int) {
        setText((bytesPerSec.coerceAtLeast(0) / 1024).toString())
    }

    private fun com.google.android.material.textfield.TextInputEditText.toKiBBytes(): Int? {
        val kib = text?.toString()?.trim()?.toIntOrNull() ?: return null
        if (kib < 0) return null
        return kib * 1024
    }
}
