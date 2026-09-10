// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.util.AttributeSet
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.UiUtils
import me.zhanghai.android.fastscroll.FastScrollNestedScrollView
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class NestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.core.R.attr.nestedScrollViewStyle
) : FastScrollNestedScrollView(context, attrs, defStyleAttr) {

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.NestedScrollView)
        val fastScrollEnabled = a.getBoolean(R.styleable.NestedScrollView_fastScrollerEnabled, false)
        a.recycle()

        if (fastScrollEnabled) {
            FastScrollerBuilder(this).useMd2Style().build()
        }
        UiUtils.applyWindowInsetsAsPaddingNoTop(this)
    }
}
