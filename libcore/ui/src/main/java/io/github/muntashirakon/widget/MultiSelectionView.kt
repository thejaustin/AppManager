// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.CallSuper
import androidx.annotation.Px
import androidx.annotation.UiThread
import androidx.appcompat.widget.TintTypedArray
import androidx.core.os.ParcelCompat
import androidx.core.util.ObjectsCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.customview.view.AbsSavedState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.internal.ThemeEnforcement
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.multiselection.MultiSelectionActionsView
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.UiUtils
import java.util.Locale

@SuppressLint("RestrictedApi")
class MultiSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr), OnApplyWindowInsetsListener {

    interface OnSelectionChangeListener {
        @UiThread
        fun onSelectionChange(selectionCount: Int): Boolean
    }

    interface OnSelectionModeChangeListener {
        @UiThread
        fun onSelectionModeEnabled()
        @UiThread
        fun onSelectionModeDisabled()
    }

    private val mSelectionActionsView: MultiSelectionActionsView
    private val mDivider: View
    private val mCancelSelectionView: View
    private val mSelectAllView: CheckBox
    private val mSelectionCounter: TextView
    @Px private val mHorizontalMargin: Int
    @Px private val mBottomMargin: Int
    @Px private val mMaxHeight: Int
    @Px private val mTitleHeight: Int

    @Px private var mCurrentHeight: Int
    @Px private var mSelectionBottomPadding = 0
    private var mInSelectionMode = false
    private var mAdapter: Adapter<*, *>? = null
    private var mSelectionChangeListener: OnSelectionChangeListener? = null
    private var mSelectionModeChangeListener: OnSelectionModeChangeListener? = null
    private var mLastInsets: WindowInsetsCompat? = null

    init {
        val wrappedContext = getContext()
        LayoutInflater.from(wrappedContext).inflate(R.layout.view_selection_panel, this, true)
        mSelectionActionsView = findViewById(R.id.selection_actions)
        mCancelSelectionView = findViewById(R.id.action_cancel)
        mSelectAllView = findViewById(R.id.action_select_all)
        mSelectionCounter = findViewById(R.id.selection_counter)
        mDivider = findViewById(R.id.divider)

        mMaxHeight = UiUtils.dpToPx(wrappedContext, 36 + 1 + 75)
        mTitleHeight = UiUtils.dpToPx(wrappedContext, 48)
        mCurrentHeight = mMaxHeight

        mSelectionCounter.setOnClickListener {
            val listener = mAdapter?.getLayoutChangeListener()
            mAdapter?.setOnLayoutChangeListener(null)
            if (mCurrentHeight == mTitleHeight) maximize() else minimize()
            mAdapter?.setOnLayoutChangeListener(listener)
        }

        @Suppress("DEPRECATION")
        val attributes: TintTypedArray = ThemeEnforcement.obtainTintedStyledAttributes(
            wrappedContext, attrs, R.styleable.MultiSelectionView, defStyleAttr,
            com.google.android.material.R.style.Widget_MaterialComponents_CardView
        )
        setPreventCornerOverlap(false)
        setCardElevation(UiUtils.dpToPx(wrappedContext, 8).toFloat())

        mHorizontalMargin = resources.getDimensionPixelSize(R.dimen.padding_small)
        mBottomMargin = resources.getDimensionPixelSize(R.dimen.padding_very_small)

        if (attributes.hasValue(R.styleable.MultiSelectionView_menu)) {
            mSelectionActionsView.inflateMenu(attributes.getResourceId(R.styleable.MultiSelectionView_menu, 0))
        }
        attributes.recycle()

        ViewCompat.setOnApplyWindowInsetsListener(this, this)
    }

    internal class SavedState : AbsSavedState {
        var currentHeight = 0
        var selectionBottomPadding = 0
        var selectionBottomPaddingMinimum = 0
        var inSelectionMode = false

        constructor(superState: Parcelable) : super(superState)

        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            currentHeight = source.readInt()
            selectionBottomPadding = source.readInt()
            selectionBottomPaddingMinimum = source.readInt()
            inSelectionMode = ParcelCompat.readBoolean(source)
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(currentHeight)
            dest.writeInt(selectionBottomPadding)
            dest.writeInt(selectionBottomPaddingMinimum)
            ParcelCompat.writeBoolean(dest, inSelectionMode)
        }

