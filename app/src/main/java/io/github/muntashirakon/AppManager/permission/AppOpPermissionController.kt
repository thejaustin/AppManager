// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.annotation.UserIdInt
import android.app.AppOpsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.UserHandleHidden
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_AUTO_REVOKED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_ONE_TIME
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKED_COMPAT
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET
import io.github.muntashirakon.AppManager.logs.Log

class AppOpPermissionController(
    private val mPackageManagerPlatform: PackageManagerPermissionPlatform,
    private val mAppOpPlatform: AppOpPermissionPlatform
) : IPermissionController {

    companion object {
        private const val KILL_REASON_APP_OP_CHANGE = "Permission related app op changed"

        private val INSTANCE: AppOpPermissionController = AppOpPermissionController(
            FrameworkPackageManagerPermissionPlatform(),
            FrameworkAppOpPermissionPlatform()
        )

        fun getInstance(): AppOpPermissionController = INSTANCE
    }

    fun supports(permission: Permission): Boolean = permission.affectsAppOp()

    override fun getId(): String = "app-op"

    override fun supports(context: PermissionContext): Boolean = supports(context.permission)

    override fun getState(context: PermissionContext): PermissionControllerState {
        return PermissionControllerState(
            getId(),
            if (context.permission.isAppOpAllowed()) PermissionState.GRANTED else PermissionState.DENIED,
            isModifiable(context.permission),
            null
        )
    }

    override fun setGranted(context: PermissionContext, granted: Boolean): PermissionChangeResult {
        if (context.appOps == null) {
            return PermissionChangeResult.failure("AppOps service unavailable", null)
        }
        return try {
            if (granted) {
                grant(context.packageInfo, context.permission, context.appOps, context.setByUser, context.fixedByUser)
            } else {
                revoke(context.packageInfo, context.permission, context.appOps, context.fixedByUser)
            }
            PermissionChangeResult.success()
        } catch (e: PermissionException) {
            PermissionChangeResult.failure(e.message, e)
        }
    }

    fun isModifiable(permission: Permission): Boolean {
        return supports(permission) && mPackageManagerPlatform.canModifyPermissions()
    }

    @WorkerThread
    @Throws(PermissionException::class)
    fun grant(
        packageInfo: PackageInfo,
        permission: Permission,
        appOpsManager: AppOpsManagerCompat,
        setByTheUser: Boolean,
        fixedByTheUser: Boolean
    ) {
        requireModifiable(permission)
        val killApp = PermissionMutation.prepareGrant(packageInfo, permission, setByTheUser, fixedByTheUser)
        persist(packageInfo.applicationInfo, permission, appOpsManager, null)
        killIfNeeded(packageInfo.applicationInfo.uid, killApp)
    }

    @WorkerThread
    @Throws(PermissionException::class)
    fun revoke(
        packageInfo: PackageInfo,
        permission: Permission,
        appOpsManager: AppOpsManagerCompat,
        fixedByTheUser: Boolean
    ) {
        revoke(packageInfo, permission, appOpsManager, fixedByTheUser, null)
    }

    @WorkerThread
    @Throws(PermissionException::class)
    fun revoke(
        packageInfo: PackageInfo,
        permission: Permission,
        appOpsManager: AppOpsManagerCompat,
        fixedByTheUser: Boolean,
        reason: String?
    ) {
        requireModifiable(permission)
        val killApp = PermissionMutation.prepareRevoke(packageInfo, permission, fixedByTheUser)
        persist(packageInfo.applicationInfo, permission, appOpsManager, reason)
        killIfNeeded(packageInfo.applicationInfo.uid, killApp)
    }

    @WorkerThread
    fun trySetGrantedForBatch(
        packageInfo: PackageInfo,
        permissionName: String,
        @UserIdInt userId: Int,
        appOpsManager: AppOpsManagerCompat,
        granted: Boolean
    ): PermissionChangeResult {
        if (UserHandleHidden.getUserId(packageInfo.applicationInfo.uid) != userId) {
            return PermissionChangeResult.failure("Package user does not match requested user $userId", null)
        }
        if (!mPackageManagerPlatform.canModifyPermissions()) {
            return PermissionChangeResult.unsupported("AppOp permission capability is unavailable")
        }
        return try {
            val appOp = AppOpsManagerCompat.permissionToOpCode(permissionName)
            if (appOp == AppOpsManagerCompat.OP_NONE) {
                return PermissionChangeResult.unsupported("Permission has no AppOp $permissionName")
            }
            val mode = mAppOpPlatform.checkOperation(appOpsManager, appOp,
                packageInfo.applicationInfo.uid, packageInfo.packageName)
            val appOpAllowed = mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND
            val permission = PermissionResolver.resolve(mPackageManagerPlatform, packageInfo,
                permissionName, userId, appOpAllowed)

            val legacyRuntime = permission.isRuntime &&
                    packageInfo.applicationInfo.targetSdkVersion <= android.os.Build.VERSION_CODES.LOLLIPOP_MR1
            if (legacyRuntime && !permission.isGranted()) {
                return PermissionChangeResult.unsupported(
                    "Legacy app cannot have not-granted runtime permission $permissionName")
            }
            val killApp = permission.isAppOpAllowed() != granted && (permission.isReadOnly || legacyRuntime)
            if (!permission.isReadOnly && !legacyRuntime) {
                permission.setGranted(granted)
            }
            permission.setAppOpAllowed(granted)
            persist(packageInfo.applicationInfo, permission, appOpsManager, null, false)
            killIfNeeded(packageInfo.applicationInfo.uid, killApp)
            PermissionChangeResult.success()
        } catch (e: Exception) {
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
    private fun killIfNeeded(uid: Int, killApp: Boolean) {
        if (!killApp || !mAppOpPlatform.canKillUid()) return
        try {
            mAppOpPlatform.killUid(uid, KILL_REASON_APP_OP_CHANGE)
        } catch (e: Exception) {
            throw PermissionException(e)
        }
    }

    @Throws(PermissionException::class)
    private fun persist(
        applicationInfo: ApplicationInfo,
        permission: Permission,
        appOpsManager: AppOpsManagerCompat,
        revokeReason: String?
    ) {
        persist(applicationInfo, permission, appOpsManager, revokeReason, true)
    }

    @Throws(PermissionException::class)
    private fun persist(
        applicationInfo: ApplicationInfo,
        permission: Permission,
        appOpsManager: AppOpsManagerCompat,
        revokeReason: String?,
        updatePermissionFlags: Boolean
    ) {
        val uid = applicationInfo.uid
        val userId = UserHandleHidden.getUserId(uid)
        try {
            if (!permission.isReadOnly) {
                if (permission.isGranted()) {
                    mPackageManagerPlatform.grantPermission(applicationInfo.packageName, permission.getName(), userId)
                    Log.d("PERM", "Granted %s", permission.getName())
                } else if (mPackageManagerPlatform.checkPermission(permission.getName(),
                        applicationInfo.packageName, userId) == PERMISSION_GRANTED) {
                    mPackageManagerPlatform.revokePermission(applicationInfo.packageName,
                        permission.getName(), userId, revokeReason)
                    Log.d("PERM", "Revoked %s", permission.getName())
                }
            }

            if (updatePermissionFlags && !permission.isReadOnly) {
                updateFlags(applicationInfo, permission, userId)
            }

            if (!permission.isSystemFixed()) {
                setAppOpMode(
                    appOpsManager, permission.getAppOp(), applicationInfo.packageName, uid,
                    if (permission.isAppOpAllowed()) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED
                )
            }

            if (userId != mPackageManagerPlatform.getCurrentUserId()) {
                mPackageManagerPlatform.onPackageAltered(applicationInfo.packageName)
            }
        } catch (e: Exception) {
            throw PermissionException(e)
        }
    }

    @Throws(Exception::class)
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
        val checkAdjustPolicy = mPackageManagerPlatform.getCheckAdjustPolicyFlagPermission(applicationInfo)
        mPackageManagerPlatform.updatePermissionFlags(permission.getName(),
            applicationInfo.packageName, mask, flags, checkAdjustPolicy, userId)
    }

    @Throws(PermissionException::class)
    fun setAppOpMode(
        appOpsManager: AppOpsManagerCompat,
        appOp: Int,
        packageName: String,
        uid: Int,
        @AppOpsManagerCompat.Mode mode: Int
    ): Boolean {
        try {
            val currentMode = mAppOpPlatform.checkOperation(appOpsManager, appOp, uid, packageName)
            if (currentMode == mode) return false
            mAppOpPlatform.setMode(appOpsManager, appOp, uid, packageName, mode)
            return true
        } catch (e: Exception) {
            throw PermissionException(e)
        }
    }
}
