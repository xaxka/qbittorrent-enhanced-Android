package io.github.xixka.qbittorrent.ui.qbsettings

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.github.xixka.qbittorrent.R

/**
 * Renders one preferences section as a generated form: headers, switches,
 * text/numeric inputs, multiline codec editors and dropdowns. All values are
 * read from and written back to the shared [QBSettingsViewModel] working
 * copy, so no state lives in the views themselves.
 */
class QBPrefsAdapter(
    private val vm: QBSettingsViewModel,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var entries: List<PrefEntry> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val viewTypeHeader = 0
    private val viewTypeSwitch = 1
    private val viewTypeInput = 2
    private val viewTypeDropdown = 3

    override fun getItemViewType(position: Int): Int = when (val e = entries[position]) {
        is PrefEntry.Header -> viewTypeHeader
        is PrefEntry.Row -> when (e.field.kind) {
            PrefKind.BOOL -> viewTypeSwitch
            PrefKind.DROPDOWN -> viewTypeDropdown
            else -> viewTypeInput
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            viewTypeHeader -> HeaderHolder(inflater.inflate(R.layout.item_pref_header, parent, false))
            viewTypeSwitch -> SwitchHolder(inflater.inflate(R.layout.item_pref_switch, parent, false))
            viewTypeDropdown -> DropdownHolder(inflater.inflate(R.layout.item_pref_dropdown, parent, false))
            else -> InputHolder(inflater.inflate(R.layout.item_pref_input, parent, false))
        }
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val e = entries[position]) {
            is PrefEntry.Header -> (holder as HeaderHolder).bind(e)
            is PrefEntry.Row -> when (holder) {
                is SwitchHolder -> holder.bind(e.field)
                is InputHolder -> holder.bind(e.field)
                is DropdownHolder -> holder.bind(e.field)
                else -> Unit
            }
        }
    }

    private fun label(field: PrefField, context: android.content.Context): String =
        field.labelText ?: context.getString(field.label)

    // ---------------- holders ----------------

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: android.widget.TextView = view.findViewById(R.id.prefHeader)

        fun bind(header: PrefEntry.Header) {
            text.setText(header.label)
        }
    }

    private inner class SwitchHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val switch: MaterialSwitch = view.findViewById(R.id.prefSwitch)

        fun bind(field: PrefField) {
            switch.setOnCheckedChangeListener(null)
            switch.text = label(field, itemView.context)
            switch.isChecked = asBoolean(vm.value(field.key), field)
            switch.setOnCheckedChangeListener { _, checked ->
                vm.setValue(field.key, JsonPrimitive(checked))
            }
        }

        private fun asBoolean(element: JsonElement?, field: PrefField): Boolean {
            if (element != null && element.isJsonPrimitive) {
                return runCatching { element.asBoolean }.getOrDefault(field.def as? Boolean ?: false)
            }
            return field.def as? Boolean ?: false
        }
    }

    private inner class InputHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val layout: TextInputLayout = view.findViewById(R.id.prefInputLayout)
        private val input: TextInputEditText = view.findViewById(R.id.prefInput)
        private var watcher: TextWatcher? = null

        fun bind(field: PrefField) {
            layout.hint = label(field, itemView.context)
            input.removeTextChangedListener(watcher)
            applyInputType(field)
            input.setText(displayText(field))
            watcher = watcherFor(field)
            input.addTextChangedListener(watcher)
        }

        private fun applyInputType(field: PrefField) {
            input.inputType = when (field.kind) {
                PrefKind.INT, PrefKind.LONG -> InputType.TYPE_CLASS_NUMBER
                PrefKind.FLOAT -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                PrefKind.PASSWORD -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        }

        private fun displayText(field: PrefField): String {
            val element = vm.value(field.key) ?: return ""
            if (field.codec != PrefCodec.NONE && element.isJsonObject) {
                return QBPrefCodecs.structuredToLines(field.codec, element.asJsonObject)
            }
            if (!element.isJsonPrimitive) return element.toString()
            return when (field.unit) {
                PrefUnit.KIB_PER_SEC -> {
                    val bytes = runCatching { element.asDouble }.getOrDefault(0.0)
                    ((bytes.coerceAtLeast(0.0)) / 1024.0).let {
                        if (it == Math.floor(it)) it.toLong().toString() else it.toString()
                    }
                }

                else -> numberToString(element)
            }
        }

        private fun numberToString(element: JsonElement): String {
            if (element.asJsonPrimitive.isNumber) {
                val d = runCatching { element.asDouble }.getOrDefault(0.0)
                return if (d == Math.floor(d) && !d.isNaN()) d.toLong().toString() else d.toString()
            }
            return runCatching { element.asString }.getOrDefault("")
        }

        private fun watcherFor(field: PrefField): TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: return
                when {
                    field.codec != PrefCodec.NONE ->
                        vm.setValue(
                            field.key,
                            QBPrefCodecs.linesToStructured(field.codec, text),
                        )

                    field.kind == PrefKind.INT || field.kind == PrefKind.LONG -> {
                        val trimmed = text.trim()
                        if (trimmed.isEmpty()) {
                            if (field.blankKeepsValue) vm.revert(field.key)
                            return
                        }
                        trimmed.toLongOrNull()?.let { raw ->
                            vm.setValue(field.key, JsonPrimitive(clamp(toWireValue(raw, field), field)))
                        }
                    }

                    field.kind == PrefKind.FLOAT -> {
                        val trimmed = text.trim()
                        if (trimmed.isEmpty()) {
                            if (field.blankKeepsValue) vm.revert(field.key)
                            return
                        }
                        trimmed.toDoubleOrNull()?.let { raw ->
                            vm.setValue(field.key, JsonPrimitive(clamp(toWireValue(raw, field), field)))
                        }
                    }

                    else -> {
                        val raw = vm.rawValue(field.key)
                        if (raw != null && !raw.isJsonPrimitive) {
                            // structured value (object/array of an unknown
                            // key) edited as raw JSON: parse it back
                            runCatching { JsonParser.parseString(text) }
                                .onSuccess { vm.setValue(field.key, it) }
                        } else if (text.isBlank() && field.blankKeepsValue) {
                            // declared "blank keeps the server value": revert
                            // instead of storing an empty string
                            vm.revert(field.key)
                        } else if (field.kind == PrefKind.PASSWORD) {
                            vm.setValue(field.key, JsonPrimitive(text))
                        } else {
                            vm.setValue(field.key, JsonPrimitive(text.trim()))
                        }
                    }
                }
            }
        }

        /**
         * The editor shows display units (KiB/s) while the wire value is
         * bytes/s: mirror [displayText]'s division on the way back in.
         */
        private fun toWireValue(raw: Long, field: PrefField): Long =
            if (field.unit == PrefUnit.KIB_PER_SEC) raw * 1024L else raw

        private fun toWireValue(raw: Double, field: PrefField): Double =
            if (field.unit == PrefUnit.KIB_PER_SEC) raw * 1024.0 else raw

        private fun clamp(raw: Long, field: PrefField): Long {
            var value = raw
            field.min?.let { if (value < it) value = it }
            field.max?.let { if (value > it) value = it }
            return value
        }

        private fun clamp(raw: Double, field: PrefField): Double {
            var value = raw
            field.min?.let { if (value < it.toDouble()) value = it.toDouble() }
            field.max?.let { if (value > it.toDouble()) value = it.toDouble() }
            return value
        }
    }

    private inner class DropdownHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val layout: TextInputLayout = view.findViewById(R.id.prefDropdownLayout)
        private val dropdown: MaterialAutoCompleteTextView = view.findViewById(R.id.prefDropdown)

        fun bind(field: PrefField) {
            layout.hint = label(field, itemView.context)
            val labels = field.options.map { itemView.context.getString(it.label) }
            dropdown.setAdapter(
                ArrayAdapter(itemView.context, android.R.layout.simple_list_item_1, labels),
            )
            val index = selectedIndex(field)
            // -1 (server value outside the known options) falls back to the
            // raw wire value as text instead of a blank field, so the user
            // always sees what the server actually holds.
            val rawText = vm.value(field.key)
                ?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asJsonPrimitive.asString }.getOrNull() }
            val shown = labels.getOrElse(index) { rawText ?: field.def?.toString() ?: "" }
            dropdown.setText(shown, false)
            dropdown.setOnItemClickListener { _, _, position, _ ->
                val option = field.options.getOrNull(position) ?: return@setOnItemClickListener
                vm.setValue(field.key, encodeSelection(field, option.value))
            }
        }

        /** Index of the option matching the current (or default) value. */
        private fun selectedIndex(field: PrefField): Int {
            val element = vm.value(field.key)
            if (element == null || !element.isJsonPrimitive) {
                val def = field.def ?: return -1
                return field.options.indexOfFirst { it.value == def }.takeIf { it >= 0 } ?: -1
            }
            if (field.legacyNumeric && element.asJsonPrimitive.isNumber) {
                val code = runCatching { element.asInt }.getOrDefault(-999)
                val index = LEGACY_NUMERIC_CODES.indexOfFirst { it == code }
                if (index >= 0) return index
            }
            field.options.forEachIndexed { index, option ->
                val value = option.value
                val matches = when {
                    value is String && element.asJsonPrimitive.isString ->
                        runCatching { element.asString }.getOrDefault("\u0000") == value

                    value is Int && element.asJsonPrimitive.isNumber ->
                        runCatching { element.asInt }.getOrDefault(Int.MIN_VALUE) == value

                    else -> element.asString == value.toString()
                }
                if (matches) return index
            }
            return -1
        }

        private fun encodeSelection(field: PrefField, value: Any): JsonPrimitive {
            if (field.legacyNumeric) {
                val raw = vm.rawValue(field.key)
                if (raw != null && raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber) {
                    val index = field.options.indexOfFirst { it.value == value }
                    val code = LEGACY_NUMERIC_CODES.getOrNull(index)
                    if (code != null) return JsonPrimitive(code)
                }
            }
            return when (value) {
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }
    }

    companion object {
        /**
         * qBittorrent < 4.6 serialized proxy_type as a numeric enum with
         * non-contiguous codes (None/HTTP/SOCKS5/SOCKS4 -> 0/1/2/5). When the
         * server speaks that dialect the editor answers in kind.
         */
        private val LEGACY_NUMERIC_CODES = intArrayOf(0, 1, 2, 5)
    }
}
