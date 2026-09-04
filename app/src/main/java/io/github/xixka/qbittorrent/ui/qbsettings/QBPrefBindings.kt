package io.github.xixka.qbittorrent.ui.qbsettings

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import com.google.gson.JsonObject

/**
 * Small binding helpers shared by the six preference tabs. They read/write
 * plain widgets (MaterialSwitch / TextInputEditText / dropdown menus) against
 * a `JsonObject` of the qBittorrent WebUI preferences, tolerating missing
 * keys and older number/string variants.
 */
object QBPrefBindings {

    fun bool(prefs: JsonObject?, key: String, def: Boolean): Boolean {
        val v = prefs?.get(key) ?: return def
        return if (v.isJsonPrimitive) v.asBoolean else def
    }

    fun int(prefs: JsonObject?, key: String, def: Int): Int {
        val v = prefs?.get(key) ?: return def
        return if (v.isJsonPrimitive) runCatching { v.asInt }.getOrDefault(def) else def
    }

    fun double(prefs: JsonObject?, key: String, def: Double): Double {
        val v = prefs?.get(key) ?: return def
        return if (v.isJsonPrimitive) runCatching { v.asDouble }.getOrDefault(def) else def
    }

    fun str(prefs: JsonObject?, key: String, def: String = ""): String {
        val v = prefs?.get(key) ?: return def
        return if (v.isJsonPrimitive) v.asString else def
    }

    /** Fetches an enum value that old servers serialize as a number. */
    fun enumInt(prefs: JsonObject?, key: String, def: Int): Int {
        val v = prefs?.get(key) ?: return def
        if (!v.isJsonPrimitive) return def
        return runCatching { v.asInt }.getOrDefault(def)
    }

    /** qBittorrent ≥ 4.6 serializes some enums as strings (e.g. proxy_type). */
    fun enumStr(prefs: JsonObject?, key: String, def: String): String {
        val v = prefs?.get(key) ?: return def
        if (!v.isJsonPrimitive) return def
        return runCatching { v.asString }.getOrDefault(def)
    }

    /** True when the server serialized the key as a JSON number. */
    fun isNumeric(prefs: JsonObject?, key: String): Boolean {
        val v = prefs?.get(key) ?: return false
        return v.isJsonPrimitive && v.asJsonPrimitive.isNumber
    }
}

/**
 * Read-only dropdown backed by localized labels; the value is the index of
 * the chosen option (indices map to the qBittorrent enum values 1:1).
 */
class DropdownField(
    context: Context,
    private val view: com.google.android.material.textfield.MaterialAutoCompleteTextView,
    options: List<String>,
) {
    private val labels = options.toList()

    init {
        view.inputType = InputType.TYPE_NULL
        view.imeOptions = EditorInfo.IME_ACTION_NONE
        view.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_list_item_1, labels)
        )
        view.setOnItemClickListener { _, _, position, _ -> selected = position }
    }

    var selected: Int = -1
        private set

    fun select(index: Int) {
        selected = index
        view.setText(labels.getOrElse(index) { "" }, false)
    }

    /** Selects the option whose [valueOf] maps the raw enum value to an index. */
    fun selectBy(value: Int, mapping: (Int) -> Int) = select(mapping(value))

    fun selectedOr(default: Int): Int = if (selected in labels.indices) selected else default

    fun isEmpty(): Boolean = selected !in labels.indices
}

/** Convenience: adds a boolean preference to the outgoing diff object. */
fun JsonObject.put(key: String, value: Boolean) = addProperty(key, value)

/** Convenience: adds an integer preference (serialized as JSON number). */
fun JsonObject.put(key: String, value: Int) = addProperty(key, value)

fun JsonObject.put(key: String, value: Double) = addProperty(key, value)

fun JsonObject.put(key: String, value: String) = addProperty(key, value)
