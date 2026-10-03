// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.app.ActivityManager
import android.content.pm.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.RemoteException
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.compat.*
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.debloat.DebloatObject
import io.github.muntashirakon.AppManager.filters.options.ComponentsOption
import io.github.muntashirakon.AppManager.filters.options.FreezeOption
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings
import io.github.muntashirakon.AppManager.types.PackageSizeInfo
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo
import io.github.muntashirakon.AppManager.utils.*
import java.io.IOException
import java.security.cert.CertificateEncodingException
import java.util.*

class FilterableAppInfo(val packageInfo: PackageInfo, private val mPackageUsageInfo: PackageUsageInfo?) : IFilterableAppInfo {
    val applicationInfo: ApplicationInfo = packageInfo.applicationInfo!!
    private val mUserId: Int = UserHandleHidden.getUserId(applicationInfo.uid)
    private val mPm: PackageManager = ContextUtils.getContext().packageManager

    private var mAppLabel: String? = null
    private var mSsaid: String? = null
    private var mInstallerInfo: InstallSourceInfoCompat? = null
    private var mSignerInfo: SignerInfo? = null
    private var mSignatureSubjectLines: Array<String>? = null
    private var mSignatureSha256Checksums: Array<String>? = null
    private var mAllComponents: Map<ComponentInfo, Int>? = null
    private var mTrackerComponents: Map<ComponentInfo, Int>? = null
    private var mUsedPermissions: List<String>? = null
    private var mBackups: Array<Backup>? = null
    private var mAppOpEntries: List<AppOpsManagerCompat.OpEntry>? = null
    private var mPackageSizeInfo: PackageSizeInfo? = null
    private var mDataUsage: AppUsageStatsManager.DataUsage? = null
    private var mBloatwareInfo: DebloatObject? = null
    private var mFreezeFlags: Int? = null
    private var mUsesSensors: Boolean? = null
    private var mBatteryOptEnabled: Boolean? = null
    private var mHasKeystoreItems: Boolean? = null
    private var mRulesCount: Int? = null

    override fun getPackageName(): String = packageInfo.packageName

    override fun getUid(): Int = applicationInfo.uid

    override fun getUserId(): Int = mUserId

    override fun getAppLabel(): String {
        if (mAppLabel == null) {
            mAppLabel = applicationInfo.loadLabel(mPm).toString()
        }
        return mAppLabel!!
    }

    override fun getAppIcon(): Drawable = applicationInfo.loadIcon(mPm)

    override fun getVersionName(): String? = packageInfo.versionName

    override fun getVersionCode(): Long = PackageInfoCompat.getLongVersionCode(packageInfo)

    override fun getFirstInstallTime(): Long = packageInfo.firstInstallTime

    override fun getLastUpdateTime(): Long = packageInfo.lastUpdateTime

    override fun getTargetSdk(): Int = applicationInfo.targetSdkVersion

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getCompileSdk(): Int = applicationInfo.compileSdkVersion

    @RequiresApi(Build.VERSION_CODES.N)
    override fun getMinSdk(): Int = applicationInfo.minSdkVersion

    override fun getBackups(): Array<Backup> {
        if (mBackups == null) {
            mBackups = BackupUtils.getBackupMetadataFromDbNoLockValidate(packageName).toTypedArray()
        }
        return mBackups!!
    }

    override fun isRunning(): Boolean {
        return ActivityManagerCompat.getRunningAppProcesses().any { ArrayUtils.contains(it.pkgList, packageName) }
    }

    override fun getTrackerComponents(): Map<ComponentInfo, Int> {
        if (mTrackerComponents == null) {
            val all = allComponents
            val trackers = LinkedHashMap<ComponentInfo, Int>()
            for ((item, type) in all) {
                if (ComponentUtils.isTracker(item.name)) trackers[item] = type
            }
            mTrackerComponents = trackers
        }
        return mTrackerComponents!!
    }

    override fun getAppOps(): List<AppOpsManagerCompat.OpEntry> {
        if (mAppOpEntries == null && isInstalled) {
            val packageOps = ExUtils.exceptionAsNull { AppOpsManagerCompat().getOpsForPackage(applicationInfo.uid, packageName, null) }
            if (packageOps != null && packageOps.size == 1) {
                mAppOpEntries = packageOps[0].ops
            }
        }
        return mAppOpEntries ?: emptyList()
    }

