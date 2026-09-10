// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

class PermissionControllerState(
    val controllerId: String,
    val state: PermissionState,
    val modifiable: Boolean,
    val userAction: PermissionUserAction?
)
