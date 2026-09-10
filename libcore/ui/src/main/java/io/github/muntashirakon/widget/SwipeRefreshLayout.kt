// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.google.android.material.color.MaterialColors
import com.google.android.material.internal.ThemeEnforcement
import com.google.android.material.theme.overlay.MaterialThemeOverlay
import io.github.muntashirakon.ui.R

class SwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = DEFAULT_STYLE_RES
) : androidx.swiperefreshlayout.widget.SwipeRefreshLayout(
    MaterialThemeOverlay.wrap(context, attrs, defStyleAttr, DEFAULT_STYLE_RES), attrs
) {

    init {
        val ctx = getContext()
        @SuppressLint("RestrictedApi")
        val a = ThemeEnforcement.obtainStyledAttributes(ctx, attrs, R.styleable.SwipeRefreshLayout, defStyleAttr, defStyleRes)
        try {
            setProgressBackgroundColorSchemeColor(
                a.getColor(
                    R.styleable.SwipeRefreshLayout_progressBackgroundColor,
                    MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, -1)
                )
            )
            setColorSchemeColors(*loadIndicatorColors(ctx, a))
        } finally {
            a.recycle()
        }
    }

    companion object {
        private val DEFAULT_STYLE_RES = R.style.Widget_AppTheme_SwipeRefreshLayout

        private fun loadIndicatorColors(context: Context, typedArray: TypedArray): IntArray {
            if (!typedArray.hasValue(R.styleable.SwipeRefreshLayout_indicatorColor)) {
                return intArrayOf(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, -1))
            }
            val indicatorColorValue = typedArray.peekValue(R.styleable.SwipeRefreshLayout_indicatorColor)
            if (indicatorColorValue.type != TypedValue.TYPE_REFERENCE) {
                return intArrayOf(typedArray.getColor(R.styleable.SwipeRefreshLayout_indicatorColor, -1))
            }
            val indicatorColors = context.resources.getIntArray(
                typedArray.getResourceId(R.styleable.SwipeRefreshLayout_indicatorColor, -1)
            )
            if (indicatorColors.isEmpty()) {
                throw IllegalArgumentException("indicatorColors cannot be empty when indicatorColor is not used.")
            }
            return indicatorColors
        }
    }
}
