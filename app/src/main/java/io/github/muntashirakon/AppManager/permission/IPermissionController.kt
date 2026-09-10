// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import androidx.annotation.WorkerThread

interface IPermissionController {
    fun getId(): String

    fun supports(context: PermissionContext): Boolean

    fun getState(context: PermissionContext): PermissionControllerState

    @WorkerThread
    fun setGranted(context: PermissionContext, granted: Boolean): PermissionChangeResult
}
