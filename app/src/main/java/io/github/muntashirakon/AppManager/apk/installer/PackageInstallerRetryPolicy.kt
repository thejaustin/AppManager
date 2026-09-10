// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.content.pm.PackageInstaller

/**
 * Retry policy for vendor package-installer behavior.
 *
 * MIUI 12.5+ may require more than one attempt for an unprivileged APK installation.
 * HyperOS 2.0+ may require the installer of a system app to be another system app,
 * so the retry uses com.android.shell. These workarounds apply only to APK installation,
 * not uninstallation or install-existing operations.
 */
internal object PackageInstallerRetryPolicy {
    // MIUI-begin: Multiple attempts may be required for a successful installation.
    const val MAX_MIUI_ATTEMPTS = 4
    // MIUI-end
    // HyperOS-begin: Retry system-app installation using another system app.
    const val MAX_HYPER_OS_ATTEMPTS = 3
    // HyperOS-end

    enum class Action { FINISH, RETRY, RETRY_WITH_SHELL_INSTALLER }

    // MIUI-begin
    private const val MIUI_PERMISSION_DENIED = "INSTALL_FAILED_ABORTED: Permission denied"
    // MIUI-end
    // HyperOS-begin
    private const val HYPER_OS_ISOLATION_VIOLATION = "INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION: "
    // HyperOS-end

    fun getAction(
        status: Int,
        statusMessage: String?,
        privileged: Boolean,
        affectedMiuiVersion: Boolean,
        attemptNumber: Int
    ): Action {
        if (status != PackageInstaller.STATUS_FAILURE_ABORTED) return Action.FINISH
        // MIUI-begin: MIUI 12.5 and 20.2.0 may require repeated unprivileged attempts.
        if (!privileged && affectedMiuiVersion && statusMessage == MIUI_PERMISSION_DENIED && attemptNumber < MAX_MIUI_ATTEMPTS) {
            return Action.RETRY
        }
        // MIUI-end
        // HyperOS-begin: the installer for a system app must itself be a system app.
        if (privileged && statusMessage != null && statusMessage.startsWith(HYPER_OS_ISOLATION_VIOLATION) && attemptNumber < MAX_HYPER_OS_ATTEMPTS) {
            return Action.RETRY_WITH_SHELL_INSTALLER
        }
        // HyperOS-end
        return Action.FINISH
    }
}
