package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

/**
 * Common behaviour of the six preference tabs: registers with the shared
 * [QBSettingsViewModel], fills the widgets once the preference snapshot
 * arrives and contributes values back when the user saves.
 */
abstract class QBPrefsTabFragment : Fragment(), QBPrefsSection {

    protected val vm: QBSettingsViewModel by activityViewModels()

    private var populated = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.register(this)
        viewLifecycleOwner.lifecycleScope.launch {
            vm.raw.collect { prefs ->
                if (prefs != null && !populated) {
                    populated = true
                    populate(prefs)
                }
            }
        }
    }

    override fun onDestroyView() {
        vm.unregister(this)
        super.onDestroyView()
    }

    /** Fills the widgets from the server's preference snapshot. */
    protected abstract fun populate(prefs: JsonObject)
}
