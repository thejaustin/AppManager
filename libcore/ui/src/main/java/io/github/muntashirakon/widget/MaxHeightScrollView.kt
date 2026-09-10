// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.UiUtils

// Copyright 2016 Bumsoo Kim
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ScrollView(context, attrs, defStyleAttr, defStyleRes) {

    var maxHeight: Int = 0

    var maxHeightDp: Int
        get() = UiUtils.pxToDp(context, maxHeight)
        set(value) {
            maxHeight = UiUtils.dpToPx(context, value)
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView)
            if (a.hasValue(R.styleable.MaxHeightScrollView_maxHeight)) {
                maxHeight = a.getDimensionPixelSize(R.styleable.MaxHeightScrollView_maxHeight, 0)
            }
            a.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
    }
}
