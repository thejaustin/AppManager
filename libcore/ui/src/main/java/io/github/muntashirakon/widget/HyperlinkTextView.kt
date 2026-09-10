// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.util.AttributeSet
import com.google.android.material.textview.MaterialTextView

class HyperlinkTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialTextView(context, attrs, defStyleAttr) {

    override fun getDefaultMovementMethod(): MovementMethod = LinkMovementMethod.getInstance()
}
