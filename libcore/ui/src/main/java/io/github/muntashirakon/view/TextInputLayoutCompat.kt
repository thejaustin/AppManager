// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageButton
import com.google.android.material.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// TODO: 26/6/23 Make it a full replacement for TextInputLayout which is purposefully made for devs to suffer
object TextInputLayoutCompat {
    @JvmStatic
    fun fromTextInputEditText(editText: TextInputEditText): TextInputLayout =
        editText.parent.parent as TextInputLayout

    @JvmStatic
    fun fixEndIcon(layout: TextInputLayout) {
        val editText = layout.editText ?: return
        layout.post {
            val endIconView = layout.findViewById<AppCompatImageButton>(R.id.text_input_end_icon)
            val endIconSize = layout.endIconMinSize
            val width = editText.minimumHeight
            val newHeight = if (endIconSize > 0) endIconSize else width
            val newWidth = maxOf(width, newHeight)
            val layoutParams = FrameLayout.LayoutParams(newWidth, newHeight)
            layoutParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            endIconView.layoutParams = layoutParams
            endIconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }
}
