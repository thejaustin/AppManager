// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

class HyperlinkPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summaryView = holder.findViewById(android.R.id.summary) as? TextView
        summaryView?.movementMethod = LinkMovementMethod.getInstance()
    }
}
