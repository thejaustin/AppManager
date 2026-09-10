// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.checkbox.MaterialCheckBox

class CheckBox @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCheckBox(context, attrs, defStyleAttr) {

    private var mListener: OnCheckedChangeListener? = null

    override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        mListener = listener
        super.setOnCheckedChangeListener(listener)
    }

    fun setChecked(checked: Boolean, triggerListener: Boolean) {
        if (!triggerListener) {
            super.setOnCheckedChangeListener(null)
            super.setChecked(checked)
            super.setOnCheckedChangeListener(mListener)
            return
        }
        super.setChecked(checked)
    }

    fun toggle(triggerListener: Boolean) {
        if (!triggerListener) {
            super.setOnCheckedChangeListener(null)
            super.toggle()
            super.setOnCheckedChangeListener(mListener)
            return
        }
        super.toggle()
    }
}
