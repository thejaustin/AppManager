// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.widget.TextViewCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.resources.MaterialAttributes
import com.leinardi.android.speeddial.FabWithLabelView
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialOverlayLayout
import com.leinardi.android.speeddial.SpeedDialView
import io.github.muntashirakon.util.UiUtils

// Based on https://github.com/zhanghai/MaterialFiles/blob/9a6db781087f9e3b6345af15c735c33b305d24c2/app/src/main/java/me/zhanghai/android/files/ui/ThemedSpeedDialView.kt
// TODO: 26/6/23 Replace this with a custom implementation by removing all those bad practices that this library follows
class FloatingActionButtonGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SpeedDialView(context, attrs, defStyleAttr) {

    init {
        val fab = mainFab
        val margin = UiUtils.dpToPx(getContext(), 16)
        val layoutParams = fab.layoutParams as MarginLayoutParams
        layoutParams.setMargins(margin, margin, margin, margin)
        fab.layoutParams = layoutParams
        fab.setUseCompatPadding(false)
        setMainFabOpenedBackgroundColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError))
        setMainFabOpenedIconColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnError))
        setMainFabClosedBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer))
        setMainFabClosedIconColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer))
    }

    @SuppressLint("RestrictedApi")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val overlayLayout: SpeedDialOverlayLayout? = overlayLayout
        overlayLayout?.setBackgroundColor(Color.TRANSPARENT)
    }

    @SuppressLint("RestrictedApi")
    override fun addActionItem(actionItem: SpeedDialActionItem, position: Int, animate: Boolean): FabWithLabelView? {
        val fabImageTintColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer)
        val fabBackgroundColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer)
        val labelColor = MaterialColors.getColor(this, android.R.attr.textColorSecondary)
        val labelBackgroundColor = Color.TRANSPARENT
        val context = context
        val item = SpeedDialActionItem.Builder(actionItem.id, actionItem.getFabImageDrawable(context))
            .setLabel(actionItem.getLabel(context))
            .setFabImageTintColor(fabImageTintColor)
            .setFabBackgroundColor(fabBackgroundColor)
            .setLabelColor(labelColor)
            .setLabelBackgroundColor(labelBackgroundColor)
            .setLabelClickable(actionItem.isLabelClickable)
            .setTheme(actionItem.theme)
            .create()
        val fabWrapper = super.addActionItem(item, position, animate) ?: return null
        val fab: FloatingActionButton = fabWrapper.fab
        val margin = UiUtils.dpToPx(getContext(), 16)
        val layoutParams = fab.layoutParams as MarginLayoutParams
        layoutParams.setMargins(margin, 0, margin, 0)
        fab.layoutParams = layoutParams
        fab.setUseCompatPadding(false)
        val labelBackground: CardView = fabWrapper.labelBackground
        labelBackground.setUseCompatPadding(false)
        labelBackground.setContentPadding(0, 0, 0, 0)
        labelBackground.foreground = null
        val label = labelBackground.getChildAt(0) as TextView
        val textAppearance = MaterialAttributes.resolveOrThrow(this, com.google.android.material.R.attr.textAppearanceLabelLarge)
        TextViewCompat.setTextAppearance(label, textAppearance)
        return fabWrapper
    }
}
