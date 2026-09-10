package io.github.xixka.qbittorrent.ui.customviews

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Vertical gap between consecutive list items, matching qBC's
 * `Arrangement.spacedBy(8.dp)` on LazyColumn. The gap is only applied
 * between items, not after the last one.
 */
class VerticalSpaceItemDecoration(context: Context, gapDp: Float) : RecyclerView.ItemDecoration() {

    private val gap = (gapDp * context.resources.displayMetrics.density + 0.5f).toInt()

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val count = parent.adapter?.itemCount ?: 0
        if (position < count - 1) outRect.bottom = gap
    }
}
