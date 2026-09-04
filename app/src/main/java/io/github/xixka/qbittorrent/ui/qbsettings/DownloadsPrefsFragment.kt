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
 * torrent, saving management (TMM defaults, default/incomplete paths),
 * export directories, autorun programs, monitored folders and file options.
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
        binding.autoTmmSwitch.isChecked = bool(prefs, "auto_tmm_enabled", true)
        binding.torrentChangedTmmSwitch.isChecked = bool(prefs, "torrent_changed_tmm_enabled", true)
        binding.savePathChangedTmmSwitch.isChecked = bool(prefs, "save_path_changed_tmm_enabled", true)
        binding.categoryChangedTmmSwitch.isChecked = bool(prefs, "category_changed_tmm_enabled", true)
        binding.useCategoryPathsSwitch.isChecked = bool(prefs, "use_category_paths_in_manual_mode", true)
        binding.exportDirInput.setText(str(prefs, "export_dir"))
        binding.exportDirFinInput.setText(str(prefs, "export_dir_fin"))
        binding.autorunEnabledSwitch.isChecked = bool(prefs, "autorun_enabled", false)
        binding.autorunProgramInput.setText(str(prefs, "autorun_program"))
        binding.autorunOnAddedSwitch.isChecked =
            bool(prefs, "autorun_on_torrent_added_enabled", false)
        binding.autorunOnAddedProgramInput.setText(str(prefs, "autorun_on_torrent_added_program"))
        binding.scanDirsInput.setText(scanDirsToLines(prefs))
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
        out.put("auto_tmm_enabled", binding.autoTmmSwitch.isChecked)
        out.put("torrent_changed_tmm_enabled", binding.torrentChangedTmmSwitch.isChecked)
        out.put("save_path_changed_tmm_enabled", binding.savePathChangedTmmSwitch.isChecked)
        out.put("category_changed_tmm_enabled", binding.categoryChangedTmmSwitch.isChecked)
        out.put("use_category_paths_in_manual_mode", binding.useCategoryPathsSwitch.isChecked)
        out.put("export_dir", binding.exportDirInput.text?.toString()?.trim().orEmpty())
        out.put("export_dir_fin", binding.exportDirFinInput.text?.toString()?.trim().orEmpty())
        out.put("autorun_enabled", binding.autorunEnabledSwitch.isChecked)
        out.put("autorun_program", binding.autorunProgramInput.text?.toString()?.trim().orEmpty())
        out.put(
            "autorun_on_torrent_added_enabled",
            binding.autorunOnAddedSwitch.isChecked,
        )
        out.put(
            "autorun_on_torrent_added_program",
            binding.autorunOnAddedProgramInput.text?.toString()?.trim().orEmpty(),
        )
        out.add("scan_dirs", scanDirsToJson(binding.scanDirsInput.text?.toString().orEmpty()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Monitored folders, one per line: `path` (default save location),
     * `path|self` (save inside the monitored folder) or `path|savePath`.
     */
    private fun scanDirsToLines(prefs: JsonObject): String {
        val dirs = prefs.get("scan_dirs")?.takeIf { it.isJsonObject }?.asJsonObject ?: return ""
        return dirs.entrySet().joinToString("\n") { (path, value) ->
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber ->
                    if (value.asInt == 0) "$path|self" else path
                else -> "$path|${value.asString}"
            }
        }
    }

    private fun scanDirsToJson(lines: String): com.google.gson.JsonObject {
        val result = com.google.gson.JsonObject()
        lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val pipe = line.indexOf('|')
                if (pipe < 0) {
                    result.addProperty(line, 1)
                } else {
                    val path = line.substring(0, pipe).trim()
                    val rest = line.substring(pipe + 1).trim()
                    if (path.isNotEmpty()) {
                        if (rest == "self") result.addProperty(path, 0)
                        else if (rest.isEmpty()) result.addProperty(path, 1)
                        else result.addProperty(path, rest)
                    }
                }
            }
        return result
    }
}