    override fun getAllComponents(): Map<ComponentInfo, Int> {
        if (mAllComponents == null) {
            val components = LinkedHashMap<ComponentInfo, Int>()
            packageInfo.activities?.forEach { components[it] = ComponentsOption.COMPONENT_TYPE_ACTIVITY }
            packageInfo.services?.forEach { components[it] = ComponentsOption.COMPONENT_TYPE_SERVICE }
            packageInfo.receivers?.forEach { components[it] = ComponentsOption.COMPONENT_TYPE_RECEIVER }
            packageInfo.providers?.forEach { components[it] = ComponentsOption.COMPONENT_TYPE_PROVIDER }
            mAllComponents = components
        }
        return mAllComponents!!
    }

    override fun getAllPermissions(): List<String> {
        if (mUsedPermissions == null) {
            val used = HashSet<String>()
            packageInfo.requestedPermissions?.let { Collections.addAll(used, *it) }
            packageInfo.permissions?.forEach { used.add(it.name) }
            packageInfo.activities?.forEach { it.permission?.let { p -> used.add(p) } }
            packageInfo.services?.forEach { it.permission?.let { p -> used.add(p) } }
            packageInfo.receivers?.forEach { it.permission?.let { p -> used.add(p) } }
            mUsedPermissions = ArrayList(used)
        }
        return mUsedPermissions!!
    }

    override fun getAllRequestedFeatures(): Array<FeatureInfo> {
        return ArrayUtils.defeatNullable(FeatureInfo::class.java, packageInfo.reqFeatures)
    }

    override fun isInstalled(): Boolean = ApplicationInfoCompat.isInstalled(applicationInfo)

    override fun isFrozen(): Boolean = !isEnabled || isSuspended || isHidden

    override fun getFreezeFlags(): Int {
        if (mFreezeFlags != null) return mFreezeFlags!!
        var flags = 0
        if (!isEnabled) flags = flags or FreezeOption.FREEZE_TYPE_DISABLED
        if (isHidden) flags = flags or FreezeOption.FREEZE_TYPE_HIDDEN
        if (isSuspended) flags = flags or FreezeOption.FREEZE_TYPE_SUSPENDED
        mFreezeFlags = flags
        return flags
    }

    override fun isStopped(): Boolean = ApplicationInfoCompat.isStopped(applicationInfo)

    override fun isTestOnly(): Boolean = ApplicationInfoCompat.isTestOnly(applicationInfo)

