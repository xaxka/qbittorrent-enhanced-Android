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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatRatio(value: Double): String =
        if (value < 0.0) "1.0" else if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
