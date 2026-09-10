// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.content.pm.PackageInfo
import android.os.Build

internal object PermissionMutation {
    @Throws(PermissionException::class)
    fun prepareGrant(
        packageInfo: PackageInfo,
        permission: Permission,
        setByTheUser: Boolean,
        fixedByTheUser: Boolean
    ): Boolean {
        var killApp = false
        if (!permission.isReadOnly && (!permission.isRuntime || supportsRuntimePermissions(packageInfo))) {
            if (permission.affectsAppOp() && !permission.isAppOpAllowed()) {
                permission.setAppOpAllowed(true)
            }
            if (!permission.isGranted()) {
                permission.setGranted(true)
            }
            if (!fixedByTheUser) {
                if (permission.isUserFixed()) {
                    permission.setUserFixed(false)
                }
                if (setByTheUser && !permission.isUserSet()) {
                    permission.setUserSet(true)
                }
            } else {
                if (!permission.isUserFixed()) {
                    permission.setUserFixed(true)
                }
                if (permission.isUserSet()) {
                    permission.setUserSet(false)
                }
            }
        } else {
            ensureLegacyRuntimePermissionIsGranted(permission)
            if (permission.affectsAppOp()) {
                if (!permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(true)
                    killApp = true
                }
                if (permission.isRevokedCompat()) {
                    permission.setRevokedCompat(false)
                }
            }
            if (permission.isReviewRequired()) {
                permission.unsetReviewRequired()
            }
        }
        return killApp
    }

    @Throws(PermissionException::class)
    fun prepareRevoke(
        packageInfo: PackageInfo,
        permission: Permission,
        fixedByTheUser: Boolean
    ): Boolean {
        var killApp = false
        if (!permission.isReadOnly && (!permission.isRuntime || supportsRuntimePermissions(packageInfo))) {
            if (permission.isGranted()) {
                permission.setGranted(false)
            }
            if (fixedByTheUser) {
                if (permission.isUserSet() || !permission.isUserFixed()) {
                    permission.setUserSet(false)
                    permission.setUserFixed(true)
                }
            } else if (!permission.isUserSet() || permission.isUserFixed()) {
                permission.setUserSet(true)
                permission.setUserFixed(false)
            }
            if (permission.affectsAppOp()) {
                permission.setAppOpAllowed(false)
            }
        } else {
            ensureLegacyRuntimePermissionIsGranted(permission)
            if (permission.affectsAppOp()) {
                if (permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(false)
                    killApp = true
                }
                if (!permission.isRevokedCompat()) {
                    permission.setRevokedCompat(true)
                }
            }
        }
        return killApp
    }

    @Throws(PermissionException::class)
    private fun ensureLegacyRuntimePermissionIsGranted(permission: Permission) {
        if (permission.isRuntime && !permission.isGranted()) {
            throw PermissionException("Legacy app cannot have not-granted runtime permission ${permission.getName()}")
        }
    }

    private fun supportsRuntimePermissions(packageInfo: PackageInfo): Boolean {
        return packageInfo.applicationInfo.targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1
    }
}
