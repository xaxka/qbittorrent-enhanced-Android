package io.github.xixka.qbittorrent.util

import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * ONE torrent-state → progress-bar color mapping shared by the main list
 * rows and the detail overview bar, so the same state always paints the
 * same color wherever a progress bar is shown (previously the detail page
 * colored its bar while the list rows stayed plain colorPrimary — the two
 * most visible bars in the app disagreed).
 *
 * qBC TorrentStateColor parity, mapped onto M3 theme roles: downloading
 * family (incl. checking/moving) = primary, uploading family = tertiary,
 * paused families = outline, error family = error. The track is the same
 * color at 38% alpha (qBC: `progressColor.copy(alpha = 0.38f)`).
 */
object TorrentStateColors {

    /** Track alpha applied over the indicator color (qBC 0.38f). */
    private const val TRACK_ALPHA = 0x61

    /** Translucent track color derived from an indicator color. */
    fun translucentTrack(color: Int): Int = (color and 0x00FFFFFF) or (TRACK_ALPHA shl 24)

    /**
     * Resolves the (indicator, track) color pair for a raw qBittorrent
     * state string ("downloading", "stoppedUP", "error", …). The state is
     * matched case-insensitively, as the API mixes casings.
     */
    fun resolve(view: View, state: String): Pair<Int, Int> {
        val indicator = when (state.lowercase()) {
            // uploading family -> tertiary
            "uploading", "forcedup", "stalledup" ->
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorTertiary)

            // paused / stopped family -> outline
            "stoppedup", "pausedup", "stoppeddl", "pauseddl" ->
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutline)

            // broken family -> error
            "error", "missingfiles" ->
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorError)

            // everything downloading-ish (incl. stalled, queued, meta,
            // checking, moving) -> primary
            else ->
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary)
        }
        return indicator to translucentTrack(indicator)
    }
}
