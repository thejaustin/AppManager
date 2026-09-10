// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StyleRes
import androidx.appcompat.widget.TintTypedArray
import androidx.core.widget.TextViewCompat
import com.google.android.material.internal.ThemeEnforcement
import com.google.android.material.resources.MaterialResources
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.Shapeable
import com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.view.AutoCompleteTextViewCompat

class SearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.searchViewStyle,
    @StyleRes defStyleRes: Int = DEF_STYLE_RES,
) : androidx.appcompat.widget.SearchView(wrap(context, attrs, defStyleAttr, defStyleRes), attrs, defStyleAttr), Shapeable {

    @SuppressLint("RestrictedApi")
    private val mSearchSrcTextView: SearchAutoComplete
    private val mSearchEditFrame: LinearLayout
    private val mCloseButton: ImageView
    private val mExpandedSearchViewShapeDrawable: MaterialShapeDrawable
    private var mElevation: Float

    init {
        val ctx = getContext()
        mCloseButton = findViewById(androidx.appcompat.R.id.search_close_btn)
        mSearchSrcTextView = findViewById(androidx.appcompat.R.id.search_src_text)
        mSearchEditFrame = findViewById(androidx.appcompat.R.id.search_edit_frame)
        mElevation = elevation

        @SuppressLint("RestrictedApi")
        val a: TintTypedArray = ThemeEnforcement.obtainTintedStyledAttributes(
            ctx, attrs, R.styleable.SearchView, defStyleAttr, DEF_STYLE_RES
        )

        val textAppearance = a.getResourceId(R.styleable.SearchView_android_textAppearance, 0)
        TextViewCompat.setTextAppearance(mSearchSrcTextView, textAppearance)

        mCloseButton.imageTintList = MaterialResources.getColorStateList(
            ctx, a, R.styleable.SearchView_closeIconTint
        )

        val popupBackgroundResource = a.getResourceId(R.styleable.SearchView_android_popupBackground, 0)
        if (popupBackgroundResource != 0) {
            mSearchSrcTextView.setDropDownBackgroundResource(popupBackgroundResource)
        }

        val popupListSelector: Drawable? = a.getDrawable(R.styleable.SearchView_android_dropDownSelector)
        if (popupListSelector != null) {
            AutoCompleteTextViewCompat.setListSelector(mSearchSrcTextView, popupListSelector)
        }

        val frameMarginHorizontal = a.getDimensionPixelSize(R.styleable.SearchView_frameMarginHorizontal, 0)

        a.recycle()
        mExpandedSearchViewShapeDrawable = MaterialShapeDrawable(ctx, attrs, defStyleAttr, DEF_STYLE_RES)
        val layoutParams = mSearchEditFrame.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.marginStart = frameMarginHorizontal
        layoutParams.marginEnd = frameMarginHorizontal
        updateBackgroundExpanded()
    }

    override fun getShapeAppearanceModel(): ShapeAppearanceModel =
        mExpandedSearchViewShapeDrawable.shapeAppearanceModel

    override fun setShapeAppearanceModel(shapeAppearanceModel: ShapeAppearanceModel) {
        mExpandedSearchViewShapeDrawable.shapeAppearanceModel = shapeAppearanceModel
    }

    override fun setElevation(elevation: Float) {
        mElevation = elevation
        super.setElevation(elevation)
        mExpandedSearchViewShapeDrawable.elevation = elevation
    }

    private fun updateBackgroundExpanded() {
        mExpandedSearchViewShapeDrawable.initializeElevationOverlay(context)
        mExpandedSearchViewShapeDrawable.elevation = mElevation
        background = mExpandedSearchViewShapeDrawable
    }

    companion object {
        private val DEF_STYLE_RES = R.style.Widget_AppTheme_SearchView
    }
}