        companion object CREATOR : Parcelable.ClassLoaderCreator<SavedState> {
            override fun createFromParcel(source: Parcel, loader: ClassLoader?): SavedState = SavedState(source, loader)
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source, null)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState() ?: return null
        val ss = SavedState(superState)
        ss.currentHeight = mCurrentHeight
        ss.selectionBottomPadding = mSelectionBottomPadding
        ss.inSelectionMode = mInSelectionMode
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            mCurrentHeight = state.currentHeight
            mSelectionBottomPadding = state.selectionBottomPadding
            mInSelectionMode = state.inSelectionMode
        } else super.onRestoreInstanceState(state)
        if (mInSelectionMode) { show(); updateCounter(false) } else updateCounter(true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateMarginAndPosition()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val lp = layoutParams as MarginLayoutParams
        mSelectionBottomPadding = height + lp.topMargin + lp.bottomMargin + UiUtils.dpToPx(context, 5)
        mAdapter?.setSelectionBottomPadding(mSelectionBottomPadding)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(mCurrentHeight, MeasureSpec.AT_MOST))
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val newInsets = if (fitsSystemWindows) insets else null
        if (!ObjectsCompat.equals(mLastInsets, newInsets)) {
            mLastInsets = newInsets
            updateMarginAndPosition()
            requestLayout()
        }
        return insets
    }

    fun getMenu(): Menu = mSelectionActionsView.getMenu()

    @Px fun getHorizontalMargin(): Int = mHorizontalMargin
    @Px fun getBottomMargin(): Int = mBottomMargin
    @Px fun getSelectionBottomPadding(): Int = mSelectionBottomPadding

    fun setAdapter(adapter: Adapter<*, *>) {
        mAdapter = adapter
        adapter.setOnLayoutChangeListener { _, rect, _ -> toggleSelectionActions(rect.height()) }
        mCancelSelectionView.setOnClickListener { adapter.cancelSelection(); hide() }
        mSelectAllView.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) adapter.selectAll() else adapter.deselectAll()
        }
        adapter.setOnSelectionChangeListener { updateCounter(false) }
    }

    @UiThread
    fun show() {
        mSelectionModeChangeListener?.onSelectionModeEnabled()
        TransitionManager.beginDelayedTransition(this, MaterialSharedAxis(MaterialSharedAxis.Y, true))
        visibility = VISIBLE
        val lp = layoutParams as MarginLayoutParams
        mSelectionBottomPadding = height + lp.topMargin + lp.bottomMargin
        mInSelectionMode = true
        mAdapter?.setInSelectionMode(true)
        mAdapter?.setSelectionBottomPadding(mSelectionBottomPadding)
    }

    fun cancel() = mCancelSelectionView.performClick()

    @Suppress("DEPRECATION")
    @UiThread
    fun hide() {
        TransitionManager.beginDelayedTransition(this, MaterialSharedAxis(MaterialSharedAxis.Y, false))
        visibility = GONE
        mSelectionBottomPadding = 0
        mInSelectionMode = false
        val adapter = mAdapter
        if (adapter != null) {
            val rv = adapter.mRecyclerView
            if (rv != null && rv.fitsSystemWindows && mLastInsets != null) {
                mSelectionBottomPadding += mLastInsets!!.systemWindowInsetBottom
            }
            adapter.setInSelectionMode(false)
            adapter.setSelectionBottomPadding(mSelectionBottomPadding)
        }
        mSelectionModeChangeListener?.onSelectionModeDisabled()
    }

    fun setOnItemSelectedListener(listener: MultiSelectionActionsView.OnItemSelectedListener) {
        mSelectionActionsView.onItemSelectedListener = listener
    }

    fun setOnSelectionChangeListener(selectionChangeListener: OnSelectionChangeListener?) {
        mSelectionChangeListener = selectionChangeListener
    }

    fun setOnSelectionModeChangeListener(selectionModeChangeListener: OnSelectionModeChangeListener?) {
        mSelectionModeChangeListener = selectionModeChangeListener
    }

    @SuppressLint("SetTextI18n")
    @UiThread
    fun updateCounter(hideOnEmpty: Boolean) {
        val adapter = mAdapter ?: run { hide(); return }
        val selectionCount = adapter.getSelectedItemCount()
        if (selectionCount <= 0 && hideOnEmpty) {
            if (visibility != GONE) hide()
            if (mSelectionChangeListener?.onSelectionChange(0) == true) mSelectionActionsView.updateMenuView()
            return
        }
        if (selectionCount > 0 && visibility != VISIBLE) show()
        val totalItems = adapter.getTotalItemCount()
        mSelectionCounter.text = String.format(Locale.getDefault(), "%d/%d", selectionCount, totalItems)
        mSelectionCounter.contentDescription = context.getString(
            R.string.selected_items_accessibility_description, selectionCount, totalItems
        )
        mSelectAllView.setChecked(adapter.areAllSelected(), false)
        if (mSelectionChangeListener?.onSelectionChange(selectionCount) == true) mSelectionActionsView.updateMenuView()
        if (!adapter.isInSelectionMode()) hide()
    }

    private fun toggleSelectionActions(recyclerViewHeight: Int) {
        if (mMaxHeight * 2 > recyclerViewHeight) minimize() else maximize()
    }

    private fun minimize() {
        mCurrentHeight = mTitleHeight
        mSelectionActionsView.visibility = GONE
        mDivider.visibility = GONE
        requestLayout()
    }

    private fun maximize() {
        mCurrentHeight = mMaxHeight
        mSelectionActionsView.visibility = VISIBLE
        mDivider.visibility = VISIBLE
        requestLayout()
    }

    @Suppress("DEPRECATION")
    private fun updateMarginAndPosition() {
        val params = layoutParams
        if (params is MarginLayoutParams) {
            var totalLeftMargin = mHorizontalMargin
            var totalRightMargin = mHorizontalMargin
            var totalBottomMargin = mBottomMargin
            if (ViewCompat.getFitsSystemWindows(this) && mLastInsets != null) {
                totalLeftMargin += mLastInsets!!.systemWindowInsetLeft
                totalRightMargin += mLastInsets!!.systemWindowInsetRight
                totalBottomMargin += mLastInsets!!.systemWindowInsetBottom
            }
            params.leftMargin = totalLeftMargin
            params.rightMargin = totalRightMargin
            params.bottomMargin = totalBottomMargin
        }
        try {
            params.javaClass.getField("gravity").set(params, Gravity.BOTTOM)
        } catch (_: NoSuchFieldException) {
        } catch (_: IllegalAccessException) {
        }
        layoutParams = params
    }

    abstract class Adapter<T, VH : RecyclerView.ViewHolder>(
        diffCallback: DiffUtil.ItemCallback<T>
    ) : ListAdapter<T, VH>(diffCallback), View.OnLayoutChangeListener {

        companion object {
            @JvmField
            val PAYLOAD_SELECTION_CHANGED: Any = Any()
        }

        private var mSelectionChangeListener: (() -> Unit)? = null
        private var mLayoutChangeListener: ((RecyclerView, Rect, Rect) -> Unit)? = null
        private var mIsInSelectionMode = false
        internal var mRecyclerView: RecyclerView? = null
        private var mDefaultBottomPadding = 0
        private var mPreFilterScrollState: Parcelable? = null

        @AnyThread
        abstract fun getItemId(position: Int): Long

        @UiThread
        protected abstract fun select(position: Int): Boolean

        @UiThread
        protected abstract fun deselect(position: Int): Boolean

        @AnyThread
        protected abstract fun isSelected(position: Int): Boolean

        @AnyThread
        protected open fun isSelectable(position: Int): Boolean = true

        @UiThread
        @CallSuper
        open fun cancelSelection() = deselectAll()

        @AnyThread
        protected abstract fun getSelectedItemCount(): Int

        @AnyThread
        protected abstract fun getTotalItemCount(): Int

        @AnyThread
        fun isInSelectionMode(): Boolean = mIsInSelectionMode

        @AnyThread
        fun areAllSelected(): Boolean {
            for (position in 0 until itemCount) {
                if (isSelectable(position) && !isSelected(position)) return false
            }
            return true
        }

        @UiThread
        fun notifySelectionChange() = mSelectionChangeListener?.invoke()

        @AnyThread
        fun setInSelectionMode(inSelectionMode: Boolean) { mIsInSelectionMode = inSelectionMode }

        @UiThread
        @CallSuper
        open fun toggleSelection(position: Int) {
            if (!isSelectable(position)) return
            if (isSelected(position)) {
                if (deselect(position)) { notifyItemChanged(position, PAYLOAD_SELECTION_CHANGED); notifySelectionChange() }
            } else {
                if (select(position)) { notifySelectionChange(); notifyItemChanged(position, PAYLOAD_SELECTION_CHANGED) }
            }
        }

        @UiThread
        @CallSuper
        open fun selectAll() {
            var selStart = -1
            var selEnd = -1
            for (position in 0 until itemCount) {
                if (isSelectable(position) && select(position)) {
                    if (selStart == -1) selStart = position
                    selEnd = position
                }
            }
            notifySelectionChange()
            val selSize = (selEnd - selStart) + 1
            if (selSize > 0) notifyItemRangeChanged(selStart, selSize, PAYLOAD_SELECTION_CHANGED)
        }

        @UiThread
        @CallSuper
        open fun deselectAll() {
            for (position in 0 until itemCount) {
                if (isSelectable(position) && isSelected(position) && deselect(position)) {
                    notifyItemChanged(position, PAYLOAD_SELECTION_CHANGED)
                }
            }
            notifySelectionChange()
        }

        @UiThread
        @CallSuper
        open fun selectRange(firstPosition: Int, secondPosition: Int) {
            val beginPosition = minOf(firstPosition, secondPosition)
            val endPosition = maxOf(firstPosition, secondPosition)
            for (position in beginPosition..endPosition) select(position)
            notifySelectionChange()
            notifyItemRangeChanged(beginPosition, endPosition - beginPosition + 1, PAYLOAD_SELECTION_CHANGED)
        }

        override fun onLayoutChange(
            v: View, left: Int, top: Int, right: Int, bottom: Int,
            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
        ) {
            val listener = mLayoutChangeListener ?: return
            val rect = Rect(left, top, right, bottom)
            val oldRect = Rect(oldLeft, oldTop, oldRight, oldBottom)
            if (rect.width() != oldRect.width() || rect.height() != oldRect.height()) {
                listener(mRecyclerView!!, rect, oldRect)
            }
        }

        internal fun setOnSelectionChangeListener(listener: (() -> Unit)?) { mSelectionChangeListener = listener }
        internal fun setOnLayoutChangeListener(listener: ((RecyclerView, Rect, Rect) -> Unit)?) { mLayoutChangeListener = listener }
        internal fun getLayoutChangeListener(): ((RecyclerView, Rect, Rect) -> Unit)? = mLayoutChangeListener

        internal fun setSelectionBottomPadding(@Px selectionBottomPadding: Int) {
            val rv = mRecyclerView ?: return
            if (rv.clipToPadding) rv.clipToPadding = false
            rv.setPadding(rv.paddingLeft, rv.paddingTop, rv.paddingRight,
                if (selectionBottomPadding == 0) mDefaultBottomPadding else selectionBottomPadding)
        }

        @CallSuper
        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            mRecyclerView = recyclerView
            mDefaultBottomPadding = recyclerView.paddingBottom
            recyclerView.addOnLayoutChangeListener(this)
        }

        @CallSuper
        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            recyclerView.removeOnLayoutChangeListener(this)
            mRecyclerView = null
        }

        fun submitListWithScrollState(list: List<T>?, saveState: Boolean, restoreState: Boolean) {
            val layoutManager = mRecyclerView?.layoutManager
            if (saveState && layoutManager != null) {
                mPreFilterScrollState = layoutManager.onSaveInstanceState()
            }
            val isCurrentlyAtTop: Boolean
            if (layoutManager is LinearLayoutManager) {
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                val topOffset = layoutManager.findViewByPosition(firstVisibleItem)?.top ?: 0
                isCurrentlyAtTop = firstVisibleItem == 0 && topOffset >= 0
            } else isCurrentlyAtTop = false
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

        override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                for (payload in payloads) {
                    if (payload === PAYLOAD_SELECTION_CHANGED) onBindViewHolder(holder, position)
                }
                return
            }
            super.onBindViewHolder(holder, position, payloads)
        }

        @CallSuper
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.itemView.nextFocusRightId = R.id.action_select_all
            val isSelected = isSelected(position)
            val itemView = holder.itemView
            if (itemView is MaterialCardView) {
                if (itemView.isCheckable) itemView.isChecked = isSelected
                else if (isSelected) throw UnsupportedOperationException("Card is not checkable")
            } else if (isSelected) {
                itemView.setBackgroundResource(R.drawable.item_highlight)
            }
        }
    }

    abstract class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
