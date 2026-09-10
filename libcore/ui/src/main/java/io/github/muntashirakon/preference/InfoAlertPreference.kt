// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import io.github.muntashirakon.ui.R

class InfoAlertPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.alertPreferenceStyle,
    defStyleRes: Int = R.style.Preference_M3_Alert
) : DefaultAlertPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        isAddSpaceBetweenIconAndText = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val background = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer)
        val foreground = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)
        if (holder.itemView is MaterialCardView) {
            (holder.itemView as MaterialCardView).setCardBackgroundColor(background)
        }
        val summaryView = holder.findViewById(android.R.id.summary) as? TextView
        summaryView?.setTextColor(foreground)
        val imageView = holder.findViewById(android.R.id.icon) as? ImageView
        imageView?.imageTintList = ColorStateList.valueOf(foreground)
    }
}
