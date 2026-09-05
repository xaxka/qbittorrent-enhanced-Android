package io.github.xixka.qbittorrent.ui.customviews

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.google.android.material.chip.ChipGroup
import kotlin.math.max

/**
 * A [ChipGroup] that lays its chips out in a strict grid of equal-width
 * columns (3 by default) instead of Flow's wrap-content reflow.
 *
 * Why: with wrap_content chips the drawer's filter sections break at
 * arbitrary positions — some rows carry 2 wide chips while a third would
 * still have fit, which reads as a layout bug ("3 per row should work").
 * Equal columns pin exactly N chips per row and stretch every chip to 1/N
 * of the group width, so each filter section renders as a tidy grid of
 * same-sized filter buttons, and long labels ellipsize instead of wrapping
 * the row unpredictably.
 *
 * Selection / single-selection / checked-state behavior is inherited
 * untouched — only measurement and placement are replaced.
 */
class EqualColumnsChipGroup(
    context: Context,
    attrs: AttributeSet? = null,
) : ChipGroup(context, attrs) {

    /** Number of chips per row; each takes 1/N of the group's width. */
    var columnCount: Int = DEFAULT_COLUMN_COUNT
        set(value) {
            val v = value.coerceAtLeast(1)
            if (field != v) {
                field = v
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hGap = chipSpacingHorizontal
        val vGap = chipSpacingVertical
        val cols = columnCount
        val availableWidth =
            MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight

        // Column width: an equal split of the given width. Without a width
        // hint (UNSPECIFIED) fall back to the widest child's natural size so
        // nothing collapses to zero.
        val colWidth = if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            availableWidth > 0
        ) {
            ((availableWidth - hGap * (cols - 1)) / cols).coerceAtLeast(1)
        } else {
            var natural = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (c.visibility == View.GONE) continue
                c.measure(
                    MeasureSpec.UNSPECIFIED,
                    MeasureSpec.UNSPECIFIED,
                )
                natural = max(natural, c.measuredWidth)
            }
            natural.coerceAtLeast(1)
        }

        val childWidthSpec = MeasureSpec.makeMeasureSpec(colWidth, MeasureSpec.EXACTLY)
        var totalHeight = 0
        var rowHeight = 0
        var childrenInRow = 0
        var rows = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            child.measure(childWidthSpec, getChildHeightSpec(child, heightMeasureSpec))
            rowHeight = max(rowHeight, child.measuredHeight)
            childrenInRow++
            if (childrenInRow == cols) {
                totalHeight += rowHeight
                rowHeight = 0
                childrenInRow = 0
                rows++
            }
        }
        if (childrenInRow > 0) {
            totalHeight += rowHeight
            rows++
        }
        if (rows > 1) totalHeight += vGap * (rows - 1)

        val finalWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST ->
                MeasureSpec.getSize(widthMeasureSpec)
            else -> colWidth * cols + hGap * (cols - 1) + paddingLeft + paddingRight
        }
        setMeasuredDimension(finalWidth, totalHeight + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val hGap = chipSpacingHorizontal
        val vGap = chipSpacingVertical
        val cols = columnCount
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL

        var top = paddingTop
        var rowHeight = 0
        val row = ArrayList<View>(cols)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            row.add(child)
            rowHeight = max(rowHeight, child.measuredHeight)
            if (row.size == cols) {
                placeRow(row, top, rowHeight, hGap, isRtl)
                top += rowHeight + vGap
                row.clear()
                rowHeight = 0
            }
        }
        if (row.isNotEmpty()) {
            placeRow(row, top, rowHeight, hGap, isRtl)
        }
    }

    private fun placeRow(
        row: List<View>,
        top: Int,
        rowHeight: Int,
        hGap: Int,
        isRtl: Boolean,
    ) {
        for (k in row.indices) {
            val c = row[k]
            val colWidth = c.measuredWidth
            val x = if (isRtl) {
                width - paddingRight - (k + 1) * colWidth - k * hGap
            } else {
                paddingLeft + k * (colWidth + hGap)
            }
            // vertical center within the row
            val cy = top + (rowHeight - c.measuredHeight) / 2
            c.layout(x, cy, x + colWidth, cy + c.measuredHeight)
        }
    }

    private fun getChildHeightSpec(child: View, parentHeightSpec: Int): Int =
        ViewGroup.getChildMeasureSpec(
            parentHeightSpec,
            paddingTop + paddingBottom,
            child.layoutParams.height,
        )

    companion object {
        const val DEFAULT_COLUMN_COUNT = 3
    }
}
