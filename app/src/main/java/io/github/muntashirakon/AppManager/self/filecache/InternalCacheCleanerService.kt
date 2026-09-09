// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.filecache

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.PendingIntentCompat
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.io.FileSystemManager
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.util.Calendar

// IMPORTANT: This service must be run without authentication.
class InternalCacheCleanerService : ForegroundService(TAG) {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        clearOldFiles()
        clearOldImages()
    }

    private fun clearOldFiles() {
        val lastAccessDate = System.currentTimeMillis() - 259_200_000
        val fileCache = Paths.getUnprivileged(FileSystemManager.getLocal().getFile(FileUtils.getCachePath(), "files"))
        val deleteCount = deleteFilesWithAccessDate(fileCache, lastAccessDate)
        Log.i(TAG, "Deleted $deleteCount files and directories.")
    }

    private fun clearOldImages() {
        val lastAccessDate = System.currentTimeMillis() - 604_800_000
        val fileCache = Paths.getUnprivileged(FileSystemManager.getLocal().getFile(FileUtils.getCachePath(), "images"))
        val deleteCount = deleteFilesWithAccessDate(fileCache, lastAccessDate)
        Log.i(TAG, "Deleted $deleteCount images.")
    }

    companion object {
        const val TAG = "InternalCacheCleanerService"

        @JvmStatic
        fun scheduleAlarm(context: Context) {
            val intent = Intent(context, InternalCacheCleanerService::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT
            val pastAlarmIntent = PendingIntentCompat.getService(context, 0, intent, flags or PendingIntent.FLAG_NO_CREATE, false)
            if (pastAlarmIntent != null) return

            val alarmIntent = PendingIntentCompat.getService(context, 0, intent, flags, false)
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 5)
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, alarmIntent)
        }

        private fun deleteFilesWithAccessDate(basePath: Path, accessDate: Long): Int {
            var deleteCount = 0
            for (file in basePath.listFiles()) {
                var lastAccess = file.lastAccess()
                if (lastAccess <= 0) lastAccess = file.lastModified()
                if (lastAccess <= accessDate && file.delete()) ++deleteCount
            }
            return deleteCount
        }
    }
}
