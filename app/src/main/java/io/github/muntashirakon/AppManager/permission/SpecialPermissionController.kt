// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.Manifest
import android.os.Build
import android.provider.Settings

class SpecialPermissionController internal constructor(sdkInt: Int) : IPermissionController {
    private val mUserActions: Map<String, PermissionUserAction>

    init {
        val actions = mutableMapOf<String, PermissionUserAction>()
        if (sdkInt >= Build.VERSION_CODES.M) {
            add(actions, Manifest.permission.ACCESS_NOTIFICATION_POLICY, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false)
            add(actions, Manifest.permission.PACKAGE_USAGE_STATS, Settings.ACTION_USAGE_ACCESS_SETTINGS, true)
            add(actions, Manifest.permission.SYSTEM_ALERT_WINDOW, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
            add(actions, Manifest.permission.WRITE_SETTINGS, Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
        }
        if (sdkInt >= Build.VERSION_CODES.O) {
            add(actions, Manifest.permission.REQUEST_INSTALL_PACKAGES, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, true)
        }
        if (sdkInt >= Build.VERSION_CODES.R) {
            add(actions, Manifest.permission.MANAGE_EXTERNAL_STORAGE, Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, true)
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            add(actions, Manifest.permission.MANAGE_MEDIA, Settings.ACTION_REQUEST_MANAGE_MEDIA, true)
            add(actions, Manifest.permission.SCHEDULE_EXACT_ALARM, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true)
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(actions, Manifest.permission.POST_NOTIFICATIONS, Settings.ACTION_APP_NOTIFICATION_SETTINGS, true)
        }
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(actions, Manifest.permission.RUN_USER_INITIATED_JOBS, "android.settings.MANAGE_APP_LONG_RUNNING_JOBS", true)
            add(actions, Manifest.permission.USE_FULL_SCREEN_INTENT, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true)
        }
        if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            add(actions, Manifest.permission.MEDIA_ROUTING_CONTROL, Settings.ACTION_REQUEST_MEDIA_ROUTING_CONTROL, true)
        }
        add(actions, Manifest.permission.BIND_ACCESSIBILITY_SERVICE, Settings.ACTION_ACCESSIBILITY_SETTINGS, false)
        add(actions, Manifest.permission.BIND_INPUT_METHOD, Settings.ACTION_INPUT_METHOD_SETTINGS, false)
        if (sdkInt >= Build.VERSION_CODES.O) {
            add(actions, Manifest.permission.BIND_AUTOFILL_SERVICE, Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, true)
        }
        if (sdkInt >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            add(actions, Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false)
        }
        if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            add(actions, Manifest.permission.BIND_CREDENTIAL_PROVIDER_SERVICE, Settings.ACTION_CREDENTIAL_PROVIDER, true)
        }
        mUserActions = actions.toMap()
    }

    companion object {
        private val INSTANCE: SpecialPermissionController = SpecialPermissionController(Build.VERSION.SDK_INT)

        fun getInstance(): SpecialPermissionController = INSTANCE

        private fun add(
            actions: MutableMap<String, PermissionUserAction>,
            permissionName: String,
            action: String,
            supportsPackage: Boolean
        ) {
            actions[permissionName] = PermissionUserAction(action, supportsPackage)
        }
    }

    fun supports(permissionName: String): Boolean = mUserActions.containsKey(permissionName)

    override fun getId(): String = "special-access"

    override fun supports(context: PermissionContext): Boolean = supports(context.permission.getName())

    override fun getState(context: PermissionContext): PermissionControllerState {
        return PermissionControllerState(getId(), getState(context.permission), false, getUserAction(context.permission.getName()))
    }

    override fun setGranted(context: PermissionContext, granted: Boolean): PermissionChangeResult {
        return requestChange(context.permission.getName(), granted)
    }

    fun getState(permission: Permission): PermissionState {
        if (!supports(permission.getName())) return PermissionState.UNSUPPORTED
        if (permission.affectsAppOp()) {
            return if (permission.isGrantedIncludingAppOp()) PermissionState.GRANTED else PermissionState.DENIED
        }
        // TODO: 9/2/26 Handle bound permissions
        return PermissionState.UNKNOWN
    }

    fun getUserAction(permissionName: String): PermissionUserAction? = mUserActions[permissionName]

    fun requestChange(permissionName: String, granted: Boolean): PermissionChangeResult {
        val action = getUserAction(permissionName)
            ?: return PermissionChangeResult.unsupported("Unsupported special permission $permissionName")
        return PermissionChangeResult.userActionRequired(
            "${if (granted) "Grant" else "Revoke"} requires system Settings", action)
    }
}
