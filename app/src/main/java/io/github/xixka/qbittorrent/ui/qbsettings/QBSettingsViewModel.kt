package io.github.xixka.qbittorrent.ui.qbsettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.data.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared state of the dynamic qBittorrent preferences editor.
 *
 * Loads the live preference snapshot of the connected qBittorrent instance
 * (`GET /api/v2/app/preferences`), keeps the user's edits in an editable
 * working copy and applies them as a partial diff
 * (`POST /api/v2/app/setPreferences`) — exactly the mechanism the official
 * WebUI Options dialog uses, so every setting available there can be edited
 * here too.
 *
 * The tab rows render from [sections] (schema order, plus an "other" section
 * for keys this app build does not know) and read/write their values through
 * [value]/[setValue], so the UI itself is fully generated and version-proof.
 *
 * Scoped to the ACTIVITY (all tabs and the editor screen share one instance)
 * — using fragment-scoped viewModels here was exactly the bug that left
 * every field blank before this rewrite.
 */
class QBSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app

    /** Raw preference object as returned by the server (null until loaded). */
    val raw = MutableStateFlow<JsonObject?>(null)

    val loading = MutableStateFlow(true)

    val error = MutableStateFlow<String?>(null)

    /** Set while a save request is in flight (guards double-submission). */
    val saving = MutableStateFlow(false)

    /** Sections to render: the known schema plus dynamic "other" rows. */
    val sections = MutableStateFlow<List<PrefSection>>(QBPrefSchema.sections)

    /**
     * Signature of the instance the loaded snapshot belongs to
     * (local engine flag + active server id). Null until [load] runs.
     */
    private var loadedTarget: String? = null

    /** Editable working copy; starts as a deep copy of [raw] on every load. */
    private var editable: JsonObject = JsonObject()

    fun load() {
        loading.value = true
        error.value = null
        viewModelScope.launch {
            try {
                val prefs = withContext(Dispatchers.IO) {
                    ServiceLocator.repository(appContext).appPreferences()
                }
                raw.value = prefs
                loadedTarget = targetSignature()
                resetEditable(prefs)
                sections.value = buildSections(prefs)
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
     * Reloads when the instance being edited has changed since the last
     * [load] — e.g. the user switched the active server (or toggled the
     * local engine) while the activity-scoped ViewModel kept the previous
     * server's snapshot. Discards any in-progress edits of the stale copy.
     * Returns true when a reload was triggered.
     */
    fun reloadIfTargetChanged(): Boolean {
        if (loadedTarget != null && loadedTarget != targetSignature()) {
            load()
            return true
        }
        return false
    }

    /** Identifies the instance the editor talks to right now. */
    private fun targetSignature(): String {
        val prefs = ServiceLocator.prefs(appContext)
        return if (prefs.usingLocalEngine) {
            "local"
        } else {
            "remote:${prefs.activeServer()?.id ?: 0L}"
        }
    }

    private fun resetEditable(prefs: JsonObject) {
        val copy = JsonObject()
        for ((key, value) in prefs.entrySet()) copy.add(key, deepCopy(value))
        editable = copy
    }

    private fun deepCopy(element: JsonElement): JsonElement = element.deepCopy()

    private fun buildSections(prefs: JsonObject): List<PrefSection> {
        val unknown = prefs.keySet().filter { it !in QBPrefSchema.byKey }.sorted()
        if (unknown.isEmpty()) return QBPrefSchema.sections
        val rows = unknown.map {
            PrefEntry.Row(QBPrefSchema.inferField(it, prefs.get(it)))
        }
        return QBPrefSchema.sections + PrefSection(R.string.qbt_tab_other, rows)
    }

    /** Current editable value of [key] (null when absent). */
    fun value(key: String): JsonElement? = editable.get(key)

    /** Raw server value of [key] (null when the server did not send it). */
    fun rawValue(key: String): JsonElement? = raw.value?.get(key)

    /** Reverts [key] to the server value: absent on the server = removed. */
    fun revert(key: String) {
        val original = rawValue(key)
        if (original == null) editable.remove(key) else editable.add(key, deepCopy(original))
    }

    /** Records a user edit (main thread only). */
    fun setValue(key: String, value: JsonElement) {
        editable.add(key, value)
    }

    /**
     * Collects the editable snapshot, diffs it against the loaded one and
     * posts the changed keys. Returns a human-readable result on the
     * callback; completes with `success=true` when the server accepted the
     * update (or nothing needed changing).
     */
    fun save(onResult: (success: Boolean, message: String?) -> Unit) {
        if (saving.value) return
        val loaded = raw.value ?: run {
            onResult(false, null) // caller shows the generic "not loaded" error
            return
        }
        // client-side mirrors of the server's own validations, so the user
        // gets a clear message instead of an HTTP 400
        editable.get("web_ui_username")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it.length < 3 || it.contains(':')) {
                onResult(false, ERR_USERNAME)
                return
            }
        }
        editable.get("web_ui_password")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it.length < 6) {
                onResult(false, ERR_PASSWORD)
                return
            }
        }
        val diff = diff(editable, loaded)
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
        diff.get("save_path")?.takeIf { it.isJsonPrimitive }?.asString?.let { raw ->
            // Normalize the mirrored value to the app's canonical form (the
            // default has no trailing slash): a WebUI-entered
            // "/storage/…/qbittorrent/" would otherwise live on as a second
            // spelling of the same folder.
            val normalized = raw.trim().trimEnd('/')
            if (normalized.isNotBlank()) prefs.engineSavePath = normalized
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
