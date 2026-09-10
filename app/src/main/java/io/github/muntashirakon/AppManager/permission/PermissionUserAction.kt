// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.content.Intent
import io.github.muntashirakon.AppManager.utils.IntentUtils

class PermissionUserAction internal constructor(
    private val action: String,
    private val supportsPackage: Boolean
) {
    fun getAction(): String = action

    fun supportsPackage(): Boolean = supportsPackage

    fun toIntent(packageName: String): Intent {
        return IntentUtils.getSettings(action, if (supportsPackage) packageName else null)
    }
}
