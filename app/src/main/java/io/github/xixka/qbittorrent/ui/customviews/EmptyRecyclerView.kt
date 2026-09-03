/*
 * Port of LibreTorrent EmptyRecyclerView (Apache-2.0,
 * https://github.com/proninyaroslav/libretorrent)
 */
package io.github.xixka.qbittorrent.ui.customviews

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class EmptyRecyclerView(context: Context, attrs: AttributeSet?) : RecyclerView(context, attrs) {

    private var emptyView: View? = null
    private var loadingView: View? = null

    private val observer = object : AdapterDataObserver() {
        override fun onChanged() = checkEmpty()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = checkEmpty()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = checkEmpty()
    }

    fun setEmptyView(emptyView: View?) {
        this.emptyView = emptyView
        checkEmpty()
    }

    fun setLoadingView(loadingView: View?) {
        this.loadingView = loadingView
        loadingView?.visibility = GONE
    }

    fun setLoading(isLoading: Boolean) {
        loadingView?.visibility = if (isLoading) VISIBLE else GONE
        if (isLoading) {
            emptyView?.visibility = GONE
        } else {
            checkEmpty()
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        getAdapter()?.unregisterAdapterDataObserver(observer)
        super.setAdapter(adapter)
        adapter?.registerAdapterDataObserver(observer)
        checkEmpty()
    }

    private fun checkEmpty() {
        val a = adapter
        if (emptyView != null && a != null) {
            val isEmpty = a.itemCount == 0
            emptyView?.visibility = if (isEmpty) VISIBLE else GONE
            visibility = if (isEmpty) GONE else VISIBLE
        }
    }
}
