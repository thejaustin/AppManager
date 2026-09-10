// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.utils.ThreadUtils

object PermissionOverrideManager {
    @Volatile
    private var sReconciler: PermissionOverrideReconciler? = null

    @WorkerThread
    private fun reconciler(): PermissionOverrideReconciler {
        return sReconciler ?: synchronized(PermissionOverrideManager::class.java) {
            sReconciler ?: PermissionOverrideReconciler(
                AppsDb.getInstance().permissionOverrideDao(),
                ConnectivityPermissionOverridePlatform()
            ).also { sReconciler = it }
        }
    }

    @AnyThread
    fun reconcileAll() {
        ThreadUtils.postOnBackgroundThread { reconciler().reconcileAll() }
    }

    @AnyThread
    fun reconcile(packageName: String, userId: Int) {
        ThreadUtils.postOnBackgroundThread { reconciler().reconcile(packageName, userId) }
    }

    @AnyThread
    fun remove(packageName: String, userId: Int) {
        ThreadUtils.postOnBackgroundThread { reconciler().remove(packageName, userId) }
    }

    @WorkerThread
    fun removeNow(packageName: String, userId: Int) {
        reconciler().removeNow(packageName, userId)
    }
}
