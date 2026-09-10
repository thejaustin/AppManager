// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * App Manager-owned permission overlays.
 */
@Entity(tableName = "permission_override", primaryKeys = ["package_name", "user_id", "permission_name"])
data class PermissionOverride(
    @ColumnInfo(name = "package_name") var packageName: String = "",
    @ColumnInfo(name = "user_id") var userId: Int = 0,
    @ColumnInfo(name = "permission_name") var permissionName: String = "",
    @ColumnInfo(name = "desired_granted") var desiredGranted: Boolean = false,
    @ColumnInfo(name = "controller") var controller: String = "",
    @ColumnInfo(name = "sync_status") var syncStatus: Int = 0,
    @ColumnInfo(name = "sync_time") var syncTime: Long = 0L
)
