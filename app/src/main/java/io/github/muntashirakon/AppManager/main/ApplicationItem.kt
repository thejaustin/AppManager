// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcelable
import android.os.RemoteException
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.SensorServiceCompat
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.filters.options.ComponentsOption
import io.github.muntashirakon.AppManager.filters.options.FreezeOption
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.misc.AppUsageStatsManager
import io.github.muntashirakon.AppManager.misc.PackageSizeInfo
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo
import io.github.muntashirakon.AppManager.rules.DebloatObject
import io.github.muntashirakon.AppManager.rules.compontents.ComponentInfo
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.io.Path
import java.io.InputStream
import java.io.Serializable
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ApplicationItem : IFilterableAppInfo, Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    // Default attributes
    var label: String = ""
    var packageName: String = ""
    var versionName: String? = null
    var versionCode: Long = 0
    var targetSdk: Int = 0
    var firstInstallTime: Long = 0
    var lastUpdateTime: Long = 0
    var uid: Int = 0
    var flags: Int = 0
    var isEnabled: Boolean = false // (flags & ApplicationInfo.FLAG_STOPPED) != 0
    var isInstalled: Boolean = true // (flags & ApplicationInfo.FLAG_STOPPED) != 0
    var userIds: IntArray = intArrayOf()
    var sha: Pair<String, String>? = null // Issuer short name and SHA-256
    var ssaid: String? = null // SSAID
    var hasSplits: Boolean = false
    var usesSaf: Boolean = false

    // Dynamic attributes, filled by generateOtherInfo
    var isStopped: Boolean = false
    var isSystem: Boolean = false
    var isPersistent: Boolean = false
    var usesCleartextTraffic: Boolean = false
    var allowClearingUserData: Boolean = false
    var uidOrAppIds: String = ""\nvar issuerShortName: String? = null
    var versionTag: String? = null
    var appTypePostfix: String = ""\nvar sdkString: String? = null
    var diffInstallUpdateInDays: Long = 0
    var backup: BackupMetadataV5.Metadata? = null
    var lastBackupDays: Long = 0
    var backupFlagsStr: StringBuilder? = null
    var isDisabled: Boolean = false // isFrozen

    // Lazy attributes, filled by getters
    @Transient
    private var mPackageInfo: PackageInfo? = null
    @Transient
    private var mApplicationInfo: ApplicationInfo? = null
    @Transient
    private var mSignerInfo: SignerInfo? = null
    @Transient
    private var mUsedPermissions: List<String>? = null
    @Transient
    private var mAllComponents: Map<ComponentInfo, Int>? = null
    @Transient
    private var mTrackerComponents: Map<ComponentInfo, Int>? = null
    @Transient
    private var mAppOpEntries: List<AppOpsManagerCompat.OpEntry>? = null
    @Transient
    private var mPackageUsageInfo: UsageStatsManagerCompat.PackageUsageInfo? = null
    @Transient
    private var mPackageSizeInfo: PackageSizeInfo? = null
    @Transient
    private var mDataUsage: AppUsageStatsManager.DataUsage? = null
    @Transient
    private var mBloatwareInfo: DebloatObject? = null
    @Transient
    private var mFreezeFlags: Int? = null
    @Transient
    private var mUserId: Int? = null
    @Transient
    private var mUsesSensors: Boolean? = null
    @Transient
    private var mBatteryOptEnabled: Boolean? = null
    @Transient
    private var mHasKeystoreItems: Boolean? = null
    @Transient
    private var mRulesCount: Int? = null
    @Transient
    private var mIsRunning: Boolean = false

    // OPTIMIZATION: Fields for search performance
    @Transient
    var packageNameLowerCase: String = ""\n@Transient
    var labelLowerCase: String = ""\nfun generateOtherInfo() {
        isStopped = (flags and ApplicationInfo.FLAG_STOPPED) != 0
        isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        isPersistent = (flags and ApplicationInfo.FLAG_PERSISTENT) != 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            usesCleartextTraffic = (flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0
        }
        allowClearingUserData = (flags and ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) != 0
        uidOrAppIds = if (userIds.size > 1) {
            val appId = android.os.UserHandle.getAppId(uid)
            "${userIds.size}+$appId"\n} else if (userIds.size == 1) {
            uid.toString()
        } else {
            ""\n}
        if (sha != null) {
            try {
                issuerShortName = "CN=" + (sha!!.first).split("CN=", 2)[1]
            } catch (e: ArrayIndexOutOfBoundsException) {
                issuerShortName = sha!!.first
            }
            if (TextUtils.isEmpty(sha!!.second)) {
                sha = null
            }
        }
        versionTag = versionName
        if (isInstalled && (flags and ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0) versionTag = "_$versionTag"\nif ((flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) versionTag = "debug$versionTag"\nif ((flags and ApplicationInfo.FLAG_TEST_ONLY) != 0) versionTag = "~$versionTag"\nappTypePostfix = ""\nif ((flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0) appTypePostfix += "#"\nif ((flags and ApplicationInfo.FLAG_SUSPENDED) != 0) appTypePostfix += "°"\nif ((flags and ApplicationInfo.FLAG_MULTIARCH) != 0) appTypePostfix += "X"\nif ((flags and ApplicationInfo.FLAG_HAS_CODE) == 0) appTypePostfix += "0"\nif ((flags and ApplicationInfo.FLAG_VM_SAFE_MODE) != 0) appTypePostfix += "?"\nsdkString = if (targetSdk > 0) "SDK $targetSdk" else null
        diffInstallUpdateInDays = TimeUnit.DAYS.convert(lastUpdateTime - firstInstallTime, TimeUnit.MILLISECONDS)
        if (backup != null) {
            lastBackupDays = TimeUnit.DAYS.convert(System.currentTimeMillis() - backup!!.backupTime, TimeUnit.MILLISECONDS)
            backupFlagsStr = StringBuilder()
            if (backup!!.flags.backupApkFiles()) backupFlagsStr!!.append("apk")
            if (backup!!.flags.backupData()) {
                if (backupFlagsStr!!.isNotEmpty()) backupFlagsStr!!.append("+")
                backupFlagsStr!!.append("data")
            }
            if (backup!!.hasRules) {
                if (backupFlagsStr!!.isNotEmpty()) backupFlagsStr!!.append("+")
                backupFlagsStr!!.append("rules")
            }
        }
    }

    @WorkerThread
    override fun loadIcon(pm: PackageManager): Drawable {
        fetchPackageInfo()
        if (mApplicationInfo != null) return mApplicationInfo!!.loadIcon(pm)
        if (backup != null) {
            try {
                val iconFile = backup!!.item.iconFile
                if (iconFile.exists()) {
                    iconFile.openInputStream().use { `is` ->
                        Drawable.createFromStream(`is`, name)?.let { return it }
                    }
                }
            } catch (ignore: Throwable) {
            }
        }
        return pm.defaultActivityIcon
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApplicationItem) return false
        return packageName == other.packageName
    }

    override fun hashCode(): Int = Objects.hash(packageName)

    override fun getPackageName(): String = packageName
    override fun getUid(): Int = uid

    override fun getUserId(): Int {
        if (mUserId != null) return mUserId!!
        if (userIds.isNotEmpty()) {
            val myUserId = android.os.UserHandle.myUserId()
            for (userId in userIds) {
                if (userId == myUserId) {
                    mUserId = myUserId
                    break
                }
            }
            if (mUserId == null) mUserId = userIds[0]
            return mUserId!!
        }
        return -1
    }

    fun setPackageUsageInfo(packageUsageInfo: UsageStatsManagerCompat.PackageUsageInfo?) {
        mPackageUsageInfo = packageUsageInfo
    }

    private fun fetchPackageInfo() {
        if (mPackageInfo != null) return
        val userId = getUserId()
        if (userId < 0) return
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(
                packageName, PackageManager.GET_META_DATA or PackageManagerCompat.GET_SIGNING_CERTIFICATES
                        or PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS
                        or PackageManager.GET_PROVIDERS or PackageManager.GET_SERVICES
                        or PackageManager.GET_CONFIGURATIONS or PackageManager.GET_PERMISSIONS
                        or PackageManager.GET_URI_PERMISSION_PATTERNS
                        or PackageManagerCompat.MATCH_DISABLED_COMPONENTS or PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                        or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId
            )
            mApplicationInfo = mPackageInfo!!.applicationInfo!!
            isStopped = (mApplicationInfo!!.flags and ApplicationInfo.FLAG_STOPPED) != 0
            isDisabled = FreezeUtils.isFrozen(mApplicationInfo!!)
        } catch (ignore: RemoteException) {
        } catch (ignore: PackageManager.NameNotFoundException) {
        }
    }

    override fun getAppLabel(): String = label
    override fun getAppIcon(): Drawable = loadIcon(ContextUtils.getContext().packageManager)
    override fun getVersionName(): String? = versionName
    override fun getVersionCode(): Long = versionCode
    override fun getFirstInstallTime(): Long = firstInstallTime
    override fun getLastUpdateTime(): Long = lastUpdateTime
    override fun getTargetSdk(): Int = targetSdk

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getCompileSdk(): Int {
        fetchPackageInfo()
        return mApplicationInfo?.compileSdkVersion ?: targetSdk
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun getMinSdk(): Int {
        fetchPackageInfo()
        return mApplicationInfo?.minSdkVersion ?: 0
    }

    override fun getBackups(): Array<Backup> {
        return BackupUtils.getBackupMetadataFromDbNoLockValidate(packageName).toTypedArray()
    }

    fun setRunning(running: Boolean) {
        mIsRunning = running
    }

    override fun isRunning(): Boolean = mIsRunning

    override fun getTrackerComponents(): Map<ComponentInfo, Int> {
        fetchPackageInfo()
        if (mTrackerComponents == null) {
            val allComponents = allComponents
            val trackerComponents = LinkedHashMap<ComponentInfo, Int>()
            for ((itemInfo, type) in allComponents) {
                if (ComponentUtils.isTracker(itemInfo.name)) {
                    trackerComponents[itemInfo] = type
                }
            }
            mTrackerComponents = trackerComponents
        }
        return mTrackerComponents!!
    }

    override fun getAppOps(): List<AppOpsManagerCompat.OpEntry> {
        fetchPackageInfo()
        if (mApplicationInfo != null && mAppOpEntries == null && isInstalled()) {
            val packageOps = ExUtils.exceptionAsNull {
                AppOpsManagerCompat().getOpsForPackage(
                    mApplicationInfo!!.uid, packageName, null
                )
            }
            if (packageOps != null && packageOps.size == 1) {
                mAppOpEntries = packageOps[0].ops
            }
        } else mAppOpEntries = Collections.emptyList()
        return mAppOpEntries!!
    }

    override fun getAllComponents(): Map<ComponentInfo, Int> {
        fetchPackageInfo()
        if (mPackageInfo != null && mAllComponents == null) {
            val components = LinkedHashMap<ComponentInfo, Int>()
            mPackageInfo!!.activities?.forEach { components[it] = ComponentsOption.COMPONENT_TYPE_ACTIVITY }
            mPackageInfo!!.services?.forEach { components[it] = ComponentsOption.COMPONENT_TYPE_SERVICE }
            mPackageInfo!!.receivers?.forEach { components[it] = ComponentsOption.COMPONENT_TYPE_RECEIVER }
            mPackageInfo!!.providers?.forEach { components[it] = ComponentsOption.COMPONENT_TYPE_PROVIDER }
            mAllComponents = components
        }
        return mAllComponents!!
    }

    override fun getAllPermissions(): List<String> {
        fetchPackageInfo()
        if (mPackageInfo != null && mUsedPermissions == null) {
            val usedPermissions = HashSet<String>()
            mPackageInfo!!.requestedPermissions?.let { Collections.addAll(usedPermissions, *it) }
            mPackageInfo!!.permissions?.forEach { usedPermissions.add(it.name) }
            mPackageInfo!!.activities?.forEach { it.permission?.let { p -> usedPermissions.add(p) } }
            mPackageInfo!!.services?.forEach { it.permission?.let { p -> usedPermissions.add(p) } }
            mPackageInfo!!.receivers?.forEach { it.permission?.let { p -> usedPermissions.add(p) } }
            mUsedPermissions = ArrayList(usedPermissions)
        }
        return mUsedPermissions!!
    }

    override fun getAllRequestedFeatures(): Array<FeatureInfo> {
        fetchPackageInfo()
        return mPackageInfo?.reqFeatures?.let { ArrayUtils.defeatNullable(FeatureInfo::class.java, it) } ?: emptyArray()
    }

    override fun isInstalled(): Boolean = isInstalled
    override fun isFrozen(): Boolean = !isEnabled || isSuspended() || isHidden()

    override fun getFreezeFlags(): Int {
        if (mFreezeFlags != null) return mFreezeFlags!!
        mFreezeFlags = 0
        if (!isEnabled) mFreezeFlags = mFreezeFlags!! or FreezeOption.FREEZE_TYPE_DISABLED
        if (isHidden) mFreezeFlags = mFreezeFlags!! or FreezeOption.FREEZE_TYPE_HIDDEN
        if (isSuspended()) mFreezeFlags = mFreezeFlags!! or FreezeOption.FREEZE_TYPE_SUSPENDED
        return mFreezeFlags!!
    }

    override fun isStopped(): Boolean = isStopped
    override fun isTestOnly(): Boolean = (flags and ApplicationInfo.FLAG_TEST_ONLY) != 0
    override fun isDebuggable(): Boolean = debuggable
    override fun isSystemApp(): Boolean = isSystem
    override fun hasCode(): Boolean = (flags and ApplicationInfo.FLAG_HAS_CODE) != 0
    override fun isPersistent(): Boolean = isPersistent
    override fun isUpdatedSystemApp(): Boolean = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    override fun backupAllowed(): Boolean = (flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
    override fun installedInExternalStorage(): Boolean = (flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0
    override fun requestedLargeHeap(): Boolean = (flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0
    override fun supportsRTL(): Boolean = (flags and ApplicationInfo.FLAG_SUPPORTS_RTL) != 0
    override fun dataOnlyApp(): Boolean = (flags and ApplicationInfo.FLAG_IS_DATA_ONLY) != 0
    override fun usesHttp(): Boolean = usesCleartextTraffic
    override fun isPrivileged(): Boolean {
        fetchPackageInfo()
        return mApplicationInfo?.let { ApplicationInfoCompat.isPrivileged(it) } ?: false
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun usesSensors(): Boolean {
        if (mUsesSensors == null) {
            mUsesSensors = if (isInstalled() && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)) {
                SensorServiceCompat.isSensorEnabled(packageName, userId)
            } else isInstalled()
        }
        return mUsesSensors!!
    }

    override fun isBatteryOptEnabled(): Boolean {
        if (mBatteryOptEnabled == null) {
            mBatteryOptEnabled = if (isInstalled()) DeviceIdleManagerCompat.isBatteryOptimizedApp(packageName) else true
        }
        return mBatteryOptEnabled!!
    }

    override fun hasKeyStoreItems(): Boolean {
        fetchPackageInfo()
        if (mHasKeystoreItems == null) {
            mHasKeystoreItems = if (mApplicationInfo != null && isInstalled()) KeyStoreUtils.hasKeyStore(mApplicationInfo!!.uid) else false
        }
        return mHasKeystoreItems!!
    }

    override fun getRuleCount(): Int {
        if (mRulesCount == null) {
            mRulesCount = 0
            for (userId in userIds) {
                ComponentsBlocker.getInstance(packageName, userId, false).use { cb ->
                    mRulesCount = mRulesCount!! + cb.entryCount()
                }
            }
        }
        return mRulesCount!!
    }

    override fun getSsaid(): String? = ssaid

    override fun hasSplits(): Boolean = hasSplits

    override fun hasDomainUrls(): Boolean {
        fetchPackageInfo()
        return mApplicationInfo?.let { ApplicationInfoCompat.hasDomainUrls(it) } ?: false
    }

    override fun hasStaticSharedLibrary(): Boolean {
        fetchPackageInfo()
        return mApplicationInfo?.let { ApplicationInfoCompat.isStaticSharedLibrary(it) } ?: false
    }

    override fun isHidden(): Boolean {
        fetchPackageInfo()
        return mApplicationInfo?.let { ApplicationInfoCompat.isHidden(it) } ?: false
    }

    override fun isSuspended(): Boolean {
        fetchPackageInfo()
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (mApplicationInfo?.let { ApplicationInfoCompat.isSuspended(it) } ?: false)
    }

    override fun isEnabled(): Boolean {
        fetchPackageInfo()
        return mApplicationInfo?.enabled ?: !isDisabled
    }

    override fun getSharedUserId(): String? {
        fetchPackageInfo()
        return mPackageInfo?.sharedUserId ?: sharedUserId
    }

    private fun fetchPackageSizeInfo() {
        val userId = getUserId()
        if (userId >= 0 && mPackageSizeInfo == null && isInstalled()) {
            mPackageSizeInfo = PackageUtils.getPackageSizeInfo(ContextUtils.getContext(), packageName, userId, null)
        }
    }

    override fun getTotalSize(): Long {
        fetchPackageSizeInfo()
        return mPackageSizeInfo?.totalSize ?: 0
    }

    override fun getApkSize(): Long {
        fetchPackageSizeInfo()
        return mPackageSizeInfo?.let { it.codeSize + it.obbSize } ?: 0
    }

    override fun getCacheSize(): Long {
        fetchPackageSizeInfo()
        return mPackageSizeInfo?.cacheSize ?: 0
    }

    override fun getDataSize(): Long {
        fetchPackageSizeInfo()
        return mPackageSizeInfo?.let { it.dataSize + it.mediaSize + it.cacheSize } ?: 0
    }

    override fun getDataUsage(): AppUsageStatsManager.DataUsage {
        if (mDataUsage == null && isInstalled()) {
            if (mPackageUsageInfo != null) {
                mDataUsage = AppUsageStatsManager.DataUsage.fromDataUsage(mPackageUsageInfo!!.mobileData, mPackageUsageInfo!!.wifiData)
            }
        }
        if (mDataUsage == null) {
            mDataUsage = AppUsageStatsManager.DataUsage.EMPTY
        }
        return mDataUsage!!
    }

    override fun getTimesOpened(): Int {
        fetchPackageInfo()
        return mPackageUsageInfo?.timesOpened ?: openCount
    }

    override fun getTotalScreenTime(): Long {
        fetchPackageInfo()
        return mPackageUsageInfo?.screenTime ?: screenTime
    }

    override fun getLastUsedTime(): Long {
        fetchPackageInfo()
        return mPackageUsageInfo?.lastUsageTime ?: lastUsageTime
    }

    override fun fetchSignerInfo(): SignerInfo? {
        fetchPackageInfo()
        if (mPackageInfo != null && mSignerInfo == null) {
            mSignerInfo = PackageUtils.getSignerInfo(mPackageInfo!!, !isInstalled())
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
                    } catch (e: CertificateEncodingException) {
                        ""
                    }
                }
            }
        }
        return mSignatureSha256Checksums ?: emptyArray()
    }

    override fun getInstallerInfo(): InstallSourceInfoCompat? {
        val userId = getUserId()
        if (userId >= 0 && mInstallerInfo == null && isInstalled()) {
            try {
                mInstallerInfo = PackageManagerCompat.getInstallSourceInfo(packageName, userId)
            } catch (ignore: RemoteException) {
            }
        }
        return mInstallerInfo
    }

    override fun getBloatwareInfo(): DebloatObject? {
        if (mBloatwareInfo == null) {
            for (debloatObject in StaticDataset.getDebloatObjects()) {
                if (packageName == debloatObject.packageName) {
                    mBloatwareInfo = debloatObject
                    break
                }
            }
        }
        return mBloatwareInfo
    }

    // OPTIMIZATION: Ensure lowercase fields are populated for search performance
    fun ensureLowerCaseFields() {
        if (packageNameLowerCase.isEmpty() && packageName.isNotEmpty()) {
            packageNameLowerCase = packageName.lowercase(Locale.ROOT)
        }
        if (labelLowerCase.isEmpty() && label.isNotEmpty()) {
            labelLowerCase = label.lowercase(Locale.ROOT)
        }
    }
}
