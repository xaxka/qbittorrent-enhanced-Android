/*
 * Port of LibreTorrent EmptyListPlaceholder (Apache-2.0,
 * https://github.com/proninyaroslav/libretorrent)
 */
package io.github.xixka.qbittorrent.ui.customviews

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import io.github.xixka.qbittorrent.R

class EmptyListPlaceholder(
    context: Context,
    attrs: AttributeSet?,
) : FrameLayout(context, attrs) {

    private lateinit var textView: TextView
    private lateinit var icon: ImageView

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.EmptyListPlaceholder, 0, 0)
        try {
            val root = LayoutInflater.from(context)
                .inflate(R.layout.empty_list_placeholder, this, false)
            textView = root.findViewById(R.id.text)
            icon = root.findViewById(R.id.icon)
            addView(root)

            val textRes = a.getResourceId(R.styleable.EmptyListPlaceholder_text, -1)
            if (textRes == -1) {
                textView.text = a.getString(R.styleable.EmptyListPlaceholder_text)
            } else {
                textView.setText(textRes)
            }

            val iconRes = a.getResourceId(R.styleable.EmptyListPlaceholder_icon, -1)
            if (iconRes != -1) {
                icon.setImageResource(iconRes)
                icon.visibility = View.VISIBLE
            } else {
                icon.setImageDrawable(null)
                icon.visibility = View.GONE
            }
        } finally {
            a.recycle()
        }
    }

    fun setText(text: Int) {
        textView.setText(text)
    }

    fun setIconResource(iconRes: Int) {
        icon.setImageResource(iconRes)
    }
}
