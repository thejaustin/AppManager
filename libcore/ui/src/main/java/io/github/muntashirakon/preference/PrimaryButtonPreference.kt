// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.ui.R

class PrimaryButtonPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.primaryButtonPreferenceStyle,
    defStyleRes: Int = R.style.Preference_M3_ButtonPreference_Primary
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val itemView = holder.itemView
        val button = holder.findViewById(android.R.id.button1) as MaterialButton
        button.text = title
        button.icon = icon
        if (itemView.hasOnClickListeners()) {
            button.setOnClickListener { itemView.callOnClick() }
        }
        val selectable = isSelectable
        itemView.isClickable = false
        itemView.isFocusable = false
        button.isClickable = selectable
        button.isFocusable = selectable
    }
}
