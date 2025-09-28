// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "archived_apps")
public class ArchivedApp {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName;

    @ColumnInfo(name = "app_name")
    public String appName;

    @ColumnInfo(name = "archive_timestamp")
    public long archiveTimestamp;

    @ColumnInfo(name = "apk_path")
    public String apkPath;

    public ArchivedApp(@NonNull String packageName, String appName, long archiveTimestamp, String apkPath) {
        this.packageName = packageName;
        this.appName = appName;
        this.archiveTimestamp = archiveTimestamp;
        this.apkPath = apkPath;
    }
}
