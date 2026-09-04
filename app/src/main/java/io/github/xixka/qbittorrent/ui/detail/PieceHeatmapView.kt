package io.github.xixka.qbittorrent.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.github.xixka.qbittorrent.R

/**
 * Piece-state heatmap (qBitController PieceHeatMap parity, LibreTorrent
 * style): a compact grid of colored cells fed by `GET /api/v2/torrents/
 * pieceStates` — green = downloaded, amber = downloading/partial,
 * gray = missing. Column count adapts to the width; cells are square-ish.
 */
class PieceHeatmapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var states: List<Int> = emptyList()

    private val cellSize = dp(8)
    private val gap = dp(1)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val colorHave = ContextCompat.getColor(context, R.color.colorPieceHave)
    private val colorPartial = ContextCompat.getColor(context, R.color.colorPiecePartial)
    private val colorMissing = ContextCompat.getColor(context, R.color.colorPieceMissing)

    private val rect = RectF()

    fun submit(newStates: List<Int>) {
        states = newStates
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val columns = ((width - paddingLeft - paddingRight) / (cellSize + gap))
            .toInt().coerceAtLeast(1)
        val rows = if (states.isEmpty()) 0 else (states.size + columns - 1) / columns
        val height = rows * (cellSize + gap) + paddingTop + paddingBottom
        setMeasuredDimension(
            width,
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (states.isEmpty()) return
        val columns = ((width - paddingLeft - paddingRight) / (cellSize + gap))
            .toInt().coerceAtLeast(1)
        for ((index, state) in states.withIndex()) {
            val col = index % columns
            val row = index / columns
            paint.color = when (state) {
                2 -> colorHave
                1 -> colorPartial
                else -> colorMissing
            }
            val left = paddingLeft + col * (cellSize + gap)
            val top = paddingTop + row * (cellSize + gap)
            rect.set(left.toFloat(), top.toFloat(), (left + cellSize).toFloat(), (top + cellSize).toFloat())
            canvas.drawRoundRect(rect, dp(2).toFloat(), dp(2).toFloat(), paint)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
