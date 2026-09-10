// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.annotation.UserIdInt
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.RemoteException
import android.os.UserHandleHidden
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_AUTO_REVOKED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_ONE_TIME
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKED_COMPAT
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET
import io.github.muntashirakon.AppManager.logs.Log

class PackageManagerPermissionController(
    private val mPlatform: PackageManagerPermissionPlatform
) : IPermissionController {

    companion object {
        private val INSTANCE: PackageManagerPermissionController =
            PackageManagerPermissionController(FrameworkPackageManagerPermissionPlatform())

        fun getInstance(): PackageManagerPermissionController = INSTANCE
    }

    fun supports(permission: Permission): Boolean {
        return !permission.isReadOnly && !permission.affectsAppOp()
    }

    override fun getId(): String = "package-manager"

    override fun supports(context: PermissionContext): Boolean {
        return supportsPackageManagerState(context.permission)
    }

    override fun getState(context: PermissionContext): PermissionControllerState {
        return PermissionControllerState(
            getId(),
            if (context.permission.isGranted()) PermissionState.GRANTED else PermissionState.DENIED,
            isModifiable(context.permission),
            null
        )
    }

    override fun setGranted(context: PermissionContext, granted: Boolean): PermissionChangeResult {
        if (!supportsPackageManagerState(context.permission)) {
            return PermissionChangeResult.unsupported("Package Manager does not support ${context.permission.getName()}")
        }
        return try {
            if (supports(context.permission)) {
                if (granted) {
                    grant(context.packageInfo, context.permission, context.setByUser, context.fixedByUser)
                } else {
                    revoke(context.packageInfo, context.permission, context.fixedByUser)
                }
            } else {
                setPlatformGranted(context.packageInfo, context.permission.getName(), context.userId, granted)
            }
            PermissionChangeResult.success()
        } catch (e: PermissionException) {
            PermissionChangeResult.failure(e.message, e)
        }
    }

    fun supportsPackageManagerState(permission: Permission): Boolean = !permission.isReadOnly

    fun isModifiable(permission: Permission): Boolean {
        return supports(permission) && !permission.isReadOnly && !permission.isPolicyFixed()
                && mPlatform.canModifyPermissions()
    }

    @WorkerThread
    @Throws(PermissionException::class)
    fun grant(
        packageInfo: PackageInfo,
        permission: Permission,
        setByTheUser: Boolean,
        fixedByTheUser: Boolean
    ) {
        requireModifiable(permission)
        PermissionMutation.prepareGrant(packageInfo, permission, setByTheUser, fixedByTheUser)
        persist(packageInfo.applicationInfo, permission, null)
    }

    @WorkerThread
    @Throws(PermissionException::class)
    fun revoke(
        packageInfo: PackageInfo,
        permission: Permission,
        fixedByTheUser: Boolean
    ) {
        revoke(packageInfo, permission, fixedByTheUser, null)
    }

    @WorkerThread
    @Throws(PermissionException::class)
    fun revoke(
        packageInfo: PackageInfo,
        permission: Permission,
        fixedByTheUser: Boolean,
        reason: String?
    ) {
        requireModifiable(permission)
        PermissionMutation.prepareRevoke(packageInfo, permission, fixedByTheUser)
        persist(packageInfo.applicationInfo, permission, reason)
    }

    @WorkerThread
    @Throws(PermissionException::class)
    fun setPlatformGranted(
        packageInfo: PackageInfo,
        permissionName: String,
        @UserIdInt userId: Int,
        granted: Boolean
    ) {
        val result = trySetPlatformGranted(packageInfo, permissionName, userId, granted)
        if (!result.isSuccessful()) {
            throw PermissionException(result.message, result.cause)
        }
    }

    @WorkerThread
    fun trySetPlatformGranted(
        packageInfo: PackageInfo,
        permissionName: String,
        @UserIdInt userId: Int,
        granted: Boolean
    ): PermissionChangeResult {
        if (UserHandleHidden.getUserId(packageInfo.applicationInfo.uid) != userId) {
            return PermissionChangeResult.failure("Package user does not match requested user $userId", null)
        }
        val permission = try {
            PermissionResolver.resolve(mPlatform, packageInfo, permissionName, userId, false)
        } catch (e: PermissionException) {
            return PermissionChangeResult.failure("Could not resolve permission $permissionName", e)
        }
        if (!supportsPackageManagerState(permission) || permission.isReadOnly || permission.isPolicyFixed()) {
            return PermissionChangeResult.unsupported("Unmodifiable permission $permissionName")
        }
        if (!mPlatform.canModifyPermissions()) {
            return PermissionChangeResult.unsupported("Package Manager permission capability is unavailable")
        }
        permission.setGranted(granted)
        return try {
            persist(packageInfo.applicationInfo, permission, null, false)
            PermissionChangeResult.success()
        } catch (e: PermissionException) {
            PermissionChangeResult.failure("Could not change permission $permissionName", e)
        }
    }

    @Throws(PermissionException::class)
    private fun requireModifiable(permission: Permission) {
        if (!isModifiable(permission)) {
            throw PermissionException("Unmodifiable permission ${permission.getName()}")
        }
    }

    @Throws(PermissionException::class)
    private fun persist(
        applicationInfo: ApplicationInfo,
        permission: Permission,
        revokeReason: String?
    ) {
        persist(applicationInfo, permission, revokeReason, true)
    }

    @Throws(PermissionException::class)
    private fun persist(
        applicationInfo: ApplicationInfo,
        permission: Permission,
        revokeReason: String?,
        updatePermissionFlags: Boolean
    ) {
        val userId = UserHandleHidden.getUserId(applicationInfo.uid)
        try {
            if (permission.isGranted()) {
                mPlatform.grantPermission(applicationInfo.packageName, permission.getName(), userId)
                Log.d("PERM", "Granted %s", permission.getName())
            } else if (mPlatform.checkPermission(permission.getName(), applicationInfo.packageName, userId) == PERMISSION_GRANTED) {
                mPlatform.revokePermission(applicationInfo.packageName, permission.getName(), userId, revokeReason)
                Log.d("PERM", "Revoked %s", permission.getName())
            }
            if (updatePermissionFlags) {
                updateFlags(applicationInfo, permission, userId)
            }
            if (userId != mPlatform.getCurrentUserId()) {
                mPlatform.onPackageAltered(applicationInfo.packageName)
            }
        } catch (e: Exception) {
            throw PermissionException(e)
        }
    }

    @Throws(RemoteException::class)
    private fun updateFlags(
        applicationInfo: ApplicationInfo,
        permission: Permission,
        @UserIdInt userId: Int
    ) {
        val flags = (if (permission.isUserSet()) FLAG_PERMISSION_USER_SET else 0) or
                (if (permission.isUserFixed()) FLAG_PERMISSION_USER_FIXED else 0) or
                (if (permission.isRevokedCompat()) FLAG_PERMISSION_REVOKED_COMPAT else 0) or
                (if (permission.isReviewRequired()) FLAG_PERMISSION_REVIEW_REQUIRED else 0)
        val mask = FLAG_PERMISSION_USER_SET or
                FLAG_PERMISSION_USER_FIXED or
                FLAG_PERMISSION_REVOKED_COMPAT or
                (if (permission.isReviewRequired()) 0 else FLAG_PERMISSION_REVIEW_REQUIRED) or
                FLAG_PERMISSION_ONE_TIME or
                FLAG_PERMISSION_AUTO_REVOKED
        val checkAdjustPolicy = mPlatform.getCheckAdjustPolicyFlagPermission(applicationInfo)
        mPlatform.updatePermissionFlags(permission.getName(), applicationInfo.packageName, mask,
            flags, checkAdjustPolicy, userId)
    }
}
