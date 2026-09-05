package io.github.xixka.qbittorrent.ui.detail

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.xixka.qbittorrent.R

/**
 * Shared renderer for the label/value parameter rows of the torrent detail
 * Information/Transfer cards and the peer details dialog — the Android-View
 * equivalent of qBitController's `InfoRow` composable.
 *
 * qBC keeps every card visually straight by giving all of its label columns
 * one COMMON width: it measures every label text, takes the maximum, caps it
 * at 40% of the available width and renders the label on a single
 * (end-ellipsized) line. Every value column therefore starts at the same x
 * position, every row keeps the same height, and long values (hashes, save
 * paths, comments) wrap left-aligned in their own column instead of
 * right-flushing into a ragged blob.
 *
 * Both call sites build the full row list up front and then rebind texts in
 * place on later polls (only structural changes rebuild the row views), so
 * the one-shot [alignLabels] pass is what keeps the columns stable.
 */
object DetailParamRows {

    /** Label column cap, qBC parity: 40% of the available row width. */
    private const val LABEL_WIDTH_FRACTION = 0.4f

    /** Fixed horizontal chrome of one row (label margin gap is part of the cap). */
    private const val VALUE_MARGIN_DP = 8

    /**
     * Inflates/refreshes [rows] inside [container], then aligns the label
     * columns. Rows are REUSED across polls when the structure is unchanged
     * (same count): only the two texts are rebound, so a checked chip or a
     * selection inside a value never rebuilds mid-interaction.
     */
    fun bind(
        context: Context,
        container: ViewGroup,
        rows: List<Pair<Int, String>>,
    ) {
        if (container.childCount != rows.size) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(context)
            rows.forEach { inflater.inflate(R.layout.item_detail_param, container, true) }
        }
        rows.forEachIndexed { index, (labelRes, value) ->
            val row = container.getChildAt(index)
            row.findViewById<TextView>(R.id.param_label).setText(labelRes)
            // A blank value would render an empty-looking row; the em dash
            // keeps the panel readable when the server sent nothing.
            row.findViewById<TextView>(R.id.param_value).text = value.ifBlank { "—" }
        }
        alignLabels(container)
    }

    /**
     * One-shot column alignment for dialogs that assemble their rows
     * manually (peer details): call after every row was added.
     */
    fun alignLabels(container: ViewGroup) {
        var maxWidth = 0f
        for (index in 0 until container.childCount) {
            val row = container.getChildAt(index) as? LinearLayout ?: continue
            val label = row.findViewById<TextView>(R.id.param_label) ?: continue
            val text = label.text?.toString().orEmpty()
            if (text.isEmpty()) continue
            val width = label.paint.measureText(text)
            if (width > maxWidth) maxWidth = width
        }
        if (maxWidth <= 0f) return
        val density = container.resources.displayMetrics.density
        val chrome = (VALUE_MARGIN_DP * density).toInt()
        // container.width is only valid AFTER a layout pass; during the
        // first data bind (content still being revealed) it is 0 and the
        // screen width is the closest available approximation — the cap is
        // only an upper bound, so a small deviation never breaks alignment.
        val available = if (container.width > chrome) container.width - chrome
        else container.resources.displayMetrics.widthPixels - chrome
        val cap = (available * LABEL_WIDTH_FRACTION).toInt()
        val labelWidth = maxWidth.toInt().coerceAtMost(cap).coerceAtLeast(1)
        for (index in 0 until container.childCount) {
            val row = container.getChildAt(index) as? LinearLayout ?: continue
            val label = row.findViewById<TextView>(R.id.param_label) ?: continue
            label.layoutParams = label.layoutParams.apply { width = labelWidth }
        }
    }
}
