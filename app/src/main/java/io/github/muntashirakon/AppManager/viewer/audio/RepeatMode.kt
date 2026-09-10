// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio

import androidx.annotation.IntDef

@IntDef(RepeatMode.NO_REPEAT, RepeatMode.REPEAT_INDEFINITELY, RepeatMode.REPEAT_SINGLE_INDEFINITELY)
@Retention(AnnotationRetention.SOURCE)
annotation class RepeatMode {
    companion object {
        const val NO_REPEAT = 0
        const val REPEAT_INDEFINITELY = 1
        const val REPEAT_SINGLE_INDEFINITELY = 3
    }
}
