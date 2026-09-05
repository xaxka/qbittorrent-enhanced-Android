package io.github.xixka.qbittorrent.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * Piece-state grid (qBitController PiecesBottomSheet parity): flat squares
 * of the theme primary color — 16dp cells with 4dp gaps, rows centered,
 * alpha 1.0 = downloaded, 0.5 = downloading, 0.25 = missing — fed by
 * `GET /api/v2/torrents/pieceStates`.
 */
class PieceHeatmapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var states: List<Int> = emptyList()

    private val cellSize = dp(16f)
    private val gap = dp(4f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** qBC renders the whole map in colorScheme.primary with alpha steps. */
    private val baseColor = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary)

    private val rect = RectF()

    fun submit(newStates: List<Int>) {
        val layoutNeeded = newStates.size != states.size
        states = newStates
        // The grid geometry only depends on the piece COUNT: while the count
        // is stable (the common case during 3s-interval polling) a re-layout
        // of the whole view tree is wasted work — repaint suffices.
        if (layoutNeeded) requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val columns = ((width - paddingLeft - paddingRight + gap) / (cellSize + gap))
            .toInt().coerceAtLeast(1)
        val rows = if (states.isEmpty()) 0 else (states.size + columns - 1) / columns
        val height = rows * (cellSize + gap) - gap + paddingTop + paddingBottom
        setMeasuredDimension(
            width,
            resolveSize(height.toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (states.isEmpty()) return
        val available = (width - paddingLeft - paddingRight).toFloat()
        val columns = ((available + gap) / (cellSize + gap)).toInt().coerceAtLeast(1)
        // qBC centers each row of the grid within the available width
        val rowCount = (states.size + columns - 1) / columns
        val lastRowItems = states.size - (rowCount - 1) * columns
        for ((index, state) in states.withIndex()) {
            val row = index / columns
            val col = index % columns
            val itemsInRow = if (row == rowCount - 1) lastRowItems else columns
            val rowWidth = itemsInRow * cellSize + (itemsInRow - 1) * gap
            val startX = paddingLeft + (available - rowWidth) / 2f
            paint.color = baseColor
            paint.alpha = when (state) {
                2 -> 255
                1 -> 128
                else -> 64
            }
            val left = startX + col * (cellSize + gap)
            val top = paddingTop + row * (cellSize + gap)
            rect.set(left, top, left + cellSize, top + cellSize)
            canvas.drawRect(rect, paint)
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
