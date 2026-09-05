package io.github.xixka.qbittorrent.util

import java.io.ByteArrayOutputStream

/**
 * Minimal bencode writer — just enough to emit BitTorrent metainfo
 * dictionaries (used by the on-device torrent creator).
 *
 * Supported value types: Int/Long, String (UTF-8), ByteArray (raw),
 * List&lt;*&gt; and Map&lt;String, *&gt;. Dictionary keys are emitted in
 * bytewise sorted order as the bencode specification requires.
 */
object Bencode {

    fun encode(value: Any): ByteArray {
        val out = ByteArrayOutputStream()
        write(value, out)
        return out.toByteArray()
    }

    private fun write(value: Any, out: ByteArrayOutputStream) {
        when (value) {
            is Int -> writeInteger(value.toLong(), out)
            is Long -> writeInteger(value, out)
            is String -> writeBytes(value.toByteArray(Charsets.UTF_8), out)
            is ByteArray -> writeBytes(value, out)
            is Map<*, *> -> {
                out.write('d'.code)
                val entries = value.entries
                    .filter { it.key is String && it.value != null }
                    .sortedWith(
                        compareBy { (it.key as String).toByteArray(Charsets.UTF_8).hex() }
                    )
                for (entry in entries) {
                    write(entry.key as String, out)
                    write(entry.value!!, out)
                }
                out.write('e'.code)
            }
            is List<*> -> {
                out.write('l'.code)
                for (item in value) {
                    if (item != null) write(item, out)
                }
                out.write('e'.code)
            }
            else -> throw IllegalArgumentException("Cannot bencode ${value.javaClass}")
        }
    }

    private fun writeInteger(value: Long, out: ByteArrayOutputStream) {
        out.write('i'.code)
        out.write(value.toString().toByteArray(Charsets.US_ASCII))
        out.write('e'.code)
    }

    private fun writeBytes(bytes: ByteArray, out: ByteArrayOutputStream) {
        out.write(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        out.write(':'.code)
        out.write(bytes)
    }

    /** Lowercase hex, used only as a bytewise sort key for dictionary keys. */
    private fun ByteArray.hex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }
}