    override fun isDebuggable(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun isSystemApp(): Boolean = ApplicationInfoCompat.isSystemApp(applicationInfo)

    override fun hasCode(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_HAS_CODE) != 0

    override fun isPersistent(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_PERSISTENT) != 0

    override fun isUpdatedSystemApp(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    override fun backupAllowed(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0

    override fun installedInExternalStorage(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0

    override fun requestedLargeHeap(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0

    override fun supportsRTL(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL) != 0

    override fun dataOnlyApp(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_IS_DATA_ONLY) != 0

    @get:RequiresApi(Build.VERSION_CODES.M)
    override val usesHttp: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0

    override fun isPrivileged(): Boolean = ApplicationInfoCompat.isPrivileged(applicationInfo)

    @RequiresApi(Build.VERSION_CODES.P)
    override fun usesSensors(): Boolean {
        if (!isInstalled) return false
        if (mUsesSensors == null) {
            mUsesSensors = if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)) {
                SensorServiceCompat.isSensorEnabled(packageName, mUserId)
            } else true
        }
        return mUsesSensors!!
    }

    override fun isBatteryOptEnabled(): Boolean {
        if (!isInstalled) return true
        if (mBatteryOptEnabled == null) {
            mBatteryOptEnabled = DeviceIdleManagerCompat.isBatteryOptimizedApp(packageName)
        }
        return mBatteryOptEnabled!!
    }

    override fun hasKeyStoreItems(): Boolean {
        if (!isInstalled) return false
        if (mHasKeystoreItems == null) {
            mHasKeystoreItems = KeyStoreUtils.hasKeyStore(applicationInfo.uid)
        }
        return mHasKeystoreItems!!
    }

    override fun getRuleCount(): Int {
        if (mRulesCount == null) {
            ComponentsBlocker.getInstance(packageName, mUserId, false).use { cb ->
                mRulesCount = cb.entryCount()
            }
        }
        return mRulesCount!!
    }

    override fun getSsaid(): String {
        if (mSsaid == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                mSsaid = SsaidSettings(mUserId).getSsaid(packageName, applicationInfo.uid)
            } catch (ignore: IOException) {}
        }
        return mSsaid ?: ""
    }

    override fun hasSplits(): Boolean = !packageInfo.splitNames.isNullOrEmpty()

    override fun hasDomainUrls(): Boolean = ApplicationInfoCompat.hasDomainUrls(applicationInfo)

    override fun hasStaticSharedLibrary(): Boolean = ApplicationInfoCompat.isStaticSharedLibrary(applicationInfo)

    override fun isHidden(): Boolean = ApplicationInfoCompat.isHidden(applicationInfo)

    override fun isSuspended(): Boolean = ApplicationInfoCompat.isSuspended(applicationInfo)

    override fun isEnabled(): Boolean = applicationInfo.enabled

    override fun getSharedUserId(): String? = packageInfo.sharedUserId

    private fun fetchPackageSizeInfo() {
        if (mPackageSizeInfo == null && isInstalled) {
            mPackageSizeInfo = PackageUtils.getPackageSizeInfo(ContextUtils.getContext(), packageName, mUserId, null)
        }
    }

    override fun getTotalSize(): Long {
        fetchPackageSizeInfo()
        return mPackageSizeInfo?.totalSize ?: 0L
    }

    override fun getApkSize(): Long {
        fetchPackageSizeInfo()
        return mPackageSizeInfo?.let { it.codeSize + it.obbSize } ?: 0L
    }

    override fun getCacheSize(): Long {
        fetchPackageSizeInfo()
        return mPackageSizeInfo?.cacheSize ?: 0L
    }

    override fun getDataSize(): Long {
        fetchPackageSizeInfo()
        return mPackageSizeInfo?.let { it.dataSize + it.mediaSize + it.cacheSize } ?: 0L
    }

    override fun getDataUsage(): AppUsageStatsManager.DataUsage {
        if (mDataUsage == null && isInstalled) {
            if (mPackageUsageInfo != null) {
                mDataUsage = AppUsageStatsManager.DataUsage.fromDataUsage(mPackageUsageInfo.mobileData, mPackageUsageInfo.wifiData)
            }
        }
        return mDataUsage ?: AppUsageStatsManager.DataUsage.EMPTY
    }

    override fun getTimesOpened(): Int = mPackageUsageInfo?.timesOpened ?: 0

    override fun getTotalScreenTime(): Long = mPackageUsageInfo?.screenTime ?: 0L

    override fun getLastUsedTime(): Long = mPackageUsageInfo?.lastUsageTime ?: 0L

    override fun fetchSignerInfo(): SignerInfo? {
        if (mSignerInfo == null) {
            mSignerInfo = PackageUtils.getSignerInfo(packageInfo, !isInstalled)
        }
        return mSignerInfo
    }

    override fun getSignatureSubjectLines(): Array<String> {
        fetchSignerInfo()
        if (mSignerInfo != null && mSignatureSubjectLines == null) {
            val signatures = mSignerInfo!!.allSignerCerts
            if (signatures != null) {
                mSignatureSubjectLines = Array(signatures.size) { i -> signatures[i].subjectX500Principal.name }
            }
        }
        return mSignatureSubjectLines ?: emptyArray()
    }

    override fun getSignatureSha256Checksums(): Array<String> {
        fetchSignerInfo()
        if (mSignerInfo != null && mSignatureSha256Checksums == null) {
            val signatures = mSignerInfo!!.allSignerCerts
            if (signatures != null) {
                mSignatureSha256Checksums = Array(signatures.size) { i ->
                    try {
                        DigestUtils.getHexDigest(DigestUtils.SHA_256, signatures[i].encoded)
                    } catch (e: CertificateEncodingException) { "" }
                }
            }
        }
        return mSignatureSha256Checksums ?: emptyArray()
    }

    override fun getInstallerInfo(): InstallSourceInfoCompat? {
        if (mInstallerInfo == null && isInstalled) {
            try {
                mInstallerInfo = PackageManagerCompat.getInstallSourceInfo(packageName, mUserId)
            } catch (ignore: RemoteException) {}
        }
        return mInstallerInfo
    }

    override fun getBloatwareInfo(): DebloatObject? {
        if (mBloatwareInfo == null) {
            mBloatwareInfo = StaticDataset.getDebloatObjects().find { it.packageName == packageName }
        }
        return mBloatwareInfo
    }
}
