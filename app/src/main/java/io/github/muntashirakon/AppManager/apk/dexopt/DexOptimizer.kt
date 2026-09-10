// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt

import android.content.pm.IPackageManager
import android.os.Build
import android.os.RemoteException
import androidx.annotation.RequiresApi
import io.github.muntashirakon.AppManager.self.SelfPermissions
import java.util.Objects

@RequiresApi(Build.VERSION_CODES.N)
class DexOptimizer(private val mPm: IPackageManager, private val mPackageName: String) {
    private var mLastError: Exception? = null

    fun getLastError(): Exception? = mLastError.also { mLastError = null }

    @Suppress("DEPRECATION")
    fun performDexOptMode(
        checkProfiles: Boolean,
        targetCompilerFilter: String,
        force: Boolean,
        bootComplete: Boolean,
        splitName: String?,
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                mPm.performDexOptMode(mPackageName, checkProfiles, targetCompilerFilter, force, bootComplete, splitName)
            } else {
                mPm.performDexOptMode(mPackageName, checkProfiles, targetCompilerFilter, force)
            }
        } catch (e: RemoteException) {
            mLastError = e; false
        } catch (e: SecurityException) {
            mLastError = e; false
        }
    }

    fun clearApplicationProfileData(): Boolean {
        return try {
            mPm.clearApplicationProfileData(mPackageName)
            true
        } catch (e: RemoteException) {
            mLastError = e; false
        } catch (e: SecurityException) {
            mLastError = e; false
        }
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun compileLayouts(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        return try {
            mPm.compileLayouts(mPackageName)
        } catch (e: RemoteException) {
            mLastError = e; false
        } catch (e: SecurityException) {
            mLastError = e; false
        }
    }

    fun forceDexOpt(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && SelfPermissions.isSystemOrRoot()) {
                try {
                    mPm.forceDexOpt(mPackageName)
                    return true
                } catch (e: IllegalArgumentException) {
                    if (Objects.equals(e.message, "reason -1 invalid")) {
                        // AOSP bug: https://github.com/MuntashirAkon/AppManager/issues/1131
                        return forceDexOptUnprivileged()
                    }
                    throw e
                }
            }
            forceDexOptUnprivileged()
        } catch (e: RemoteException) {
            mLastError = e; false
        } catch (e: SecurityException) {
            mLastError = e; false
        } catch (e: IllegalArgumentException) {
            mLastError = e; false
        } catch (e: IllegalStateException) {
            val message = e.message
            if (message != null && message.startsWith("Failed to dexopt: 0")) {
                return true
            }
            mLastError = e; false
        }
    }

    private fun forceDexOptUnprivileged(): Boolean =
        performDexOptMode(false, DexOptOptions.getDefaultCompilerFilter(), true, true, null)
}
