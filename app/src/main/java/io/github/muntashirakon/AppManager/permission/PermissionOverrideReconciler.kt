// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import io.github.muntashirakon.AppManager.db.dao.PermissionOverrideDao
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride
import io.github.muntashirakon.AppManager.types.UserPackagePair
import java.util.LinkedHashSet
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class PermissionOverrideReconciler(
    private val mDao: PermissionOverrideDao,
    private val mPlatform: Platform
) {
    companion object {
        const val SYNCED = 0
        const val PENDING = 1
        const val FAILED = 2
    }

    interface Platform {
        @Throws(Exception::class)
        fun resolveUid(packageName: String, userId: Int): Int

        @Throws(Exception::class)
        fun isEnforced(uid: Int, override: PermissionOverride): Boolean

        @Throws(Exception::class)
        fun apply(uid: Int, override: PermissionOverride)
    }

    private val mExecutor: Executor = Executors.newSingleThreadExecutor()
    private val mStateLock = Any()
    private val mQueuedKeys = HashSet<UserPackagePair>()

    fun reconcile(packageName: String, userId: Int) {
        val key = UserPackagePair(packageName, userId)
        synchronized(mQueuedKeys) {
            if (!mQueuedKeys.add(key)) return
        }
        mExecutor.execute {
            try {
                reconcileNow(packageName, userId)
            } finally {
                synchronized(mQueuedKeys) {
                    mQueuedKeys.remove(key)
                }
            }
        }
    }

    fun reconcileAll() {
        mExecutor.execute {
            val targets = LinkedHashSet<UserPackagePair>()
            for (override in mDao.getAll()) {
                targets.add(UserPackagePair(override.packageName, override.userId))
            }
            val ownedTargets = LinkedHashSet<UserPackagePair>()
            synchronized(mQueuedKeys) {
                for (target in targets) {
                    if (mQueuedKeys.add(target)) {
                        ownedTargets.add(target)
                    }
                }
            }
            for (target in ownedTargets) {
                try {
                    reconcileNow(target.packageName, target.userId)
                } finally {
                    synchronized(mQueuedKeys) {
                        mQueuedKeys.remove(target)
                    }
                }
            }
        }
    }

    fun remove(packageName: String, userId: Int) {
        mExecutor.execute { removeNow(packageName, userId) }
    }

    fun removeNow(packageName: String, userId: Int) {
        synchronized(mStateLock) {
            mDao.deleteForPackage(packageName, userId)
        }
    }

    fun reconcileNow(packageName: String, userId: Int) {
        synchronized(mStateLock) {
            val overrides = mDao.getForPackage(packageName, userId)
            if (overrides.isEmpty()) return
            val uid: Int
            try {
                uid = mPlatform.resolveUid(packageName, userId)
            } catch (e: Exception) {
                for (override in overrides) {
                    override.syncStatus = FAILED
                    mDao.insert(override)
                }
                return
            }
            for (override in overrides) {
                override.syncStatus = PENDING
                mDao.insert(override)
                try {
                    if (!mPlatform.isEnforced(uid, override)) mPlatform.apply(uid, override)
                    override.syncStatus = SYNCED
                    override.syncTime = System.currentTimeMillis()
                } catch (e: Exception) {
                    override.syncStatus = FAILED
                }
                mDao.insert(override)
            }
        }
    }
}
