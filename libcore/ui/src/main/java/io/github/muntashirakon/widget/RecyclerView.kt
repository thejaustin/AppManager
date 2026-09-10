// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.UiUtils
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class RecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.recyclerview.R.attr.recyclerViewStyle
) : androidx.recyclerview.widget.RecyclerView(context, attrs, defStyleAttr) {

    open class AdapterDataChangedObserver : AdapterDataObserver() {
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = onChanged()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onChanged()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = onChanged()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = onChanged()
    }

    private var mEmptyView: View? = null

    private val mObserver = object : AdapterDataObserver() {
        override fun onChanged() = checkIfEmpty()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = checkIfEmpty()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = checkIfEmpty()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = checkIfEmpty()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = checkIfEmpty()
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RecyclerView)
        val fastScrollEnabled = a.getBoolean(R.styleable.RecyclerView_fastScrollerEnabled, false)
        a.recycle()
        if (fastScrollEnabled) {
            FastScrollerBuilder(this).useMd2Style().build()
        }
        UiUtils.applyWindowInsetsAsPaddingNoTop(this)
    }

    private fun checkIfEmpty() {
        if (isInEditMode) return
        val adapter = adapter ?: return
        val emptyViewVisible = adapter.itemCount == 0
        mEmptyView?.visibility = if (emptyViewVisible) VISIBLE else GONE
        visibility = if (emptyViewVisible) GONE else VISIBLE
    }

    @UiThread
    override fun setAdapter(adapter: Adapter<*>?) {
        getAdapter()?.unregisterAdapterDataObserver(mObserver)
        super.setAdapter(adapter)
        adapter?.registerAdapterDataObserver(mObserver)
        checkIfEmpty()
    }

    fun setEmptyView(emptyView: View) {
        mEmptyView = emptyView
        checkIfEmpty()
    }

    fun setSelection(position: Int) {
        (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
    }

    abstract class ListAdapter<T, VH : ViewHolder> : androidx.recyclerview.widget.ListAdapter<T, VH> {
        private var mRecyclerView: androidx.recyclerview.widget.RecyclerView? = null
        private var mPreFilterScrollState: Parcelable? = null

        protected constructor(diffCallback: DiffUtil.ItemCallback<T>) : super(diffCallback)
        protected constructor(config: AsyncDifferConfig<T>) : super(config)

        @CallSuper
        override fun onAttachedToRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            mRecyclerView = recyclerView
        }

        @CallSuper
        override fun onDetachedFromRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            mRecyclerView = null
        }

        @CallSuper
        override fun onViewAttachedToWindow(holder: VH) {
            super.onViewAttachedToWindow(holder)
            AdapterUtils.fixTextSelectionInView(holder)
        }

        fun submitListWithScrollState(list: List<T>?, saveState: Boolean, restoreState: Boolean) {
            val layoutManager = mRecyclerView?.layoutManager
            if (saveState && layoutManager != null) {
                mPreFilterScrollState = layoutManager.onSaveInstanceState()
            }
            val isCurrentlyAtTop = if (layoutManager is LinearLayoutManager) {
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                val topOffset = layoutManager.findViewByPosition(firstVisibleItem)?.top ?: 0
                firstVisibleItem == 0 && topOffset >= 0
            } else false
            super.submitList(list) {
                if (layoutManager != null) {
                    when {
                        isCurrentlyAtTop -> {
                            layoutManager.scrollToPosition(0)
                            if (restoreState) mPreFilterScrollState = null
                        }
                        restoreState && mPreFilterScrollState != null -> {
                            layoutManager.onRestoreInstanceState(mPreFilterScrollState)
                            mPreFilterScrollState = null
                        }
                    }
                }
            }
        }
    }
}
