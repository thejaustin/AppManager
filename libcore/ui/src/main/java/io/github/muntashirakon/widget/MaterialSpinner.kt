// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.Filterable
import android.widget.ListAdapter
import androidx.annotation.NonNull
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.view.TextInputLayoutCompat

class MaterialSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialSpinnerStyle
) : TextInputLayout(context, attrs, defStyleAttr) {

    private val mAutoCompleteTextView: MaterialAutoCompleteTextView
    private var mSelection: Int = 0

    init {
        val ctx = getContext()
        mAutoCompleteTextView = MaterialAutoCompleteTextView(ctx)
        addView(mAutoCompleteTextView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        mAutoCompleteTextView.inputType = EditorInfo.TYPE_NULL
        mAutoCompleteTextView.keyListener = null
        mAutoCompleteTextView.isFocusable = false
        setEndIconTintList(mAutoCompleteTextView.textColors)
        endIconMode = END_ICON_DROPDOWN_MENU
        TextInputLayoutCompat.fixEndIcon(this)
    }

    fun <T> setAdapter(adapter: T?) where T : ListAdapter, T : Filterable {
        mAutoCompleteTextView.setAdapter(adapter)
        if (adapter != null) {
            setSelection(mSelection)
        }
    }

    fun setOnItemClickListener(listener: AdapterView.OnItemClickListener?) {
        mAutoCompleteTextView.onItemClickListener = listener
    }

    fun setSelection(position: Int) {
        mSelection = position
        val adapter = mAutoCompleteTextView.adapter
        val obj = if (adapter == null || mSelection < 0 || adapter.count <= mSelection) {
            null
        } else {
            adapter.getItem(mSelection)
        }
        mAutoCompleteTextView.setText(if (obj == null) "" else obj.toString(), false)
    }
}
