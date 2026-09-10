// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.annotation.UserIdInt
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.PermissionInfo
import android.content.pm.PackageInfo
import androidx.core.content.pm.PermissionInfoCompat
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat

internal object PermissionResolver {
    @Throws(PermissionException::class)
    fun resolve(
        platform: PackageManagerPermissionPlatform,
        packageInfo: PackageInfo,
        permissionName: String,
        @UserIdInt userId: Int,
        appOpAllowed: Boolean
    ): Permission {
        try {
            val permissionInfo = platform.getPermissionInfo(permissionName, packageInfo.packageName)
                ?: throw PermissionException("Unknown permission $permissionName")
            val granted = platform.checkPermission(permissionName, packageInfo.packageName, userId) == PERMISSION_GRANTED
            val flags = platform.getPermissionFlags(permissionName, packageInfo.packageName, userId)
            val appOp = AppOpsManagerCompat.permissionToOpCode(permissionName)
            val protection = PermissionInfoCompat.getProtection(permissionInfo)
            val protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo)
            if (protection == PermissionInfo.PROTECTION_DANGEROUS && PermUtils.systemSupportsRuntimePermissions()) {
                return RuntimePermission(permissionName, granted, appOp, appOpAllowed, flags)
            }
            if ((protectionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                return DevelopmentPermission(permissionName, granted, appOp, appOpAllowed, flags)
            }
            return ReadOnlyPermission(permissionName, granted, appOp, appOpAllowed, flags)
        } catch (e: PermissionException) {
            throw e
        } catch (e: Exception) {
            throw PermissionException(e)
        }
    }
}
