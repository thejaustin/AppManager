// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.NotificationUtils

class AMExceptionHandler(private val mContext: Context) : Thread.UncaughtExceptionHandler {
    private val mDefaultExceptionHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val report = StringBuilder("$e\n")
            e.stackTrace.forEach { report.append("    at $it\n") }
            var cause: Throwable? = e.cause
            while (cause != null) {
                report.append(" Caused by: $cause\n")
                cause.stackTrace.forEach { report.append("   at $it\n") }
                cause = cause.cause
            }
            report.append("\nDevice Info:\n")
            report.append(DeviceInfo(mContext))

            val i = Intent(Intent.ACTION_SEND).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identifier = System.currentTimeMillis().toString()
                }
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "App Manager: Crash report")
                putExtra(Intent.EXTRA_TEXT, report.toString())
            }
            val pendingIntent = PendingIntentCompat.getActivity(
                mContext, 0,
                Intent.createChooser(i, mContext.getText(R.string.send_crash_report)),
                PendingIntent.FLAG_ONE_SHOT, false
            )
            val builder = NotificationUtils.getCrashNotificationBuilder(mContext)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_default_notification)
                .setTicker(mContext.getText(R.string.app_name))
                .setContentTitle(mContext.getText(R.string.am_crashed))
                .setContentText(mContext.getText(R.string.tap_to_submit_crash_report))
                .setContentIntent(pendingIntent)
            NotificationUtils.displayCrashNotification(mContext, builder.build())
            android.os.SystemClock.sleep(250)
        } catch (reportError: Throwable) {
            android.util.Log.e("AMExceptionHandler", "Unable to display crash notification", reportError)
        }

        mDefaultExceptionHandler?.uncaughtException(t, e)
    }

    companion object {
        private const val EMAIL = "am4android@riseup.net"
    }
}
