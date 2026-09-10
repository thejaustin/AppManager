// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.self.SelfPermissions

interface AppOpPermissionPlatform {
    @Throws(Exception::class)
    fun checkOperation(appOpsManager: AppOpsManagerCompat, appOp: Int, uid: Int, packageName: String): Int

    @Throws(Exception::class)
    fun setMode(appOpsManager: AppOpsManagerCompat, appOp: Int, uid: Int, packageName: String, @AppOpsManagerCompat.Mode mode: Int)

    fun canKillUid(): Boolean

    @Throws(Exception::class)
    fun killUid(uid: Int, reason: String)
}

internal class FrameworkAppOpPermissionPlatform : AppOpPermissionPlatform {
    @Throws(Exception::class)
    override fun checkOperation(appOpsManager: AppOpsManagerCompat, appOp: Int, uid: Int, packageName: String): Int {
        return appOpsManager.checkOperation(appOp, uid, packageName)
    }

    @Throws(Exception::class)
    override fun setMode(appOpsManager: AppOpsManagerCompat, appOp: Int, uid: Int, packageName: String, mode: Int) {
        appOpsManager.setMode(appOp, uid, packageName, mode)
    }

    override fun canKillUid(): Boolean = SelfPermissions.canKillUid()

    @Throws(Exception::class)
    override fun killUid(uid: Int, reason: String) {
        ActivityManagerCompat.killUid(uid, reason)
    }
}
