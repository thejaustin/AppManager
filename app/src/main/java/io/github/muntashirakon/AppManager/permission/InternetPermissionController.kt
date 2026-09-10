// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.Manifest
import android.net.ConnectivityManagerHidden
import android.os.Build
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.dao.PermissionOverrideDao
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride
import io.github.muntashirakon.AppManager.self.SelfPermissions

class InternetPermissionController @JvmOverloads constructor(
    private val mSdkInt: Int = Build.VERSION.SDK_INT,
    private val mDao: PermissionOverrideDao = AppsDb.getInstance().permissionOverrideDao(),
    private val mPlatform: ConnectivityPermissionOverridePlatform = ConnectivityPermissionOverridePlatform()
) : IPermissionController {

    companion object {
        val PERMISSION: String = Manifest.permission.INTERNET
        const val CONTROLLER = "internet"
        val DEFAULT_FIREWALL_CHAIN: Int = ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_3

        @Volatile
        private var sInstance: InternetPermissionController? = null

        @WorkerThread
        fun getInstance(): InternetPermissionController {
            return sInstance ?: synchronized(InternetPermissionController::class.java) {
                sInstance ?: InternetPermissionController().also { sInstance = it }
            }
        }

        @AnyThread
        fun isSupported(): Boolean = isSupportedApi(Build.VERSION.SDK_INT) && canModify()

        private fun canModify(): Boolean {
            return SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.NETWORK_SETTINGS)
        }

        private fun isSupportedApi(sdkInt: Int): Boolean {
            return sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        }
    }

    fun supports(permissionName: String): Boolean {
        return isSupportedApi(mSdkInt) && PERMISSION == permissionName && canModify()
    }

    override fun getId(): String = CONTROLLER

    override fun supports(context: PermissionContext): Boolean {
        return supports(context.permission.getName())
    }

    override fun getState(context: PermissionContext): PermissionControllerState {
        return PermissionControllerState(getId(), getState(context.packageInfo.packageName, context.userId), true, null)
    }

    override fun setGranted(context: PermissionContext, granted: Boolean): PermissionChangeResult {
        return setGranted(context.packageInfo.packageName, context.userId, granted, DEFAULT_FIREWALL_CHAIN)
    }

    fun setGranted(packageName: String, userId: Int, granted: Boolean, firewallChain: Int): PermissionChangeResult {
        if (!supports(PERMISSION)) {
            return PermissionChangeResult.unsupported("INTERNET overlay requires Android 14+")
        }
        val override = PermissionOverride(packageName, userId, PERMISSION, granted, firewallChain.toString())
        override.syncStatus = PermissionOverrideReconciler.PENDING
        mDao.insert(override)
        PermissionOverrideManager.reconcile(packageName, userId)
        return PermissionChangeResult.success()
    }

    fun getState(packageName: String, userId: Int): PermissionState {
        if (!supports(PERMISSION)) return PermissionState.UNSUPPORTED
        val override = mDao.get(packageName, userId, PERMISSION) ?: return PermissionState.UNKNOWN
        return try {
            val uid = mPlatform.resolveUid(packageName, userId)
            val enforced = mPlatform.isEnforced(uid, override)
            if (enforced) {
                if (override.desiredGranted) PermissionState.GRANTED else PermissionState.DENIED
            } else {
                PermissionState.UNKNOWN
            }
        } catch (e: Exception) {
            PermissionState.UNKNOWN
        }
    }
}
