package io.github.xixka.qbittorrent.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Compact single-row piece bar, qBC PieceBar parity: one full-width bar
 * clipped to a 4dp corner radius, where consecutive pieces with the same
 * state are merged into runs of the theme primary color at alpha 1.0
 * (downloaded), 0.5 (downloading), 0.25 (missing). Tap behaviour lives on
 * the host card.
 */
class PieceBarView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var states: List<Int> = emptyList()

    /** qBC draws the bar in colorScheme.primary, not a fixed hue. */
    private val color = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val clipPath = Path()
    private val cornerRadius = dp(4f)

    fun submit(newStates: List<Int>) {
        states = newStates
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // qBC: .clip(RoundedCornerShape(4.dp)) around the whole bar
        val save = canvas.save()
        clipPath.reset()
        rect.set(0f, 0f, w, h)
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        if (states.isEmpty()) {
            paint.color = color
            paint.alpha = 64
            canvas.drawRect(0f, 0f, w, h, paint)
            canvas.restoreToCount(save)
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
        canvas.restoreToCount(save)
    }

    private fun resolveThemeColor(attr: Int): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
