// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.annotation.UserIdInt
import android.content.pm.PackageInfo
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat

class PermissionControllerRegistry internal constructor(
    private val mPackageManagerController: PackageManagerPermissionController,
    private val mAppOpController: AppOpPermissionController,
    private val mSpecialPermissionController: SpecialPermissionController
) {
    @Volatile
    private var mInternetController: InternetPermissionController? = null

    companion object {
        private val INSTANCE: PermissionControllerRegistry = PermissionControllerRegistry(
            PackageManagerPermissionController.getInstance(),
            AppOpPermissionController.getInstance(),
            SpecialPermissionController.getInstance()
        )

        fun getInstance(): PermissionControllerRegistry = INSTANCE
    }

    fun isOverlayModifiable(permissionName: String): Boolean {
        return InternetPermissionController.isSupported()
                && InternetPermissionController.PERMISSION == permissionName
    }

    fun isModifiable(permission: Permission): Boolean {
        if (isOverlayModifiable(permission.getName())) return true
        if (mAppOpController.supports(permission)) {
            return mAppOpController.isModifiable(permission)
        }
        return mPackageManagerController.isModifiable(permission)
    }

    fun getOverlayState(packageName: String, userId: Int, permissionName: String): PermissionState {
        if (isOverlayModifiable(permissionName)) {
            return internetController().getState(packageName, userId)
        }
        return PermissionState.UNSUPPORTED
    }

    private fun internetController(): InternetPermissionController {
        return mInternetController ?: synchronized(this) {
            mInternetController ?: InternetPermissionController.getInstance().also { mInternetController = it }
        }
    }

    fun resolve(context: PermissionContext): IPermissionController {
        val providers = mutableListOf<IPermissionController>()
        if (InternetPermissionController.PERMISSION == context.permission.getName()
                && InternetPermissionController.isSupported()) {
            providers.add(internetController())
        } else {
            if (mPackageManagerController.supportsPackageManagerState(context.permission)) {
                providers.add(mPackageManagerController)
            }
            if (mAppOpController.supports(context.permission)) {
                providers.add(mAppOpController)
            }
        }
        if (providers.isEmpty() && mSpecialPermissionController.supports(context.permission.getName())) {
            providers.add(mSpecialPermissionController)
        }
        return CompositePermissionController(providers)
    }

    fun trySetGrantedForBatch(
        packageInfo: PackageInfo,
        permissionName: String,
        @UserIdInt userId: Int,
        appOpsManager: AppOpsManagerCompat,
        granted: Boolean
    ): PermissionChangeResult {
        if (isOverlayModifiable(permissionName)) {
            return internetController().setGranted(packageInfo.packageName, userId, granted,
                InternetPermissionController.DEFAULT_FIREWALL_CHAIN)
        }
        var result: PermissionChangeResult
        if (AppOpsManagerCompat.permissionToOpCode(permissionName) != AppOpsManagerCompat.OP_NONE) {
            result = mAppOpController.trySetGrantedForBatch(packageInfo, permissionName, userId,
                appOpsManager, granted)
        } else {
            result = mPackageManagerController.trySetPlatformGranted(packageInfo, permissionName,
                userId, granted)
        }
        if (result.status == PermissionChangeResult.Status.UNSUPPORTED
                && mSpecialPermissionController.supports(permissionName)) {
            return mSpecialPermissionController.requestChange(permissionName, granted)
        }
        return result
    }
}
