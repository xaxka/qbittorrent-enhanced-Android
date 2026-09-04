package io.github.xixka.qbittorrent.ui.qbsettings

import com.google.gson.JsonObject

/**
 * Line-based codecs for the two structured preference values, kept identical
 * to the WebUI's own representations:
 *
 *  * `scan_dirs` — watched folders, one per line:
 *    `path` (default save location), `path|self` (save inside the folder)
 *    or `path|savePath`.
 *  * `web_ui_custom_http_headers` — `Name: value` per line.
 */
object QBPrefCodecs {

    fun structuredToLines(codec: PrefCodec, value: JsonObject?): String = when (codec) {
        PrefCodec.SCAN_DIRS -> scanDirsToLines(value)
        PrefCodec.HTTP_HEADERS -> headersToLines(value)
        PrefCodec.NONE -> value?.toString() ?: ""
    }

    fun linesToStructured(codec: PrefCodec, lines: String): JsonObject = when (codec) {
        PrefCodec.SCAN_DIRS -> linesToScanDirs(lines)
        PrefCodec.HTTP_HEADERS -> linesToHeaders(lines)
        PrefCodec.NONE -> JsonObject()
    }

    fun scanDirsToLines(dirs: JsonObject?): String {
        if (dirs == null) return ""
        return dirs.entrySet().joinToString("\n") { (path, value) ->
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber ->
                    if (value.asInt == 0) "$path|self" else path

                else -> "$path|${value.asString}"
            }
        }
    }

    fun linesToScanDirs(lines: String): JsonObject {
        val result = JsonObject()
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
                        when {
                            rest == "self" -> result.addProperty(path, 0)
                            rest.isEmpty() -> result.addProperty(path, 1)
                            else -> result.addProperty(path, rest)
                        }
                    }
                }
            }
        return result
    }

    fun headersToLines(headers: JsonObject?): String {
        if (headers == null) return ""
        return headers.entrySet().joinToString("\n") { (name, value) -> "$name: ${value.asString}" }
    }

    fun linesToHeaders(lines: String): JsonObject {
        val result = JsonObject()
        lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val name = line.substring(0, colon).trim()
                    val value = line.substring(colon + 1).trim()
                    if (name.isNotEmpty()) result.addProperty(name, value)
                }
            }
        return result
    }
}
