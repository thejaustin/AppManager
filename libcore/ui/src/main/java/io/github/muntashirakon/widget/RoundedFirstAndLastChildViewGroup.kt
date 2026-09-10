// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.Shapeable
import io.github.muntashirakon.ui.R

class RoundedFirstAndLastChildViewGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FlowLayout(context, attrs, defStyleAttr, defStyleRes) {

    override fun addView(child: View, index: Int, params: LayoutParams) {
        super.addView(child, index, params)
        updateFirstAndLastViews()
    }

    private fun updateFirstAndLastViews() {
        val count = childCount
        when (count) {
            0 -> return
            1 -> {
                val v = getChildAt(0)
                if (v is Shapeable) {
                    v.shapeAppearanceModel = ShapeAppearanceModel.builder(
                        v.context, R.style.ShapeAppearance_AppTheme_LargeComponent, 0
                    ).build()
                }
            }
            2 -> {
                val firstView = getChildAt(0)
                if (firstView is Shapeable) {
                    firstView.shapeAppearanceModel = ShapeAppearanceModel.builder(
                        firstView.context, R.style.ShapeAppearance_AppTheme_LeftRounded, 0
                    ).build()
                }
                val secondView = getChildAt(count - 1)
                if (secondView is Shapeable) {
                    secondView.shapeAppearanceModel = ShapeAppearanceModel.builder(
                        secondView.context, R.style.ShapeAppearance_AppTheme_RightRounded, 0
                    ).build()
                }
            }
            else -> {
                val lastView = getChildAt(count - 2)
                if (lastView is Shapeable) {
                    lastView.shapeAppearanceModel = ShapeAppearanceModel.builder(lastView.context, 0, 0).build()
                }
                val thisView = getChildAt(count - 1)
                if (thisView is Shapeable) {
                    thisView.shapeAppearanceModel = ShapeAppearanceModel.builder(
                        thisView.context, R.style.ShapeAppearance_AppTheme_RightRounded, 0
                    ).build()
                }
            }
        }
    }
}
