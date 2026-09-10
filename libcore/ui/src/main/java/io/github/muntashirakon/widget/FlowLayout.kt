// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import io.github.muntashirakon.ui.R

// Copyright 2017 Tianxing Li
// Copyright 2021 Muntashir Al-Islam
@Suppress("unused")
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr, defStyleRes) {

    /**
     * Special value for the child view spacing.
     * SPACING_AUTO mean's that the actual spacing is calculated according to the size of the
     * container and the number of the child views, so that the child views are placed evenly in
     * the container.
     */
    /**
     * Special value for the horizontal spacing of the child views in the last row
     * SPACING_ALIGN means that the horizontal spacing of the child views in the last row keeps
     * the same with the spacing used in the row above. If there is only one row, this value is
     * ignored and the spacing will be calculated according to childSpacing.
     */
    companion object {
        const val SPACING_AUTO = -65536
        const val SPACING_ALIGN = -65537
        private const val SPACING_UNDEFINED = -65538
        private const val UNSPECIFIED_GRAVITY = -1
        private const val ROW_VERTICAL_GRAVITY_AUTO = -65536
    }

    private var mSingleLine = false
    var childSpacing = 0
        set(value) {
            field = value
            requestLayout()
        }
    var minChildSpacing = 0
        set(value) {
            field = value
            requestLayout()
        }
    var childSpacingForLastRow = SPACING_UNDEFINED
        set(value) {
            field = value
            requestLayout()
        }
    var rowSpacing = 0f
        set(value) {
            field = value
            requestLayout()
        }
    private var mAdjustedRowSpacing = 0f
    var maxRows = Int.MAX_VALUE
        set(value) {
            field = value
            requestLayout()
        }
    private var mGravity = UNSPECIFIED_GRAVITY
    private var mRowVerticalGravity = ROW_VERTICAL_GRAVITY_AUTO
    private var mExactMeasuredHeight = 0

    private val mHorizontalSpacingForRow = ArrayList<Float>()
    private val mHeightForRow = ArrayList<Int>()
    private val mWidthForRow = ArrayList<Int>()
    private val mChildNumForRow = ArrayList<Int>()

    init {
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.FlowLayout, 0, 0)
        try {
            mSingleLine = array.getBoolean(R.styleable.FlowLayout_singleLine, false)
            childSpacing = getDimensionOrInt(array, R.styleable.FlowLayout_childSpacing, 0)
            minChildSpacing = getDimensionOrInt(array, R.styleable.FlowLayout_minChildSpacing, 0)
            childSpacingForLastRow = getDimensionOrInt(array, R.styleable.FlowLayout_childSpacingForLastRow, SPACING_UNDEFINED)
            rowSpacing = getDimensionOrInt(array, R.styleable.FlowLayout_rowSpacing, 0).toFloat()
            maxRows = array.getInt(R.styleable.FlowLayout_maxRows, Int.MAX_VALUE)
            mGravity = array.getInt(R.styleable.FlowLayout_android_gravity, UNSPECIFIED_GRAVITY)
            mRowVerticalGravity = array.getInt(R.styleable.FlowLayout_rowVerticalGravity, ROW_VERTICAL_GRAVITY_AUTO)
            if (isInEditMode) {
                val listItemId = array.getResourceId(R.styleable.FlowLayout_listItem, 0)
                val itemCount = array.getInt(R.styleable.FlowLayout_itemCount, 10)
                initPreview(listItemId, itemCount)
            }
        } finally {
            array.recycle()
        }
    }

    private fun initPreview(@LayoutRes layoutId: Int, count: Int) {
        if (layoutId != 0) {
            for (i in 0 until count) {
                LayoutInflater.from(context).inflate(layoutId, this, true)
            }
        }
    }

    private fun getDimensionOrInt(a: android.content.res.TypedArray, index: Int, defValue: Int): Int {
        val tv = TypedValue()
        a.getValue(index, tv)
        return if (tv.type == TypedValue.TYPE_DIMENSION) {
            a.getDimensionPixelSize(index, defValue)
        } else {
            a.getInt(index, defValue)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        mHorizontalSpacingForRow.clear()
        mHeightForRow.clear()
        mWidthForRow.clear()
        mChildNumForRow.clear()

        var measuredHeight = 0
        var measuredWidth = 0
        val childCount = childCount
        var rowWidth = 0
        var maxChildHeightInRow = 0
        var childNumInRow = 0
        val rowSize = widthSize - paddingLeft - paddingRight
        var rowTotalChildWidth = 0
        val allowFlow = widthMode != MeasureSpec.UNSPECIFIED && !mSingleLine
        val resolvedChildSpacing = if (childSpacing == SPACING_AUTO && widthMode == MeasureSpec.UNSPECIFIED) 0 else childSpacing
        val tmpSpacing = if (resolvedChildSpacing == SPACING_AUTO) minChildSpacing.toFloat() else resolvedChildSpacing.toFloat()

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            val childParams = child.layoutParams
            var horizontalMargin = 0
            var verticalMargin = 0
            if (childParams is MarginLayoutParams) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, measuredHeight)
                horizontalMargin = childParams.leftMargin + childParams.rightMargin
                verticalMargin = childParams.topMargin + childParams.bottomMargin
            } else {
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
            }

            val childWidth = child.measuredWidth + horizontalMargin
            val childHeight = child.measuredHeight + verticalMargin
            if (allowFlow && rowWidth + childWidth > rowSize) { // Need flow to next row
                // Save parameters for current row
                mHorizontalSpacingForRow.add(
                    getSpacingForRow(resolvedChildSpacing, rowSize, rowTotalChildWidth, childNumInRow)
                )
                mChildNumForRow.add(childNumInRow)
                mHeightForRow.add(maxChildHeightInRow)
                mWidthForRow.add(rowWidth - tmpSpacing.toInt())
                if (mHorizontalSpacingForRow.size <= maxRows) {
                    measuredHeight += maxChildHeightInRow
                }
                measuredWidth = maxOf(measuredWidth, rowWidth)

                // Place the child view to next row
                childNumInRow = 1
                rowWidth = childWidth + tmpSpacing.toInt()
                rowTotalChildWidth = childWidth
                maxChildHeightInRow = childHeight
            } else {
                childNumInRow++
                rowWidth += childWidth + tmpSpacing.toInt()
                rowTotalChildWidth += childWidth
                maxChildHeightInRow = maxOf(maxChildHeightInRow, childHeight)
            }
        }

        // Measure remaining child views in the last row
        if (childSpacingForLastRow == SPACING_ALIGN) {
            // For SPACING_ALIGN, use the same spacing from the row above if there is more than one row.
            if (mHorizontalSpacingForRow.size >= 1) {
                mHorizontalSpacingForRow.add(mHorizontalSpacingForRow[mHorizontalSpacingForRow.size - 1])
            } else {
                mHorizontalSpacingForRow.add(
                    getSpacingForRow(resolvedChildSpacing, rowSize, rowTotalChildWidth, childNumInRow)
                )
            }
        } else if (childSpacingForLastRow != SPACING_UNDEFINED) {
            // For SPACING_AUTO and specific DP values, apply them to the spacing strategy.
            mHorizontalSpacingForRow.add(
                getSpacingForRow(childSpacingForLastRow, rowSize, rowTotalChildWidth, childNumInRow)
            )
        } else {
            // For SPACING_UNDEFINED, apply childSpacing to the spacing strategy for the last row.
            mHorizontalSpacingForRow.add(
                getSpacingForRow(resolvedChildSpacing, rowSize, rowTotalChildWidth, childNumInRow)
            )
        }

        mChildNumForRow.add(childNumInRow)
        mHeightForRow.add(maxChildHeightInRow)
        mWidthForRow.add(rowWidth - tmpSpacing.toInt())
        if (mHorizontalSpacingForRow.size <= maxRows) {
            measuredHeight += maxChildHeightInRow
        }
        measuredWidth = maxOf(measuredWidth, rowWidth)

        measuredWidth = when {
            resolvedChildSpacing == SPACING_AUTO -> widthSize
            widthMode == MeasureSpec.UNSPECIFIED -> measuredWidth + paddingLeft + paddingRight
            else -> minOf(measuredWidth + paddingLeft + paddingRight, widthSize)
        }

        measuredHeight += paddingTop + paddingBottom
        val rowNum = minOf(mHorizontalSpacingForRow.size, maxRows)
        val resolvedRowSpacing = if (rowSpacing == SPACING_AUTO.toFloat() && heightMode == MeasureSpec.UNSPECIFIED) 0f else rowSpacing
        if (resolvedRowSpacing == SPACING_AUTO.toFloat()) {
            mAdjustedRowSpacing = if (rowNum > 1) (heightSize - measuredHeight) / (rowNum - 1f) else 0f
            measuredHeight = heightSize
        } else {
            mAdjustedRowSpacing = resolvedRowSpacing
            if (rowNum > 1) {
                measuredHeight = if (heightMode == MeasureSpec.UNSPECIFIED) {
                    (measuredHeight + mAdjustedRowSpacing * (rowNum - 1)).toInt()
                } else {
                    minOf((measuredHeight + mAdjustedRowSpacing * (rowNum - 1)).toInt(), heightSize)
                }
            }
        }

        mExactMeasuredHeight = measuredHeight

        val finalWidth = if (widthMode == MeasureSpec.EXACTLY) widthSize else measuredWidth
        val finalHeight = if (heightMode == MeasureSpec.EXACTLY) heightSize else measuredHeight

        setMeasuredDimension(finalWidth, finalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val paddingStart = paddingStart
        val paddingEnd = paddingEnd
        val paddingTop = paddingTop
        val paddingBottom = paddingBottom

        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        var x: Int
        var y = paddingTop

        val verticalGravity = mGravity and Gravity.VERTICAL_GRAVITY_MASK
        val horizontalGravity = mGravity and Gravity.HORIZONTAL_GRAVITY_MASK

        // Calculate y
        when (verticalGravity) {
            Gravity.CENTER_VERTICAL -> {
                val offset = (b - t - paddingTop - paddingBottom - mExactMeasuredHeight) / 2
                y += offset
            }
            Gravity.BOTTOM -> {
                val offset = b - t - paddingTop - paddingBottom - mExactMeasuredHeight
                y += offset
            }
        }

        val horizontalPadding = paddingStart + paddingEnd
        val layoutWidth = r - l

        val verticalRowGravity = mRowVerticalGravity and Gravity.VERTICAL_GRAVITY_MASK

        val rowCount = mChildNumForRow.size
        var childIdx = 0
        for (row in 0 until minOf(rowCount, maxRows)) {
            val childNum = mChildNumForRow[row]
            val rowHeight = mHeightForRow[row]
            val spacing = mHorizontalSpacingForRow[row]
            x = paddingStart + getHorizontalGravityOffsetForRow(horizontalGravity, layoutWidth, horizontalPadding, row)
            if (isRtl) {
                x = layoutWidth - x
            }
            var i = 0
            while (i < childNum && childIdx < childCount) {
                val child = getChildAt(childIdx++)
                if (child.visibility == GONE) {
                    continue
                } else {
                    i++
                }

                val childParams = child.layoutParams
                var marginStart = 0
                var marginTop = 0
                var marginBottom = 0
                var marginEnd = 0
                if (childParams is MarginLayoutParams) {
                    marginStart = childParams.marginStart
                    marginEnd = childParams.marginEnd
                    marginTop = childParams.topMargin
                    marginBottom = childParams.bottomMargin
                }

                val childWidth = child.measuredWidth
                val childHeight = child.measuredHeight
                var tt = y + marginTop
                if (verticalRowGravity == Gravity.BOTTOM) {
                    tt = y + rowHeight - marginBottom - childHeight
                } else if (verticalRowGravity == Gravity.CENTER_VERTICAL) {
                    tt = y + marginTop + (rowHeight - marginTop - marginBottom - childHeight) / 2
                }
                val bb = tt + childHeight
                if (isRtl) {
                    val l2 = x - marginStart
                    val r2 = l2 - childWidth
                    child.layout(r2, tt, l2, bb)
                } else {
                    val l2 = x + marginStart
                    val r2 = l2 + childWidth
                    child.layout(l2, tt, r2, bb)
                }
                // The width consumed by this child
                val consumedWidth = childWidth + spacing.toInt() + marginStart + marginEnd
                x += if (isRtl) -consumedWidth else consumedWidth
            }
            y += rowHeight + mAdjustedRowSpacing.toInt()
        }

        for (i in childIdx until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (isRtl) {
                child.layout(layoutWidth, 0, layoutWidth, 0)
            } else {
                child.layout(0, 0, 0, 0)
            }
        }
    }

    private fun getHorizontalGravityOffsetForRow(
        horizontalGravity: Int,
        parentLayoutWidth: Int,
        horizontalPadding: Int,
        row: Int,
    ): Int {
        if (childSpacing == SPACING_AUTO || row >= mWidthForRow.size
            || row >= mChildNumForRow.size || mChildNumForRow[row] <= 0
        ) {
            return 0
        }
        return when (horizontalGravity) {
            Gravity.CENTER_HORIZONTAL ->
                // (Layout width - (total horizontal padding + real row width)) / 2
                (parentLayoutWidth - horizontalPadding - mWidthForRow[row]) / 2
            Gravity.END ->
                parentLayoutWidth - horizontalPadding - mWidthForRow[row]
            else -> 0
        }
    }

    override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        MarginLayoutParams(context, attrs)

    /**
     * Whether the items are constrained to a single horizontal line.
     *
     * @return `true` if the items are constrained to a single horizontal line, `false` otherwise.
     */
    var isSingleLine: Boolean
        get() = mSingleLine
        set(value) {
            mSingleLine = value
            requestLayout()
        }

    override fun setGravity(gravity: Int) {
        if (mGravity != gravity) {
            mGravity = gravity
            requestLayout()
        }
    }

    fun setRowVerticalGravity(rowVerticalGravity: Int) {
        if (mRowVerticalGravity != rowVerticalGravity) {
            mRowVerticalGravity = rowVerticalGravity
            requestLayout()
        }
    }

    val rowsCount: Int get() = mChildNumForRow.size

    private fun getSpacingForRow(spacingAttribute: Int, rowSize: Int, usedSize: Int, childNum: Int): Float {
        if (spacingAttribute == SPACING_AUTO) {
            if (childNum > 1) {
                return (rowSize - usedSize) / (childNum - 1f)
            }
            return 0f
        }
        return spacingAttribute.toFloat()
    }
}
