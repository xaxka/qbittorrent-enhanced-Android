/*
 * Port of LibreTorrent window-inset helpers (Apache-2.0,
 * https://github.com/proninyaroslav/libretorrent) — required by the
 * edge-to-edge layout used by the home screen.
 */
package io.github.xixka.qbittorrent.util

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object WindowInsetsSide {
    const val LEFT = 1
    const val TOP = 2
    const val RIGHT = 4
    const val BOTTOM = 8
    const val ALL = 15
}

/**
 * Pads [child] with the system bar / display cutout insets, mirroring
 * LibreTorrent's Utils.applyWindowInsets: margins are adjusted (keeping the
 * initial values), so it composes cleanly with layout XML margins.
 *
 * @param parent optional view that receives the inset dispatch (e.g. the
 * drawer container whose child should be inset); defaults to [child] itself.
 */
fun applyWindowInsets(parent: View? = null, child: View, sideMask: Int = WindowInsetsSide.ALL) {
    val baseTypeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

    val params = child.layoutParams
    val initialTop = (params as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
    val initialBottom = (params as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    val initialLeft = (params as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0
    val initialRight = (params as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(parent ?: child) { _, windowInsets ->
        val insets = windowInsets.getInsets(baseTypeMask)
        val p = child.layoutParams as? ViewGroup.MarginLayoutParams
        if (p != null) {
            if (sideMask and WindowInsetsSide.TOP != 0) p.topMargin = initialTop + insets.top
            if (sideMask and WindowInsetsSide.BOTTOM != 0) p.bottomMargin = initialBottom + insets.bottom
            if (sideMask and WindowInsetsSide.LEFT != 0) p.leftMargin = initialLeft + insets.left
            if (sideMask and WindowInsetsSide.RIGHT != 0) p.rightMargin = initialRight + insets.right
            child.layoutParams = p
        }
        WindowInsetsCompat.CONSUMED
    }
}
