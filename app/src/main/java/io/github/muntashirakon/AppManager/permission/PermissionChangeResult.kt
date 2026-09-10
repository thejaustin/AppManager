// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

class PermissionChangeResult private constructor(
    val status: Status,
    val message: String?,
    val cause: Throwable?,
    val userAction: PermissionUserAction?
) {
    enum class Status {
        SUCCESS,
        UNSUPPORTED,
        FAILURE,
        USER_ACTION_REQUIRED
    }

    fun isSuccessful(): Boolean = status == Status.SUCCESS

    companion object {
        fun success(): PermissionChangeResult =
            PermissionChangeResult(Status.SUCCESS, null, null, null)

        fun unsupported(message: String): PermissionChangeResult =
            PermissionChangeResult(Status.UNSUPPORTED, message, null, null)

        fun failure(message: String?, cause: Throwable?): PermissionChangeResult =
            PermissionChangeResult(Status.FAILURE, message, cause, null)

        fun userActionRequired(message: String, userAction: PermissionUserAction): PermissionChangeResult =
            PermissionChangeResult(Status.USER_ACTION_REQUIRED, message, null, userAction)
    }
}
