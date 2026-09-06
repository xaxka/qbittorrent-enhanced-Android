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
 * same-sized filter buttons.
 *
 * Adaptive fallback: translations differ wildly in label length (a CJK
 * status fits a 1/3-of-drawer cell while the German/Russian equivalent
 * does not), so before laying out, every chip's natural width is measured;
 * when the WIDEST chip would not fit the preferred cell the grid steps
 * down one column (3 → 2 → 1) until it does. Chinese therefore keeps its
 * 3-column grid, a long-label locale simply gets a wider cell — labels
 * never ellipsize because of the grid, only through the per-chip
 * maxLines/ellipsize last resort (absurdly long custom tag names).
 *
 * Selection / single-selection / checked-state behavior is inherited
 * untouched — only measurement and placement are replaced.
 */
class EqualColumnsChipGroup(
    context: Context,
    attrs: AttributeSet? = null,
) : ChipGroup(context, attrs) {

    /** Preferred number of chips per row; each takes 1/N of the group width. */
    var columnCount: Int = DEFAULT_COLUMN_COUNT
        set(value) {
            val v = value.coerceAtLeast(1)
            if (field != v) {
                field = v
                requestLayout()
            }
        }

    /** Columns actually used by the last measure/layout pass. */
    private var laidOutColumns: Int = columnCount

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hGap = chipSpacingHorizontal
        val vGap = chipSpacingVertical
        val availableWidth =
            MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight

        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            availableWidth > 0
        ) {
            // Measure every chip at its natural width first: the column
            // decision needs the WIDEST label of this group (which varies
            // by language and by user-created category/tag names).
            var maxNatural = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (c.visibility == View.GONE) continue
                c.measure(
                    MeasureSpec.UNSPECIFIED,
                    MeasureSpec.UNSPECIFIED,
                )
                maxNatural = max(maxNatural, c.measuredWidth)
            }

            // Step down while the widest chip would not fit its cell —
            // 3 → 2 → 1, so the grid adapts instead of clipping labels.
            var cols = columnCount
            while (cols > 1 && maxNatural > colWidthFor(availableWidth, hGap, cols)) {
                cols--
            }
            laidOutColumns = cols

            val colWidth = colWidthFor(availableWidth, hGap, cols).coerceAtLeast(1)
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

            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                totalHeight + paddingTop + paddingBottom,
            )
        } else {
            // No width hint (e.g. tools preview): natural widest child so
            // nothing collapses to zero.
            laidOutColumns = columnCount
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
            val colWidth = natural.coerceAtLeast(1)
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
                if (childrenInRow == laidOutColumns) {
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
            // keep the vertical gap consistent with the width-hinted branch
            if (rows > 1) totalHeight += vGap * (rows - 1)
            setMeasuredDimension(
                laidOutColumns * colWidth + chipSpacingHorizontal * (laidOutColumns - 1) +
                    paddingLeft + paddingRight,
                totalHeight + paddingTop + paddingBottom,
            )
        }
    }

    /** Cell width for [cols] columns: an equal split minus the gaps. */
    private fun colWidthFor(availableWidth: Int, hGap: Int, cols: Int): Int =
        (availableWidth - hGap * (cols - 1)) / cols

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val hGap = chipSpacingHorizontal
        val vGap = chipSpacingVertical
        val cols = laidOutColumns
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
