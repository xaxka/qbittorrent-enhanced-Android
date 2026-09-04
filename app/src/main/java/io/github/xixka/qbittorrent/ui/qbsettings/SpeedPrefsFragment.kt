package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsSpeedBinding
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.bool
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.int

/**
 * Speed tab — the WebUI Options "Speed" page. The API stores rate limits in
 * bytes/s; the UI shows/edits KiB/s exactly like the official WebUI.
 */
class SpeedPrefsFragment : QBPrefsTabFragment() {

    private var _binding: FragmentQbPrefsSpeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var schedDays: DropdownField
    private lateinit var protocol: DropdownField

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsSpeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        schedDays = DropdownField(
            requireContext(),
            binding.schedDaysDropdown,
            listOf(
                getString(R.string.qbt_sched_day_everyday),
                getString(R.string.qbt_sched_day_weekday),
                getString(R.string.qbt_sched_day_weekend),
            ),
        )
        protocol = DropdownField(
            requireContext(),
            binding.protocolDropdown,
            listOf(
                getString(R.string.qbt_protocol_both),
                getString(R.string.qbt_protocol_tcp),
                getString(R.string.qbt_protocol_utp),
            ),
        )
    }

    override fun populate(prefs: JsonObject) {
        binding.dlLimitInput.setKiB(int(prefs, "dl_limit", 0))
        binding.upLimitInput.setKiB(int(prefs, "up_limit", 0))
        binding.altDlLimitInput.setKiB(int(prefs, "alt_dl_limit", 0))
        binding.altUpLimitInput.setKiB(int(prefs, "alt_up_limit", 0))
        binding.schedEnabledSwitch.isChecked = bool(prefs, "scheduler_enabled", false)
        binding.schedFromHourInput.setText(int(prefs, "schedule_from_hour", 8).toString())
        binding.schedFromMinInput.setText(int(prefs, "schedule_from_min", 0).toString())
        binding.schedToHourInput.setText(int(prefs, "schedule_to_hour", 20).toString())
        binding.schedToMinInput.setText(int(prefs, "schedule_to_min", 0).toString())
        schedDays.select(QBPrefBindings.enumInt(prefs, "scheduler_days", 0).coerceIn(0, 2))
        protocol.select(QBPrefBindings.enumInt(prefs, "bittorrent_protocol", 0).coerceIn(0, 2))
        binding.utpRateSwitch.isChecked = bool(prefs, "limit_utp_rate", false)
        binding.tcpOverheadSwitch.isChecked = bool(prefs, "limit_tcp_overhead", false)
        binding.lanPeersSwitch.isChecked = bool(prefs, "limit_lan_peers", true)
    }

    override fun collectValues(out: JsonObject) {
        binding.dlLimitInput.toKiBBytes()?.let { out.put("dl_limit", it) }
        binding.upLimitInput.toKiBBytes()?.let { out.put("up_limit", it) }
        binding.altDlLimitInput.toKiBBytes()?.let { out.put("alt_dl_limit", it) }
        binding.altUpLimitInput.toKiBBytes()?.let { out.put("alt_up_limit", it) }
        out.put("scheduler_enabled", binding.schedEnabledSwitch.isChecked)
        out.put("schedule_from_hour", binding.schedFromHourInput.toIntClamped(0, 23))
        out.put("schedule_from_min", binding.schedFromMinInput.toIntClamped(0, 59))
        out.put("schedule_to_hour", binding.schedToHourInput.toIntClamped(0, 23))
        out.put("schedule_to_min", binding.schedToMinInput.toIntClamped(0, 59))
        out.put("scheduler_days", schedDays.selectedOr(0))
        out.put("bittorrent_protocol", protocol.selectedOr(0))
        out.put("limit_utp_rate", binding.utpRateSwitch.isChecked)
        out.put("limit_tcp_overhead", binding.tcpOverheadSwitch.isChecked)
        out.put("limit_lan_peers", binding.lanPeersSwitch.isChecked)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun TextInputEditText.setKiB(bytesPerSec: Int) {
        setText((bytesPerSec.coerceAtLeast(0) / 1024).toString())
    }

    /** KiB/s field to bytes/s; negative/blank values are not sent. */
    private fun TextInputEditText.toKiBBytes(): Int? {
        val kib = text?.toString()?.trim()?.toIntOrNull() ?: return null
        if (kib < 0) return null
        return kib * 1024
    }

    private fun TextInputEditText.toIntClamped(min: Int, max: Int): Int =
        (text?.toString()?.trim()?.toIntOrNull() ?: min).coerceIn(min, max)
}
