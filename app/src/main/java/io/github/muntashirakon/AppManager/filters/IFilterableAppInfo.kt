// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.content.pm.ComponentInfo
import android.content.pm.FeatureInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.debloat.DebloatObject
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager

interface IFilterableAppInfo {
    fun getPackageName(): String
    fun getUserId(): Int
    fun getUid(): Int
    fun getAppLabel(): String
    fun getAppIcon(): Drawable
    fun getVersionName(): String?
    fun getVersionCode(): Long
    fun getFirstInstallTime(): Long
    fun getLastUpdateTime(): Long
    fun getTargetSdk(): Int

    @RequiresApi(Build.VERSION_CODES.S)
    fun getCompileSdk(): Int

    @RequiresApi(Build.VERSION_CODES.N)
    fun getMinSdk(): Int

    fun getBackups(): Array<Backup>
    fun isRunning(): Boolean
    fun getTrackerComponents(): Map<ComponentInfo, Int>
    fun getAppOps(): List<AppOpsManagerCompat.OpEntry>
    fun getAllComponents(): Map<ComponentInfo, Int>
    fun getAllPermissions(): List<String>
    fun getAllRequestedFeatures(): Array<FeatureInfo>
    fun isInstalled(): Boolean
    fun isFrozen(): Boolean
    fun getFreezeFlags(): Int
    fun isStopped(): Boolean
    fun isTestOnly(): Boolean
    fun isDebuggable(): Boolean
    fun isSystemApp(): Boolean
    fun hasCode(): Boolean
    fun isPersistent(): Boolean
    fun isUpdatedSystemApp(): Boolean
    fun backupAllowed(): Boolean
    fun installedInExternalStorage(): Boolean
    fun requestedLargeHeap(): Boolean
    fun supportsRTL(): Boolean
    fun dataOnlyApp(): Boolean

    @RequiresApi(Build.VERSION_CODES.M)
    fun usesHttp(): Boolean

    fun isPrivileged(): Boolean

    @RequiresApi(Build.VERSION_CODES.P)
    fun usesSensors(): Boolean

    @RequiresApi(Build.VERSION_CODES.M)
    fun isBatteryOptEnabled(): Boolean

    fun hasKeyStoreItems(): Boolean
    fun getRuleCount(): Int
    fun getSsaid(): String?
    fun hasDomainUrls(): Boolean
    fun hasStaticSharedLibrary(): Boolean
    fun isHidden(): Boolean
    fun isSuspended(): Boolean
    fun isEnabled(): Boolean
    fun getSharedUserId(): String?
    fun getTotalSize(): Long
    fun getApkSize(): Long
    fun getCacheSize(): Long
    fun getDataSize(): Long
    fun getDataUsage(): AppUsageStatsManager.DataUsage
    fun getTimesOpened(): Int
    fun getTotalScreenTime(): Long
    fun getLastUsedTime(): Long
    fun fetchSignerInfo(): SignerInfo?
    fun getSignatureSubjectLines(): Array<String>
    fun getSignatureSha256Checksums(): Array<String>
    fun getInstallerInfo(): InstallSourceInfoCompat?
    fun getBloatwareInfo(): DebloatObject?
}
