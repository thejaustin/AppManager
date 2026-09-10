// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer
import io.github.muntashirakon.AppManager.accessibility.NoRootAccessibilityService
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.ThreadUtils

class LeadingActivityTrackerActivity : BaseActivity() {
    private val mSettingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { init() }

    private val mUsageAccessSettingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { init() }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        init()
    }

    override fun getTransparentBackground(): Boolean = true

    private fun init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.grant_required_permission)
                .setMessage(R.string.grant_overlay_permission_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    mSettingsLauncher.launch(intent)
                }
                .setNegativeButton(R.string.go_back) { _, _ -> finish() }
                .show()
            return
        }
        if (!SelfPermissions.checkUsageStatsPermission()) {
            ThreadUtils.postOnMainThread {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.grant_usage_access)
                    .setMessage(R.string.grant_usage_acess_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.go) { _, _ ->
                        mUsageAccessSettingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    .setNegativeButton(R.string.go_back) { _, _ -> finish() }
                    .show()
            }
            return
        }
        if (!NoRootAccessibilityService.isAccessibilityEnabled(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.grant_required_permission)
                .setMessage(R.string.grant_accessibility_permission_for_tracking_window_contents)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ ->
                    mSettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(R.string.go_back) { _, _ -> finish() }
                .show()
            return
        }
        AccessibilityMultiplexer.getInstance().enableLeadingActivityTracker(true)
        finish()
    }
}
