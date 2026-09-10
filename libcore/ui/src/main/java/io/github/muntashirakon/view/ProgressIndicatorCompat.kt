// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view

import android.view.View
import com.google.android.material.progressindicator.BaseProgressIndicator

object ProgressIndicatorCompat {
    @JvmStatic
    fun <T : BaseProgressIndicator<*>> setVisibility(progressIndicator: T?, visible: Boolean) {
        progressIndicator ?: return
        if (visible) {
            progressIndicator.show()
        } else {
            progressIndicator.hide()
            progressIndicator.visibility = View.GONE
        }
    }
}
