// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

enum class PermissionState {
    GRANTED, DENIED, UNKNOWN, UNSUPPORTED;

    fun isGranted(): Boolean = this == GRANTED
}
