package io.github.xixka.qbittorrent.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.github.xixka.qbittorrent.R

/**
 * Compact single-row piece bar, qBC PieceBar parity: one full-width bar
 * where consecutive pieces with the same state are merged into runs —
 * primary color at alpha 1.0 (downloaded), 0.5 (downloading), 0.25
 * (missing). Tap behaviour lives on the host card.
 */
class PieceBarView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var states: List<Int> = emptyList()

    private val color = ContextCompat.getColor(context, R.color.colorPieceHave)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    fun submit(newStates: List<Int>) {
        states = newStates
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (states.isEmpty()) {
            paint.color = color
            paint.alpha = 63
            canvas.drawRect(0f, 0f, w, h, paint)
            return
        }

        val pieceWidth = w / states.size
        var i = 0
        while (i < states.size) {
            val state = states[i]
            var end = i + 1
            while (end < states.size && states[end] == state) end++
            paint.color = color
            paint.alpha = when (state) {
                2 -> 255
                1 -> 128
                else -> 64
            }
            rect.set(i * pieceWidth, 0f, end * pieceWidth, h)
            canvas.drawRect(rect, paint)
            i = end
        }
    }
}
