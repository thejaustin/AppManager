// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.IPackageInstaller
import android.os.UserHandleHidden
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ContextUtils
import java.util.UUID

/**
 * Keeps enough ownership information to abandon sessions orphaned by process death.
 */
internal object PackageInstallerSessionRegistry {
    const val PREFS_NAME = "package_installer_sessions"
    const val KEY_PREFIX = "session_"

    private val TAG = PackageInstallerSessionRegistry::class.java.simpleName
    private val PROCESS_TOKEN: String = UUID.randomUUID().toString()

    interface SessionBackend {
        @Throws(Exception::class)
        fun getSessionIds(): Set<Int>

        @Throws(Exception::class)
        fun abandonSession(sessionId: Int)
    }

    @Synchronized
    fun cleanupOrphanedSessions(packageInstaller: IPackageInstaller) {
        val context = ContextUtils.getContext()
        cleanupOrphanedSessions(context, object : SessionBackend {
            override fun getSessionIds(): Set<Int> =
                packageInstaller.getMySessions(context.packageName, UserHandleHidden.myUserId())
                    .list.mapTo(HashSet()) { it.sessionId }

            override fun abandonSession(sessionId: Int) {
                packageInstaller.abandonSession(sessionId)
            }
        })
    }

    @Synchronized
    fun cleanupOrphanedSessions(context: Context, backend: SessionBackend) {
        val preferences = getPreferences(context)
        val records = preferences.all
        if (records.isEmpty()) return
        val liveSessionIds = try {
            backend.getSessionIds()
        } catch (e: Exception) {
            Log.w(TAG, "Could not query package installer sessions.", e)
            return
        }
        val editor = preferences.edit()
        var changed = false
        for ((key, value) in records) {
            if (!key.startsWith(KEY_PREFIX) || PROCESS_TOKEN == value) continue
            val sessionId = try {
                key.substring(KEY_PREFIX.length).toInt()
            } catch (e: NumberFormatException) {
                editor.remove(key)
                changed = true
                continue
            }
            if (sessionId !in liveSessionIds) {
                editor.remove(key)
                changed = true
                continue
            }
            try {
                backend.abandonSession(sessionId)
                editor.remove(key)
                changed = true
                Log.i(TAG, "Abandoned orphaned package installer session %d.", sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not abandon orphaned package installer session %d.", e, sessionId)
            }
        }
        if (changed && !editor.commit()) {
            Log.w(TAG, "Could not persist package installer session cleanup.")
        }
    }

    @Synchronized
    fun record(sessionId: Int) {
        if (sessionId < 0) return
        val persisted = getPreferences(ContextUtils.getContext()).edit()
            .putString(KEY_PREFIX + sessionId, PROCESS_TOKEN)
            .commit()
        if (!persisted) {
            Log.w(TAG, "Could not persist package installer session %d.", sessionId)
        }
    }

    @Synchronized
    fun forget(sessionId: Int) {
        if (sessionId < 0) return
        getPreferences(ContextUtils.getContext()).edit().remove(KEY_PREFIX + sessionId).commit()
    }

    fun getProcessToken(): String = PROCESS_TOKEN

    fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
