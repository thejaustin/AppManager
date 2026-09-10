// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride

@Dao
interface PermissionOverrideDao {
    @Query("SELECT * FROM permission_override WHERE package_name = :packageName AND user_id = :userId AND permission_name = :permissionName LIMIT 1")
    fun get(packageName: String, userId: Int, permissionName: String): PermissionOverride?

    @Query("SELECT * FROM permission_override WHERE package_name = :packageName AND user_id = :userId")
    fun getForPackage(packageName: String, userId: Int): List<PermissionOverride>

    @Query("SELECT * FROM permission_override")
    fun getAll(): List<PermissionOverride>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(override: PermissionOverride)

    @Query("DELETE FROM permission_override WHERE package_name = :packageName AND user_id = :userId AND permission_name = :permissionName")
    fun delete(packageName: String, userId: Int, permissionName: String)

    @Query("DELETE FROM permission_override WHERE package_name = :packageName AND user_id = :userId")
    fun deleteForPackage(packageName: String, userId: Int)
}
