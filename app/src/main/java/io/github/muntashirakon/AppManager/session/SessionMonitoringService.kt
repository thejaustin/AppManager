// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.session

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.DummyActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.misc.ScreenLockChecker
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.util.concurrent.Future

class SessionMonitoringService : Service() {

    private var mIsWorking = false
    private var mCheckLockResult: Future<*>? = null
    private var mScreenLockChecker: ScreenLockChecker? = null
    private var mScreenLockedReceiverRegistered = false

    private val mScreenLockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                mCheckLockResult?.cancel(true)
                mCheckLockResult = ThreadUtils.postOnBackgroundThread {
                    if (mScreenLockChecker == null) {
                        mScreenLockChecker = ScreenLockChecker(this@SessionMonitoringService) { lockScreen() }
                    }
                    mScreenLockChecker!!.checkLock()
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && STOP_ACTION == intent.action) {
            lockScreen()
            return START_NOT_STICKY
        }
        if (mIsWorking) return START_NOT_STICKY
        mIsWorking = true
        val screenLockEnabled = Prefs.Privacy.isScreenLockEnabled()
        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "Session Monitor", NotificationManagerCompat.IMPORTANCE_LOW)
        val notificationSettingIntent = NotificationUtils.getNotificationSettingIntent(CHANNEL_ID)
        val defaultIntent = PendingIntentCompat.getActivity(this, 0, notificationSettingIntent, PendingIntent.FLAG_UPDATE_CURRENT, false)
        val stopIntent = Intent(this, SessionMonitoringService::class.java).setAction(STOP_ACTION)
        val pendingIntent = PendingIntentCompat.getService(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT, false)
        val stopServiceAction = NotificationCompat.Action.Builder(
            null,
            getString(if (screenLockEnabled) R.string.action_lock_app else R.string.action_stop_service),
            pendingIntent
        ).setAuthenticationRequired(true).build()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentTitle(getString(if (screenLockEnabled) R.string.app_manager_is_unlocked else R.string.app_manager_is_running))
            .setContentText(getString(R.string.tap_to_open_notification_settings))
            .setSmallIcon(R.drawable.ic_default_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(defaultIntent)
            .addAction(stopServiceAction)
        ForegroundService.start(
            this,
            NotificationUtils.nextNotificationId(null),
            builder.build(),
            ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        if (screenLockEnabled && Prefs.Privacy.isAutoLockEnabled()) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(this, mScreenLockedReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            mScreenLockedReceiverRegistered = true
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent) {
        // https://issuetracker.google.com/issues/36967794
        val intent = Intent(this, DummyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        if (mScreenLockedReceiverRegistered) {
            unregisterReceiver(mScreenLockedReceiver)
            mScreenLockedReceiverRegistered = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    fun lockScreen() {
        // Simply stopping the service is enough
        // TODO: 16/7/23 Wipe memory? Ref: https://github.com/mollyim/mollyim-android/blob/2f8fe769628f7daddc87e8acfab1c4b5d301f728/app/src/main/java/org/thoughtcrime/securesms/service/WipeMemoryService.java#L102
        stopSelf()
        Process.killProcess(Process.myPid())
    }

    companion object {
        const val TAG = "SessionMonitoringService"
        const val CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.SESSION_MONITOR"
        const val STOP_ACTION = BuildConfig.APPLICATION_ID + ".action.STOP_SESSION_MONITOR"
    }
}
