package io.github.xixka.qbittorrent.util

import java.util.Locale
import kotlin.math.abs

/**
 * Formatting helpers for sizes, speeds, ETA and dates — byte units follow the
 * LibreTorrent UX (KiB/MiB/GiB).
 */
object Format {

    private const val KIB = 1024.0

    fun size(bytes: Long): String {
        if (abs(bytes) < KIB) return "$bytes B"
        val kb = bytes / KIB
        if (abs(kb) < KIB) return trim(kb) + " KiB"
        val mb = kb / KIB
        if (abs(mb) < KIB) return trim(mb) + " MiB"
        val gb = mb / KIB
        if (abs(gb) < KIB) return trim(gb) + " GiB"
        return trim(gb / KIB) + " TiB"
    }

    fun speed(bytesPerSec: Long): String = size(bytesPerSec) + "/s"

    fun progress(fraction: Double): String =
        String.format(Locale.ROOT, "%.1f%%", fraction.coerceIn(0.0, 1.0) * 100)

    /** qBittorrent ETA sentinel: 8640000 = unknown. */
    fun eta(seconds: Long): String {
        if (seconds <= 0 || seconds >= 8640000) return "∞"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "%d:%02d:%02d".format(Locale.ROOT, h, m, s)
            m > 0 -> "%d:%02d".format(Locale.ROOT, m, s)
            else -> "%ds".format(Locale.ROOT, s)
        }
    }

    fun epochDate(seconds: Long): String {
        if (seconds <= 0) return "—"
        return java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.MEDIUM,
        ).format(java.util.Date(seconds * 1000))
    }

    fun duration(seconds: Long): String {
        if (seconds <= 0) return "—"
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return when {
            d > 0 -> "%dd %02dh %02dm".format(Locale.ROOT, d, h, m)
            h > 0 -> "%dh %02dm".format(Locale.ROOT, h, m)
            else -> "%dm".format(Locale.ROOT, m)
        }
    }

    fun ratio(ratio: Double): String = String.format(Locale.ROOT, "%.2f", ratio)

    /**
     * Collapses NFO-style comment blobs: runs of 2+ newlines (possibly with
     * whitespace-only lines between them) become a single newline, every
     * line is trimmed and the whole string is trimmed. Public tracker
     * comments routinely carry dozens of blank lines which made the detail
     * overview card mostly empty space.
     */
    fun collapseBlankLines(value: String): String =
        value.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    private fun trim(value: Double): String {
        return if (value >= 100) String.format(Locale.ROOT, "%.0f", value)
        else String.format(Locale.ROOT, "%.1f", value)
    }
}
