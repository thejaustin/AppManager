// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference

import android.content.Context
import android.os.Build
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.github.muntashirakon.ui.R

open class DefaultAlertPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.alertPreferenceStyle,
    defStyleRes: Int = R.style.Preference_M3_Alert
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    var isAddSpaceBetweenIconAndText: Boolean = true

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summaryView = holder.findViewById(android.R.id.summary) as? TextView
        summaryView?.movementMethod = LinkMovementMethod.getInstance()

        var imageFrame = holder.findViewById(androidx.preference.R.id.icon_frame)
        if (imageFrame == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            imageFrame = holder.findViewById(android.R.id.icon_frame)
        }
        if (imageFrame is LinearLayoutCompat) {
            imageFrame.gravity = Gravity.START
        }
        val empty = holder.findViewById(android.R.id.empty)
        if (empty != null) {
            empty.visibility = if (isAddSpaceBetweenIconAndText && imageFrame != null && imageFrame.visibility != View.GONE) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}
