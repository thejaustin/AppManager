// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.content.pm.PackageInfo
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat

class PermissionContext @JvmOverloads constructor(
    val packageInfo: PackageInfo,
    val permission: Permission,
    val userId: Int,
    val appOps: AppOpsManagerCompat?,
    val setByUser: Boolean = true,
    val fixedByUser: Boolean = true
)
