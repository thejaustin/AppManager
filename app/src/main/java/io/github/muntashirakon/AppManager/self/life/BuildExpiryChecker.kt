// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.life

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import java.util.Locale

object BuildExpiryChecker {
    private const val BUILD_TYPE_DEBUG = 0
    private const val BUILD_TYPE_ALPHA = 1
    private const val BUILD_TYPE_BETA = 2
    private const val BUILD_TYPE_RC = 3
    private const val BUILD_TYPE_STABLE = 4

    private val TIME_SPAN_MILLIS = longArrayOf(
        2L * 30 * 24 * 60 * 60_000,  // 2 months
        6L * 30 * 24 * 60 * 60_000,  // 6 months
        6L * 30 * 24 * 60 * 60_000,  // 6 months
        6L * 30 * 24 * 60 * 60_000,  // 6 months
        18L * 30 * 24 * 60 * 60_000, // 18 months
    )

    private val WARNING_PERIOD_MILLIS = longArrayOf(
        14L * 24 * 60 * 60_000,       // 2 weeks
        30L * 24 * 60 * 60_000,       // 1 month
        30L * 24 * 60 * 60_000,       // 1 month
        30L * 24 * 60 * 60_000,       // 1 month
        3L * 30 * 24 * 60 * 60_000,   // 3 months
    )

    fun getBuildExpiredDialog(
        activity: FragmentActivity,
        continueClickListener: DialogInterface.OnClickListener?
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_manager_build_expired)
            .setMessage(R.string.app_manager_build_expired_message)
            .setCancelable(false)
            .setPositiveButton(R.string.update) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    data = getUpdateUri()
                }
                activity.startActivity(intent)
                activity.finishAndRemoveTask()
            }
            .setNeutralButton(R.string.uninstall) { _, _ ->
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                }
                activity.startActivity(intent)
                activity.finishAndRemoveTask()
            }
        if (getBuildType() == BUILD_TYPE_STABLE) {
            builder.setNeutralButton(R.string.action_continue, continueClickListener)
        }
        return builder.create()
    }

    fun buildExpired(): Boolean? {
        val buildType = getBuildType()
        val timeSpan = System.currentTimeMillis() - BuildConfig.BUILD_TIME_MILLIS
        val realTimeSpan = TIME_SPAN_MILLIS[buildType]
        if (timeSpan <= realTimeSpan) {
            return false
        }
        val warningPeriod = WARNING_PERIOD_MILLIS[buildType]
        if (timeSpan <= realTimeSpan + warningPeriod) {
            return null
        }
        return true
    }

    private fun getUpdateUri(): Uri {
        if (getBuildType() == BUILD_TYPE_DEBUG) {
            return Uri.parse("https://github.com/MuntashirAkon/AppManager/actions/workflows/debug_build.yml")
        }
        // TODO: 3/12/22 For Stable builds, check F-Droid too
        return Uri.parse("https://github.com/MuntashirAkon/AppManager/releases")
    }

    private fun getBuildType(): Int {
        if (BuildConfig.DEBUG) return BUILD_TYPE_DEBUG
        val versionParts = BuildConfig.VERSION_NAME.split("-")
        if (versionParts.size == 1) return BUILD_TYPE_STABLE
        val lastPart = versionParts.last()
        return when (lastPart.substring(0, lastPart.length - 2).lowercase(Locale.ROOT)) {
            "alpha" -> BUILD_TYPE_ALPHA
            "beta" -> BUILD_TYPE_BETA
            "rc" -> BUILD_TYPE_RC
            else -> throw IllegalStateException("Invalid App Manager version")
        }
    }
}
