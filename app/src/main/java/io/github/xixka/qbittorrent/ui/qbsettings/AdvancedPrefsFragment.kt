package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsAdvancedBinding
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.bool
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.int

/**
 * Advanced tab — a curated subset of the WebUI Options "Advanced" page: the
 * libtorrent threading knobs that matter most on Android (async I/O / hashing
 * threads, checking memory) plus tracker announce options.
 */
class AdvancedPrefsFragment : QBPrefsTabFragment() {

    private var _binding: FragmentQbPrefsAdvancedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsAdvancedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun populate(prefs: JsonObject) {
        binding.asyncIoThreadsInput.setText(int(prefs, "async_io_threads", 10).toString())
        binding.hashingThreadsInput.setText(int(prefs, "hashing_threads", 2).toString())
        binding.checkingMemInput.setText(int(prefs, "checking_memory_use", 16).toString())
        binding.announceAllSwitch.isChecked = bool(prefs, "announce_to_all_trackers", false)
        binding.announceAllTiersSwitch.isChecked = bool(prefs, "announce_to_all_tiers", false)
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
