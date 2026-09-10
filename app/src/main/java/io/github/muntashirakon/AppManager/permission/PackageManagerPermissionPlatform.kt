// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.annotation.UserIdInt
import android.content.pm.ApplicationInfo
import android.content.pm.PermissionInfo
import android.os.RemoteException
import android.os.UserHandleHidden
import io.github.muntashirakon.AppManager.compat.PermissionCompat
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.BroadcastUtils
import io.github.muntashirakon.AppManager.utils.ContextUtils

interface PackageManagerPermissionPlatform {
    fun canModifyPermissions(): Boolean

    @Throws(RemoteException::class)
    fun checkPermission(permissionName: String, packageName: String, @UserIdInt userId: Int): Int

    @Throws(RemoteException::class)
    fun getPermissionInfo(permissionName: String, packageName: String): PermissionInfo?

    fun getPermissionFlags(permissionName: String, packageName: String, @UserIdInt userId: Int): Int

    @Throws(RemoteException::class)
    fun grantPermission(packageName: String, permissionName: String, @UserIdInt userId: Int)

    @Throws(RemoteException::class)
    fun revokePermission(packageName: String, permissionName: String, @UserIdInt userId: Int, reason: String?)

    fun getCheckAdjustPolicyFlagPermission(applicationInfo: ApplicationInfo): Boolean

    @Throws(RemoteException::class)
    fun updatePermissionFlags(
        permissionName: String,
        packageName: String,
        flagMask: Int,
        flagValues: Int,
        checkAdjustPolicy: Boolean,
        @UserIdInt userId: Int
    )

    @UserIdInt
    fun getCurrentUserId(): Int

    fun onPackageAltered(packageName: String)
}

internal class FrameworkPackageManagerPermissionPlatform : PackageManagerPermissionPlatform {
    override fun canModifyPermissions(): Boolean = SelfPermissions.canModifyPermissions()

    override fun checkPermission(permissionName: String, packageName: String, userId: Int): Int {
        return PermissionCompat.checkPermission(permissionName, packageName, userId)
    }

    @Throws(RemoteException::class)
    override fun getPermissionInfo(permissionName: String, packageName: String): PermissionInfo? {
        return PermissionCompat.getPermissionInfo(permissionName, packageName, 0)
    }

    override fun getPermissionFlags(permissionName: String, packageName: String, userId: Int): Int {
        return PermissionCompat.getPermissionFlags(permissionName, packageName, userId)
    }

    @Throws(RemoteException::class)
    override fun grantPermission(packageName: String, permissionName: String, userId: Int) {
        PermissionCompat.grantPermission(packageName, permissionName, userId)
    }

    @Throws(RemoteException::class)
    override fun revokePermission(packageName: String, permissionName: String, userId: Int, reason: String?) {
        if (reason == null) {
            PermissionCompat.revokePermission(packageName, permissionName, userId)
        } else {
            PermissionCompat.revokePermission(packageName, permissionName, userId, reason)
        }
    }

    override fun getCheckAdjustPolicyFlagPermission(applicationInfo: ApplicationInfo): Boolean {
        return PermissionCompat.getCheckAdjustPolicyFlagPermission(applicationInfo)
    }

    @Throws(RemoteException::class)
    override fun updatePermissionFlags(
        permissionName: String,
        packageName: String,
        flagMask: Int,
        flagValues: Int,
        checkAdjustPolicy: Boolean,
        userId: Int
    ) {
        PermissionCompat.updatePermissionFlags(permissionName, packageName, flagMask, flagValues, checkAdjustPolicy, userId)
    }

    override fun getCurrentUserId(): Int = UserHandleHidden.myUserId()

    override fun onPackageAltered(packageName: String) {
        BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(packageName))
    }
}
