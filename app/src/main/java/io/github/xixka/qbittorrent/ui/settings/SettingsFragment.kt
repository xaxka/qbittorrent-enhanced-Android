package io.github.xixka.qbittorrent.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.xixka.qbittorrent.BuildConfig
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import io.github.xixka.qbittorrent.databinding.FragmentSettingsListBinding
import io.github.xixka.qbittorrent.ui.main.MainActivity
import io.github.xixka.qbittorrent.ui.qbsettings.QBSettingsFragment
import io.github.xixka.qbittorrent.ui.log.LogFragment
import io.github.xixka.qbittorrent.ui.settings.ServerSettingsFragment
import io.github.xixka.qbittorrent.util.ThemeUtils
import io.github.xixka.qbittorrent.util.UpdateChecker
import kotlinx.coroutines.launch

/**
 * Settings hub, LibreTorrent-style preference list. Shown IN PLACE by the
 * bottom navigation of MainActivity (no separate activity); subpages
 * (qBittorrent options editor, server connections, log viewer) are pushed
 * onto the same in-place container — never a new window. RSS has its own
 * bottom-navigation tab; statistics live behind the drawer's listening-
 * port / DHT rows (popup dialog); engine boot-autostart + watchdog are
 * always-on internals — no entries duplicated here.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SettingsRowAdapter

    private val rows = mutableListOf<Any>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.settingsToolbar.setTitle(R.string.settings)
        adapter = SettingsRowAdapter(::onRowClick)
        binding.settingsList.layoutManager = LinearLayoutManager(requireContext())
        binding.settingsList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        rebuildRows()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private val prefs get() = ServiceLocator.prefs(requireContext())

    // ---------------- rows ----------------

    private fun rebuildRows() {
        rows.clear()
        rows += Header(R.string.settings_qbt_section)
        rows += Item(
            id = ID_QB_SETTINGS,
            icon = R.drawable.ic_tune_24px,
            title = getString(R.string.settings_qbt_open),
            summary = getString(R.string.settings_qbt_open_sub),
        )
        rows += Item(
            id = ID_LOG,
            icon = R.drawable.ic_article_24px,
            title = getString(R.string.log_title),
            summary = getString(R.string.log_title_sub),
        )

        rows += Header(R.string.settings_appearance)
        rows += Item(
            id = ID_DYNAMIC_COLORS,
            icon = R.drawable.ic_palette_24px,
            title = getString(R.string.pref_dynamic_colors),
            summary = if (ThemeUtils.supportsDynamicColors) {
                getString(R.string.pref_dynamic_colors_sub)
            } else {
                getString(R.string.pref_dynamic_colors_unsupported)
            },
            switch = true,
            checked = prefs.dynamicColors,
            enabled = ThemeUtils.supportsDynamicColors,
        )
        rows += Item(
            id = ID_THEME,
            icon = R.drawable.ic_qbittorrent_logo,
            title = getString(R.string.pref_theme),
            summary = themeLabel(prefs.themeMode),
        )
        rows += Item(
            id = ID_SHOW_RSS,
            icon = R.drawable.ic_rss_feed_24px,
            title = getString(R.string.pref_show_rss),
            summary = getString(R.string.pref_show_rss_sub),
            switch = true,
            checked = prefs.showRss,
        )

        rows += Header(R.string.settings_behavior)
        rows += Item(
            id = ID_POLL_INTERVAL,
            icon = R.drawable.ic_schedule_24px,
            title = getString(R.string.settings_poll_label),
            summary = getString(R.string.pref_poll_interval_sub, prefs.pollIntervalSec),
        )
        // Engine lifecycle (boot autostart + watchdog) is always-on internal
        // behavior — no toggle rows, the engine is simply managed for the
        // user and only surfaces a retry prompt if it fails to start.

        rows += Header(R.string.settings_connection)
        rows += Item(
            id = ID_SERVER,
            icon = R.drawable.ic_dns_24px,
            title = getString(R.string.settings_server_connection),
            summary = connectionSummary(),
        )
        // No "use remote server" row here: switching between the bundled
        // engine and remote servers belongs INSIDE the server-connection
        // screen (its top switch) — a single canonical entry.

        rows += Header(R.string.settings_about)
        rows += Item(
            id = ID_VERSION,
            icon = R.drawable.ic_info_24px,
            title = getString(R.string.settings_version),
            summary = BuildConfig.VERSION_NAME,
        )
        rows += Item(
            id = ID_CHECK_UPDATE,
            icon = R.drawable.ic_refresh_24px,
            title = getString(R.string.check_for_updates),
            summary = null,
        )
        rows += Item(
            id = ID_ABOUT,
            icon = R.drawable.ic_help_24px,
            title = getString(R.string.about),
            summary = null,
        )
        adapter.submit(rows)
    }

    private fun connectionSummary(): String =
        if (prefs.usingLocalEngine) {
            getString(R.string.settings_server_connection_engine)
        } else {
            val profiles = prefs.serverProfiles()
            when {
                profiles.isEmpty() -> getString(R.string.settings_server_connection_not_configured)
                else -> {
                    val active = prefs.activeServer()
                    getString(
                        R.string.settings_server_connection_fmt,
                        active?.displayName() ?: "",
                        profiles.size,
                    )
                }
            }
        }

    private fun themeLabel(mode: String): String = when (mode) {
        ThemeUtils.MODE_LIGHT -> getString(R.string.theme_light)
        ThemeUtils.MODE_DARK -> getString(R.string.theme_dark)
        else -> getString(R.string.theme_system)
    }

    // ---------------- interactions ----------------

    private fun onRowClick(item: Item, checked: Boolean) {
        when (item.id) {
            ID_QB_SETTINGS -> push(QBSettingsFragment())
            ID_LOG -> push(LogFragment())

            ID_DYNAMIC_COLORS -> {
                prefs.dynamicColors = checked
                activity?.recreate()
            }

            ID_SHOW_RSS -> prefs.showRss = checked

            ID_THEME -> showThemeDialog()

            ID_POLL_INTERVAL -> showPollIntervalDialog()

            ID_SERVER -> push(ServerSettingsFragment())

            ID_CHECK_UPDATE -> checkUpdate()

            ID_ABOUT -> showAboutDialog()

            ID_VERSION -> showAboutDialog()
        }
    }

    /** Opens a settings subpage in the same window (in-place push). */
    private fun push(fragment: androidx.fragment.app.Fragment) {
        (activity as? MainActivity)?.pushPage(fragment)
    }

    private fun showThemeDialog() {
        val modes = listOf(ThemeUtils.MODE_SYSTEM, ThemeUtils.MODE_LIGHT, ThemeUtils.MODE_DARK)
        val labels = modes.map { themeLabel(it) }.toTypedArray()
        val current = modes.indexOf(prefs.themeMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pref_theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.themeMode = modes[which]
                ThemeUtils.applyThemeMode(modes[which])
                dialog.dismiss()
                rebuildRows()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPollIntervalDialog() {
        val layout = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input, null)
        layout.findViewById<TextInputLayout>(R.id.inputLayout)?.hint =
            getString(R.string.settings_poll_label)
        val input = layout.findViewById<TextInputEditText>(R.id.input)
        input?.setText(prefs.pollIntervalSec.toString())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_poll_label)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input?.text?.toString()?.trim()?.toIntOrNull()?.let {
                    prefs.pollIntervalSec = it.coerceIn(1, 60)
                }
                rebuildRows()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- update check ----------------

    private fun checkUpdate() {
        prefs.lastUpdateCheck = System.currentTimeMillis()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { UpdateChecker.check() }
            result
                .onSuccess { update ->
                    if (update != null) showUpdateDialog(update) else showUpdateToast()
                }
                .onFailure { showUpdateToast() }
        }
    }

    private fun showUpdateToast() {
        android.widget.Toast.makeText(
            requireContext(),
            getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showUpdateDialog(update: UpdateChecker.Update) {
        val notes = if (update.notes.isBlank()) "" else "\n\n" + update.notes.take(600)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_message, update.version) + notes)
            .setPositiveButton(R.string.update_download) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.apkUrl ?: update.htmlUrl)),
                    )
                }
            }
            .setNeutralButton(R.string.update_release_page) { _, _ ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.htmlUrl)))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about)
            .setMessage(
                getString(R.string.about_message) +
                    "\n\n" + getString(R.string.about_version, BuildConfig.VERSION_NAME)
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------------- adapter ----------------

    /** Section header row. */
    private class Header(val titleRes: Int)

    /** Clickable settings row, optionally carrying a switch. */
    private data class Item(
        val id: Int,
        val icon: Int,
        val title: String,
        val summary: String?,
        val switch: Boolean = false,
        val checked: Boolean = false,
        val enabled: Boolean = true,
    )

    private inner class SettingsRowAdapter(
        private val onClick: (Item, Boolean) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<Any>()
        private val viewTypeHeader = 0
        private val viewTypeRow = 1

        fun submit(newItems: List<Any>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int =
            if (items[position] is Header) viewTypeHeader else viewTypeRow

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == viewTypeHeader) {
                HeaderHolder(inflater.inflate(R.layout.item_settings_header, parent, false))
            } else {
                RowHolder(inflater.inflate(R.layout.item_settings_row, parent, false))
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Header -> (holder as HeaderHolder).text.setText(item.titleRes)
                is Item -> (holder as RowHolder).bind(item)
            }
        }

        private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view as TextView
        }

        private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.rowIcon)
            private val title: TextView = view.findViewById(R.id.rowTitle)
            private val summary: TextView = view.findViewById(R.id.rowSummary)
            private val switch: MaterialSwitch = view.findViewById(R.id.rowSwitch)
            private val chevron: ImageView = view.findViewById(R.id.rowChevron)

            fun bind(item: Item) {
                icon.setImageResource(item.icon)
                title.text = item.title
                if (item.summary.isNullOrBlank()) {
                    summary.visibility = View.GONE
                } else {
                    summary.visibility = View.VISIBLE
                    summary.text = item.summary
                }
                itemView.isEnabled = item.enabled
                itemView.alpha = if (item.enabled) 1f else 0.45f
                if (item.switch) {
                    switch.visibility = View.VISIBLE
                    chevron.visibility = View.GONE
                    switch.isChecked = item.checked
                    switch.isEnabled = item.enabled
                } else {
                    switch.visibility = View.GONE
                    chevron.visibility = if (item.enabled) View.VISIBLE else View.GONE
                }
                itemView.setOnClickListener {
                    if (item.switch) {
                        switch.isChecked = !switch.isChecked
                    }
                    onClick(item, if (item.switch) switch.isChecked else false)
                }
            }
        }
    }

    companion object {
        private const val ID_QB_SETTINGS = 1
        private const val ID_DYNAMIC_COLORS = 2
        private const val ID_THEME = 3
        private const val ID_SHOW_RSS = 5
        private const val ID_POLL_INTERVAL = 4
        private const val ID_SERVER = 7
        private const val ID_VERSION = 8
        private const val ID_CHECK_UPDATE = 9
        private const val ID_ABOUT = 10
        private const val ID_LOG = 12
    }
}
