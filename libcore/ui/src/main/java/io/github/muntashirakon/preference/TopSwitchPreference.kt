// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
import io.github.muntashirakon.ui.R

class TopSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.topSwitchPreferenceStyle,
    defStyleRes: Int = R.style.Preference_M3_TopSwitchPreference
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes)
