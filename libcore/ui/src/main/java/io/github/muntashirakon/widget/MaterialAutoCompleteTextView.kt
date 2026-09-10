// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StyleableRes
import com.google.android.material.internal.ThemeEnforcement
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.view.AutoCompleteTextViewCompat

class MaterialAutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.autoCompleteTextViewStyle
) : com.google.android.material.textfield.MaterialAutoCompleteTextView(context, attrs, defStyleAttr) {

    init {
        val ctx = getContext()
        @SuppressLint("RestrictedApi")
        val attributes = ThemeEnforcement.obtainStyledAttributes(
            ctx,
            attrs,
            com.google.android.material.R.styleable.MaterialAutoCompleteTextView,
            defStyleAttr,
            androidx.appcompat.R.style.Widget_AppCompat_AutoCompleteTextView
        )
        val popupListSelector = attributes.getDrawable(R.styleable.MaterialAutoCompleteTextView_android_dropDownSelector)
        if (popupListSelector != null) {
            AutoCompleteTextViewCompat.setListSelectorMaterial(this, popupListSelector)
        }
        attributes.recycle()
    }
}
