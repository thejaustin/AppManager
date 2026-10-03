// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.IntDef
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Prefs
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.Collections

object NotificationUtils {
    private const val HIGH_PRIORITY_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.HIGH_PRIORITY"
    private const val CRASH_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.CRASH"
    private const val INSTALL_CONFIRM_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.INSTALL_CONFIRM"
    private const val FREEZE_UNFREEZE_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.FREEZE_UNFREEZE"
    @JvmField
    val HIGH_PRIORITY_NOTIFICATION_INFO: NotificationProgressHandler.NotificationManagerInfo =
        NotificationProgressHandler.NotificationManagerInfo(
            HIGH_PRIORITY_CHANNEL_ID, "Alerts", NotificationManagerCompat.IMPORTANCE_HIGH
        )

    @IntDef(
        NotificationManagerCompat.IMPORTANCE_UNSPECIFIED,
        NotificationManagerCompat.IMPORTANCE_NONE,
        NotificationManagerCompat.IMPORTANCE_MIN,
        NotificationManagerCompat.IMPORTANCE_LOW,
        NotificationManagerCompat.IMPORTANCE_DEFAULT,
        NotificationManagerCompat.IMPORTANCE_HIGH,
        NotificationManagerCompat.IMPORTANCE_MAX
    )
    @Retention(RetentionPolicy.SOURCE)
    annotation class NotificationImportance

    @IntDef(
        NotificationCompat.PRIORITY_MIN,
        NotificationCompat.PRIORITY_LOW,
        NotificationCompat.PRIORITY_DEFAULT,
        NotificationCompat.PRIORITY_HIGH,
        NotificationCompat.PRIORITY_MAX
    )
    @Retention(RetentionPolicy.SOURCE)
    annotation class NotificationPriority

    fun interface NotificationBuilder {
        fun build(builder: NotificationCompat.Builder): Notification
    }

    private val sNotificationIds: MutableMap<String?, Int> = Collections.synchronizedMap(HashMap())

    @JvmStatic
    fun nextNotificationId(tag: String?): Int {
        val id = sNotificationIds[tag]
        if (id == null) {
            sNotificationIds[tag] = 1
            return 1
        }
        val newId = id + 1
        sNotificationIds[tag] = newId
        return newId
    }

    @JvmStatic
    fun getHighPriorityNotificationBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, HIGH_PRIORITY_CHANNEL_ID)
            .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    @JvmStatic
    fun getCrashNotificationBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CRASH_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    @JvmStatic
    fun displayHighPriorityNotification(context: Context, notification: Notification) {
        displayHighPriorityNotification(context) { _ -> notification }
    }

    @JvmStatic
    fun displayHighPriorityNotification(
        context: Context,
        notification: NotificationBuilder
    ) {
        val notificationTag = "alert"
        val notificationId = nextNotificationId(notificationTag)
        displayNotification(
            context, HIGH_PRIORITY_CHANNEL_ID, "Alerts",
            NotificationManagerCompat.IMPORTANCE_HIGH, notificationTag, notificationId, notification
        )
    }

    @JvmStatic
    fun displayCrashNotification(context: Context, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                CRASH_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH
            ).setName("Crash reports").build()
        )
        if (SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            manager.notify("crash", 1, notification)
        } else {
            android.util.Log.e("NotificationUtils", "Crash notification permission is not granted")
        }
    }

    @JvmStatic
    fun displayFreezeUnfreezeNotification(
        context: Context,
        notificationTag: String?,
        notification: NotificationBuilder
    ) {
        displayNotification(
            context, FREEZE_UNFREEZE_CHANNEL_ID, "Freeze",
            NotificationManagerCompat.IMPORTANCE_DEFAULT, notificationTag, 1, notification
        )
    }

    @JvmStatic
    fun displayInstallConfirmNotification(
        context: Context,
        notification: NotificationBuilder
    ): Int {
        val notificationId = nextNotificationId(INSTALL_CONFIRM_CHANNEL_ID)
        displayNotification(
            context, INSTALL_CONFIRM_CHANNEL_ID, "Confirm Installation",
            NotificationManagerCompat.IMPORTANCE_HIGH, INSTALL_CONFIRM_CHANNEL_ID, notificationId, notification
        )
        return notificationId
    }

    @JvmStatic
    fun cancelInstallConfirmNotification(context: Context, notificationId: Int) {
        if (notificationId <= 0) {
            return
        }
        val manager = getNewNotificationManager(
            context, INSTALL_CONFIRM_CHANNEL_ID,
            "Confirm Installation", NotificationManagerCompat.IMPORTANCE_HIGH
        )
        manager.cancel(INSTALL_CONFIRM_CHANNEL_ID, notificationId)
    }

    private fun displayNotification(
        context: Context,
        channelId: String,
        channelName: CharSequence,
        @NotificationImportance importance: Int,
        notificationTag: String?,
        notificationId: Int,
        notification: NotificationBuilder
    ) {
        val manager = getNewNotificationManager(context, channelId, channelName, importance)
        val builder = NotificationCompat.Builder(context, channelId)
            .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
            .setPriority(importanceToPriority(importance))
        if (SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            manager.notify(notificationTag, notificationId, notification.build(builder))
        }
    }

    @JvmStatic
    fun getFreezeUnfreezeNotificationManager(context: Context): NotificationManagerCompat {
        return getNewNotificationManager(
            context, FREEZE_UNFREEZE_CHANNEL_ID, "Freeze",
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
    }

    @JvmStatic
    fun getNewNotificationManager(
        context: Context,
        channelId: String,
        channelName: CharSequence?,
        importance: Int
    ): NotificationManagerCompat {
        val channel = NotificationChannelCompat.Builder(channelId, importance)
            .setName(channelName)
            .build()
        val managerCompat = NotificationManagerCompat.from(context)
        managerCompat.createNotificationChannel(channel)
        return managerCompat
    }

    @NotificationPriority
    @JvmStatic
    fun importanceToPriority(@NotificationImportance importance: Int): Int {
        return when (importance) {
            NotificationManagerCompat.IMPORTANCE_HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationManagerCompat.IMPORTANCE_LOW -> NotificationCompat.PRIORITY_LOW
            NotificationManagerCompat.IMPORTANCE_MAX -> NotificationCompat.PRIORITY_MAX
            NotificationManagerCompat.IMPORTANCE_NONE,
            NotificationManagerCompat.IMPORTANCE_MIN -> NotificationCompat.PRIORITY_MIN
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
            NotificationManagerCompat.IMPORTANCE_UNSPECIFIED -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    @JvmStatic
    fun getNotificationSettingIntent(channelId: String?): Intent {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (channelId != null) {
                intent.action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            } else {
                intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            }
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"\nintent.putExtra("app_package", BuildConfig.APPLICATION_ID)
            intent.putExtra("app_uid", Process.myUid())
        }
        return intent
    }
}
