// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.google.android.material.internal.ViewUtils

class AppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.appBarLayoutStyle
) : com.google.android.material.appbar.AppBarLayout(context, attrs, defStyleAttr) {

    init {
        @Suppress("DEPRECATION")
        @SuppressLint("RestrictedApi")
        ViewUtils.doOnApplyWindowInsets(this) { view, insets, initialPadding ->
            if (!ViewCompat.getFitsSystemWindows(view)) {
                return@doOnApplyWindowInsets insets
            }
            initialPadding.top += insets.systemWindowInsetTop
            val isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL
            val systemWindowInsetLeft = insets.systemWindowInsetLeft
            val systemWindowInsetRight = insets.systemWindowInsetRight
            initialPadding.start += if (isRtl) systemWindowInsetRight else systemWindowInsetLeft
            initialPadding.end += if (isRtl) systemWindowInsetLeft else systemWindowInsetRight
            initialPadding.applyToView(view)
            insets
        }
    }
}
