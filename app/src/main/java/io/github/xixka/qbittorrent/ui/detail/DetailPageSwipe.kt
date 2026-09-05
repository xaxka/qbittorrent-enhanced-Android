package io.github.xixka.qbittorrent.ui.detail

import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2

/**
 * qBC parity: swiping to another detail tab finishes the current tab's
 * action-mode selection instead of leaving a stale contextual bar with a
 * dead selection on the page the user can no longer see.
 *
 * Returns an "unregister" runnable the fragment MUST invoke from
 * onDestroyView so the callback never outlives the view.
 */
internal fun Fragment.finishSelectionOnPageSwipe(
    tabIndex: Int,
    finishSelection: () -> Unit,
): () -> Unit {
    val callback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (position != tabIndex) finishSelection()
        }
    }
    val pager = (activity as? DetailActivity)?.detailViewPager
    pager?.registerOnPageChangeCallback(callback)
    return { pager?.unregisterOnPageChangeCallback(callback) }
}
