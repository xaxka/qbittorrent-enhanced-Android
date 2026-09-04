package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsDownloadsBinding
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.bool
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.str

/**
 * Downloads tab — the WebUI Options "Downloads" page: behaviour when adding a
 * torrent (start paused, queue top, content layout, stop condition, tracker
 * merging, .torrent auto-delete) and saving management (default/incomplete
 * paths, preallocation, .!qB extension, unwanted folder).
 */
class DownloadsPrefsFragment : QBPrefsTabFragment() {

    private var _binding: FragmentQbPrefsDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var contentLayout: DropdownField
    private lateinit var stopCondition: DropdownField
    private lateinit var autoDelete: DropdownField

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentLayout = DropdownField(
            requireContext(),
            binding.contentLayoutDropdown,
            listOf(
                getString(R.string.qbt_content_layout_original),
                getString(R.string.qbt_content_layout_subfolder),
                getString(R.string.qbt_content_layout_nosubfolder),
            ),
        )
        stopCondition = DropdownField(
            requireContext(),
            binding.stopConditionDropdown,
            listOf(
                getString(R.string.qbt_stop_condition_none),
                getString(R.string.qbt_stop_condition_metadata),
                getString(R.string.qbt_stop_condition_checked),
            ),
        )
        autoDelete = DropdownField(
            requireContext(),
            binding.autoDeleteDropdown,
            listOf(
                getString(R.string.qbt_auto_delete_never),
                getString(R.string.qbt_auto_delete_added),
                getString(R.string.qbt_auto_delete_downloaded),
            ),
        )
    }

    override fun populate(prefs: JsonObject) {
        binding.addStoppedSwitch.isChecked = bool(prefs, "add_stopped_enabled", false)
        binding.addTopSwitch.isChecked = bool(prefs, "add_to_top_of_queue", false)
        contentLayout.select(when (str(prefs, "torrent_content_layout", "Original")) {
            "Subfolder" -> 1
            "NoSubfolder" -> 2
            else -> 0
        })
        stopCondition.select(when (str(prefs, "torrent_stop_condition", "None")) {
            "MetadataReceived" -> 1
            "FilesChecked" -> 2
            else -> 0
        })
        binding.mergeTrackersSwitch.isChecked = bool(prefs, "merge_trackers", false)
        autoDelete.select(when (QBPrefBindings.enumInt(prefs, "auto_delete_mode", 0)) {
            1 -> 1
            2 -> 2
            else -> 0
        })
        binding.savePathInput.setText(str(prefs, "save_path"))
        binding.tempPathEnabledSwitch.isChecked = bool(prefs, "temp_path_enabled", false)
        binding.tempPathInput.setText(str(prefs, "temp_path"))
        binding.preallocateSwitch.isChecked = bool(prefs, "preallocate_all", false)
        binding.incompleteExtSwitch.isChecked = bool(prefs, "incomplete_files_ext", false)
        binding.unwantedFolderSwitch.isChecked = bool(prefs, "use_unwanted_folder", false)
    }

    override fun collectValues(out: JsonObject) {
        out.put("add_stopped_enabled", binding.addStoppedSwitch.isChecked)
        out.put("add_to_top_of_queue", binding.addTopSwitch.isChecked)
        out.put("torrent_content_layout", when (contentLayout.selectedOr(0)) {
            1 -> "Subfolder"
            2 -> "NoSubfolder"
            else -> "Original"
        })
        out.put("torrent_stop_condition", when (stopCondition.selectedOr(0)) {
            1 -> "MetadataReceived"
            2 -> "FilesChecked"
            else -> "None"
        })
        out.put("merge_trackers", binding.mergeTrackersSwitch.isChecked)
        out.put("auto_delete_mode", autoDelete.selectedOr(0))
        out.put("save_path", binding.savePathInput.text?.toString()?.trim().orEmpty())
        out.put("temp_path_enabled", binding.tempPathEnabledSwitch.isChecked)
        out.put("temp_path", binding.tempPathInput.text?.toString()?.trim().orEmpty())
        out.put("preallocate_all", binding.preallocateSwitch.isChecked)
        out.put("incomplete_files_ext", binding.incompleteExtSwitch.isChecked)
        out.put("use_unwanted_folder", binding.unwantedFolderSwitch.isChecked)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
