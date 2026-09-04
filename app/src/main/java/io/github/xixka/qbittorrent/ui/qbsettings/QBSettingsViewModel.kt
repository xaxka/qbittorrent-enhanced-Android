package io.github.xixka.qbittorrent.ui.qbsettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.xixka.qbittorrent.data.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared state of the qBittorrent preferences editor.
 *
 * Loads the live preference snapshot of the connected qBittorrent instance
 * (`GET /api/v2/app/preferences`) and applies user edits as a partial diff
 * (`POST /api/v2/app/setPreferences`) — exactly the mechanism the official
 * WebUI Options dialog uses, so every setting available there can be edited
 * here too.
 */
class QBSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app

    /** Raw preference object as returned by the server (null until loaded). */
    val raw = MutableStateFlow<JsonObject?>(null)

    val loading = MutableStateFlow(true)

    val error = MutableStateFlow<String?>(null)

    /** Set while a save request is in flight (guards double-submission). */
    val saving = MutableStateFlow(false)

    private val sections = CopyOnWriteArrayList<QBPrefsSection>()

    fun register(section: QBPrefsSection) {
        if (section !in sections) sections.add(section)
    }

    fun unregister(section: QBPrefsSection) {
        sections.remove(section)
    }

    fun load() {
        loading.value = true
        error.value = null
        viewModelScope.launch {
            try {
                val prefs = withContext(Dispatchers.IO) {
                    ServiceLocator.repository(appContext).appPreferences()
                }
                raw.value = prefs
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: e.javaClass.simpleName
            } finally {
                loading.value = false
            }
        }
    }

    fun retry() = load()

    /**
     * Collects the values of every registered section, diffs them against the
     * loaded snapshot and posts the changed keys. Returns a human-readable
     * result on the callback; completes with `success=true` when the server
     * accepted the update (or nothing needed changing).
     */
    fun save(onResult: (success: Boolean, message: String?) -> Unit) {
        if (saving.value) return
        val loaded = raw.value ?: run {
            onResult(false, null) // caller shows the generic "not loaded" error
            return
        }
        val collected = JsonObject()
        for (section in sections) section.collectValues(collected)
        // client-side mirrors of the server's own validations, so the user
        // gets a clear message instead of an HTTP 400
        collected.get("web_ui_username")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it.length < 3 || it.contains(':')) {
                onResult(false, ERR_USERNAME)
                return
            }
        }
        collected.get("web_ui_password")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it.length < 6) {
                onResult(false, ERR_PASSWORD)
                return
            }
        }
        val diff = diff(collected, loaded)
        if (diff.size() == 0) {
            onResult(true, NO_CHANGES)
            return
        }
        saving.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ServiceLocator.repository(appContext).setPreferences(diff)
                }
                mirrorLocalEnginePrefs(diff)
                onResult(true, diff.size().toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(false, e.message ?: e.javaClass.simpleName)
            } finally {
                saving.value = false
            }
        }
    }

    /**
     * In the Enhanced edition with the bundled engine active, the engine
     * provisioning mirrors (WebUI port / credentials / save path) must follow
     * what the user just applied, so the engine config file and the derived
     * client endpoint stay in sync across engine restarts.
     */
    private fun mirrorLocalEnginePrefs(diff: JsonObject) {
        val prefs = ServiceLocator.prefs(appContext)
        if (!prefs.usingLocalEngine) return
        diff.get("web_ui_port")?.takeIf { it.isJsonPrimitive }?.asInt?.let {
            if (it in 1..65535) prefs.enginePort = it
        }
        diff.get("web_ui_address")?.takeIf { it.isJsonPrimitive }?.asString?.let { address ->
            prefs.engineLanAccess = address.trim() == "*" || address.isBlank()
        }
        diff.get("web_ui_username")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it.isNotBlank()) prefs.engineUsername = it
        }
        diff.get("web_ui_password")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it.isNotBlank()) prefs.enginePassword = it
        }
        diff.get("save_path")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it.isNotBlank()) prefs.engineSavePath = it
        }
        ServiceLocator.resetClient()
    }

    companion object {
        const val NO_CHANGES = "no_changes"
        const val ERR_USERNAME = "username_short"
        const val ERR_PASSWORD = "password_short"

        /**
         * Keys of [new] whose value differs from [old] (missing counts as a
         * change). Numbers are compared numerically so `8080` (int) equals
         * `8080.0` (double) regardless of JSON number parsing.
         */
        fun diff(new: JsonObject, old: JsonObject): JsonObject {
            val result = JsonObject()
            for ((key, value) in new.entrySet()) {
                val previous = old.get(key)
                if (previous == null || !jsonEquals(value, previous)) {
                    result.add(key, value)
                }
            }
            return result
        }

        fun jsonEquals(a: JsonElement, b: JsonElement): Boolean {
            if (a is JsonPrimitive && b is JsonPrimitive) {
                if (a.isNumber && b.isNumber) {
                    val da = a.asDouble
                    val db = b.asDouble
                    return (da.isNaN() && db.isNaN()) || da == db
                }
                return a.asString == b.asString
            }
            return a == b
        }
    }
}

/** A tab of the preferences editor that contributes values on save. */
interface QBPrefsSection {
    /** Writes this section's current UI values into [out] (canonical types). */
    fun collectValues(out: JsonObject)
}
