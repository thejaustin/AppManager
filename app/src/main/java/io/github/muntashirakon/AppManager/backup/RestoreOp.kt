// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import android.app.AppOpsManager
import android.app.INotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.apk.ApkFile
import io.github.muntashirakon.AppManager.apk.installer.InstallerOptions
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat
import io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER
import io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.compat.*
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList
import io.github.muntashirakon.AppManager.magisk.MagiskHide
import io.github.muntashirakon.AppManager.permission.PermUtils
import io.github.muntashirakon.AppManager.permission.Permission
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.rules.PseudoRules
import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.rules.RulesImporter
import io.github.muntashirakon.AppManager.rules.struct.*
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings
import io.github.muntashirakon.AppManager.uri.UriManager
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.SplitInputStream
import io.github.muntashirakon.io.UidGidPair
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@WorkerThread
internal class RestoreOp @Throws(BackupException::class) constructor(
    private val mPackageName: String,
    private val mRequestedFlags: BackupFlags,
    private val mBackupItem: BackupItems.BackupItem,
    private val mUserId: Int
) : Closeable {
    private val mBackupFlags: BackupFlags
    private val mBackupInfo: BackupMetadataV5.Info
    private val mBackupMetadata: BackupMetadataV5.Metadata
    private var mPackageInfo: PackageInfo? = null
    private var mUid = 0
    private val mChecksum: BackupItems.Checksum
    private var mIsInstalled = false
    private var mRequiresRestart = false

    init {
        try {
            mBackupInfo = mBackupItem.info
            mBackupFlags = mBackupInfo.flags
        } catch (e: IOException) {
            mBackupItem.cleanup()
            throw BackupException("Could not read backup info. Possibly due to a malformed json file.", e)
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(mBackupInfo.crypto)) {
            mBackupItem.cleanup()
            throw BackupException("Mode ${mBackupInfo.crypto} is currently unavailable.")
        }
        try {
            mBackupItem.setCrypto(mBackupInfo.getCrypto())
        } catch (e: CryptoException) {
            mBackupItem.cleanup()
            throw BackupException("Could not get crypto ${mBackupInfo.crypto}", e)
        }
        try {
            mBackupMetadata = mBackupItem.getMetadata(mBackupInfo).metadata
        } catch (e: IOException) {
            mBackupItem.cleanup()
            throw BackupException("Could not read backup metadata. Possibly due to a malformed json file.", e)
        }
        // Get checksums
        try {
            mChecksum = mBackupItem.checksum
        } catch (e: Throwable) {
            mBackupItem.cleanup()
            throw BackupException("Failed to get checksums.", e)
        }
        // Verify metadata
        if (!mRequestedFlags.skipSignatureCheck()) {
            try {
                verifyMetadata()
            } catch (e: BackupException) {
                mBackupItem.cleanup()
                throw e
            }
        }
        // Check user handle
        if (mBackupInfo.userId != mUserId) {
            Log.w(TAG, "Using different user handle.")
        }
        // Get package info
        mPackageInfo = null
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(
                mPackageName, PackageManagerCompat.GET_SIGNING_CERTIFICATES
                        or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, mUserId
            )
            mUid = mPackageInfo!!.applicationInfo!!.uid
        } catch (ignore: Exception) {
        }
        mIsInstalled = mPackageInfo != null
    }

    override fun close() {
        Log.d(TAG, "Close called")
        mChecksum.close()
        mBackupItem.cleanup()
    }

    @Throws(BackupException::class)
    fun runRestore(progressHandler: ProgressHandler?) {
        try {
            if (mRequestedFlags.backupData() && mBackupMetadata.keyStore && !mRequestedFlags.skipSignatureCheck()) {
                // Check checksum of master key first
                checkMasterKey()
            }
            incrementProgress(progressHandler)
            if (mRequestedFlags.backupApkFiles()) {
                restoreApkFiles()
                incrementProgress(progressHandler)
            }
            if (mRequestedFlags.backupData()) {
                restoreData()
                if (mBackupMetadata.keyStore) {
                    restoreKeyStore()
                }
                incrementProgress(progressHandler)
            }
            if (mRequestedFlags.backupExtras()) {
                restoreExtras()
                incrementProgress(progressHandler)
            }
            if (mRequestedFlags.backupRules()) {
                restoreRules()
                incrementProgress(progressHandler)
            }
        } catch (e: BackupException) {
            throw e
        } catch (th: Throwable) {
            throw BackupException("Unknown error occurred", th)
        }
    }

    fun requiresRestart(): Boolean {
        return mRequiresRestart
    }

    @Throws(BackupException::class)
    private fun verifyMetadata() {
        val isV5AndUp = mBackupItem.isV5AndUp()
        if (isV5AndUp) {
            val infoFile: Path = try {
                mBackupItem.infoFile
            } catch (e: IOException) {
                throw BackupException("Could not get metadata file.", e)
            }
            val checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, infoFile)
            if (checksum != mChecksum[infoFile.getName()]) {
                throw BackupException(
                    "Couldn't verify metadata file." +
                            "\nFile: " + infoFile +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum[infoFile.getName()]
                )
            }
        }
        val metadataFile: Path = try {
            if (isV5AndUp) mBackupItem.getMetadataV5File(false) else mBackupItem.metadataV2File
        } catch (e: IOException) {
            throw BackupException("Could not get metadata file.", e)
        }
        val checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, metadataFile)
        if (checksum != mChecksum[metadataFile.getName()]) {
            throw BackupException(
                "Couldn't verify metadata file." +
                        "\nFile: " + metadataFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum[metadataFile.getName()]
            )
        }
    }

    @Throws(BackupException::class)
    private fun checkMasterKey() {
        if (true) {
            // TODO: 6/2/22 MasterKey may not actually be necessary.
            return
        }
        val oldChecksum = mChecksum[MASTER_KEY]
        val masterKey: Path = try {
            KeyStoreUtils.getMasterKey(mUserId)
        } catch (e: FileNotFoundException) {
            if (oldChecksum == null) return else throw BackupException("Master key existed when the checksum was made but now it doesn't.")
        }
        if (oldChecksum == null) {
            throw BackupException("Master key exists but it didn't exist when the backup was made.")
        }
        val newChecksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, masterKey.getContentAsString()!!.toByteArray())
        if (newChecksum != oldChecksum) {
            throw BackupException("Checksums for master key did not match.")
        }
    }

    @Throws(BackupException::class)
    private fun restoreApkFiles() {
        if (!mBackupFlags.backupApkFiles()) {
            throw BackupException("APK restore is requested but backup doesn't contain any source files.")
        }
        var backupSourceFiles = mBackupItem.sourceFiles
        if (backupSourceFiles.isEmpty()) {
            // No source backup found
            throw BackupException("Source restore is requested but there are no source files.")
        }
        var isVerified = true
        if (mPackageInfo != null) {
            // Check signature of the installed app
            val certChecksumList = listOf(*PackageUtils.getSigningCertChecksums(mBackupInfo.checksumAlgo, mPackageInfo!!, false))
            val certChecksums = BackupItems.Checksum.getCertChecksums(mChecksum)
            for (checksum in certChecksums) {
                if (certChecksumList.contains(checksum)) continue
                isVerified = false
                if (!mRequestedFlags.skipSignatureCheck()) {
                    throw BackupException(
                        "Signing info verification failed." +
                                "\nInstalled: " + certChecksumList +
                                "\nBackup: " + Arrays.toString(certChecksums)
                    )
                }
            }
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            var checksum: String
            for (file in backupSourceFiles) {
                checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file)
                if (checksum != mChecksum[file.getName()]) {
                    throw BackupException(
                        "Source file verification failed." +
                                "\nFile: " + file +
                                "\nFound: " + checksum +
                                "\nRequired: " + mChecksum[file.getName()]
                    )
                }
            }
        }
        if (!isVerified) {
            // Signature verification failed but still here because signature check is disabled.
            // The only way to restore is to reinstall the app
            synchronized(sLock) {
                val installer = PackageInstallerCompat.getNewInstance()
                if (installer.uninstall(mPackageName, mUserId, false)) {
                    throw BackupException("An uninstallation was necessary but couldn't perform it.")
                }
            }
        }
        // Setup package staging directory
        var packageStagingDirectory = Paths.get(PackageUtils.PACKAGE_STAGING_DIRECTORY)
        try {
            synchronized(sLock) {
                PackageUtils.ensurePackageStagingDirectoryPrivileged()
            }
        } catch (ignore: Exception) {
        }
        try {
            if (!packageStagingDirectory.canWrite()) {
                packageStagingDirectory = mBackupItem.getUnencryptedBackupPath()
            }
        } catch (e: IOException) {
            throw BackupException("Could not create package staging directory", e)
        }
        synchronized(sLock) {
            // Setup apk files, including split apk
            val splitCount = mBackupMetadata.splitConfigs!!.size
            val allApkNames = arrayOfNulls<String>(splitCount + 1)
            val allApks = arrayOfNulls<Path>(splitCount + 1)
            try {
                val baseApk = packageStagingDirectory.createNewFile(mBackupMetadata.apkName, null)
                allApks[0] = baseApk
                allApkNames[0] = mBackupMetadata.apkName
                for (i in 1 until allApkNames.size) {
                    allApkNames[i] = mBackupMetadata.splitConfigs!![i - 1]
                    allApks[i] = packageStagingDirectory.createNewFile(allApkNames[i]!!, null)
                }
            } catch (e: IOException) {
                throw BackupException("Could not create staging files", e)
            }
            // Decrypt sources
            try {
                backupSourceFiles = mBackupItem.decrypt(backupSourceFiles)
            } catch (e: IOException) {
                throw BackupException("Failed to decrypt ${backupSourceFiles.contentToString()}", e)
            }
            // Extract apk files to the package staging directory
            try {
                TarUtils.extract(mBackupInfo.tarType, backupSourceFiles, packageStagingDirectory, allApkNames.requireNoNulls(), null, null)
            } catch (th: Throwable) {
                throw BackupException("Failed to extract the apk file(s).", th)
            }
            // A normal update will do it now
            val options = InstallerOptions.getDefault()
            options.installerName = mBackupMetadata.installer
            options.userId = mUserId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                options.installScenario = PackageManager.INSTALL_SCENARIO_BULK
            }
            val status = AtomicReference<String?>()
            val packageInstaller = PackageInstallerCompat.getNewInstance()
            packageInstaller.setOnInstallListener(object : PackageInstallerCompat.OnInstallListener {
                override fun onStartInstall(sessionId: Int, packageName: String) {}

                override fun onAnotherAttemptInMiui(apkFile: io.github.muntashirakon.AppManager.apk.ApkFile?) {
                    packageInstaller.install(allApks.requireNoNulls(), mPackageName, options)
                }

                override fun onSecondAttemptInHyperOsWithoutInstaller(apkFile: io.github.muntashirakon.AppManager.apk.ApkFile?) {
                    options.installerName = "com.android.shell"\npackageInstaller.install(allApks.requireNoNulls(), mPackageName, options)
                }

                override fun onFinishedInstall(sessionId: Int, packageName: String, result: Int, blockingPackage: String?, statusMessage: String?) {
                    status.set(statusMessage)
                }
            })
            try {
                if (!packageInstaller.install(allApks.requireNoNulls(), mPackageName, options)) {
                    var statusMessage: String = if (!isVerified) {
                        // Previously installed app was uninstalled.
                        "Couldn't perform a re-installation"\n} else {
                        "Couldn't perform an installation"\n}
                    if (status.get() != null) {
                        statusMessage += ": " + status.get()
                    } else statusMessage += "."\nthrow BackupException(statusMessage)
                }
            } finally {
                deleteFiles(allApks.requireNoNulls()) // Clean up apk files
            }
            // Get package info, again
            try {
                mPackageInfo = PackageManagerCompat.getPackageInfo(
                    mPackageName, PackageManagerCompat.GET_SIGNING_CERTIFICATES
                            or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, mUserId
                )
                mUid = mPackageInfo!!.applicationInfo!!.uid
                mIsInstalled = true
            } catch (e: Exception) {
                throw BackupException("Apparently the install wasn't complete in the previous section.", e)
            }
        }
    }

    @Throws(BackupException::class)
    private fun restoreKeyStore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // keystore v2 is not supported.
            Log.w(TAG, "Ignoring KeyStore backups for %s", mPackageName)
            return
        }
        if (mPackageInfo == null) {
            throw BackupException("KeyStore restore is requested but the app isn't installed.")
        }
        var keyStoreFiles = mBackupItem.keyStoreFiles
        if (keyStoreFiles.isEmpty()) {
            throw BackupException("KeyStore files should've existed but they didn't")
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            var checksum: String
            for (file in keyStoreFiles) {
                checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file)
                if (checksum != mChecksum[file.getName()]) {
                    throw BackupException(
                        "KeyStore file verification failed." +
                                "\nFile: " + file +
                                "\nFound: " + checksum +
                                "\nRequired: " + mChecksum[file.getName()]
                    )
                }
            }
        }
        // Decrypt sources
        try {
            keyStoreFiles = mBackupItem.decrypt(keyStoreFiles)
        } catch (e: IOException) {
            throw BackupException("Failed to decrypt ${keyStoreFiles.contentToString()}", e)
        }
        // Restore KeyStore files to the /data/misc/keystore folder
        val keyStorePath = KeyStoreUtils.getKeyStorePath(mUserId)
        // Note down UID/GID
        val uidGidPair: UidGidPair
        val mode: Int
        try {
            uidGidPair = keyStorePath.getFile()!!.getUidGid()!!
            mode = keyStorePath.getFile()!!.mode
        } catch (e: ErrnoException) {
            throw BackupException("Failed to access properties of the KeyStore folder.", e)
        }
        try {
            TarUtils.extract(mBackupInfo.tarType, keyStoreFiles, keyStorePath, null, null, null)
            // Restore folder permission
            Paths.chown(keyStorePath, uidGidPair.uid, uidGidPair.gid)
            Paths.chmod(keyStorePath, mode and 0x1ff) // 0777 in octal
        } catch (th: Throwable) {
            throw BackupException("Failed to restore the KeyStore files.", th)
        }
        // Rename files
        val keyStoreFileNames = KeyStoreUtils.getKeyStoreFiles(KEYSTORE_PLACEHOLDER, mUserId)
        for (keyStoreFileName in keyStoreFileNames) {
            try {
                val newFilename = keyStoreFileName.replace(KEYSTORE_PLACEHOLDER.toString(), mUid.toString())
                keyStorePath.findFile(keyStoreFileName).renameTo(newFilename)
                val targetFile = keyStorePath.findFile(newFilename)
                // Restore file permission
                Paths.chown(targetFile, uidGidPair.uid, uidGidPair.gid)
                Paths.chmod(targetFile, 0x180) // 0600 in octal
            } catch (e: IOException) {
                throw BackupException("Failed to rename KeyStore files", e)
            } catch (e: ErrnoException) {
                throw BackupException("Failed to rename KeyStore files", e)
            }
        }
        Runner.runCommand(arrayOf("restorecon", "-R", keyStorePath.getFilePath()!!))
    }

    @Throws(BackupException::class)
    private fun restoreData() {
        // Data restore is requested: Data restore is only possible if the app is actually
        // installed. So, check if it's installed first.
        if (mPackageInfo == null) {
            throw BackupException("Data restore is requested but the app isn't installed.")
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            // Verify integrity of the data backups
            var checksum: String
            for (i in mBackupMetadata.dataDirs!!.indices) {
                val dataFiles = mBackupItem.getDataFiles(i)
                if (dataFiles.isEmpty()) {
                    throw BackupException("Data restore is requested but there are no data files for index $i.")
                }
                for (file in dataFiles) {
                    checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file)
                    if (checksum != mChecksum[file.getName()]) {
                        throw BackupException(
                            "Data file verification failed for index $i." +
                                    "\nFile: " + file +
                                    "\nFound: " + checksum +
                                    "\nRequired: " + mChecksum[file.getName()]
                        )
                    }
                }
            }
        }
        // Force-stop and clear app data
        PackageManagerCompat.clearApplicationUserData(mPackageName, mUserId)
        // Restore backups
        for (i in mBackupMetadata.dataDirs!!.indices) {
            val backupDataDir = mBackupMetadata.dataDirs!![i]
            if (backupDataDir == BackupManager.DATA_BACKUP_SPECIAL_ADB) {
                // Adb backup restore
                restoreAdb(i)
            } else {
                // Regular directory restore
                restoreDirectory(mBackupMetadata.dataDirs!![i], i)
            }
        }
    }

    @Throws(BackupException::class)
    private fun restoreDirectory(dir: String, index: Int) {
        val dataSource = BackupUtils.getWritableDataDirectory(dir, mBackupInfo.userId, mUserId)
        val dataDirectoryInfo = BackupDataDirectoryInfo.getInfo(dataSource, mUserId)
        val dataSourceFile = dataDirectoryInfo.getDirectory()

        var dataFiles = mBackupItem.getDataFiles(index)
        if (dataFiles.isEmpty()) {
            throw BackupException("Data restore is requested but there are no data files for index $index.")
        }
        var uidGidPair = dataSourceFile.getUidGid()
        if (uidGidPair == null) {
            // Fallback to app UID
            uidGidPair = UidGidPair(mUid, mUid)
        }
        if (dataDirectoryInfo.isExternal()) {
            // Skip if external data restore is not requested
            when (dataDirectoryInfo.subtype) {
                BackupDataDirectoryInfo.TYPE_ANDROID_DATA -> if (!mRequestedFlags.backupExternalData()) {
                    return
                }
                BackupDataDirectoryInfo.TYPE_ANDROID_OBB, BackupDataDirectoryInfo.TYPE_ANDROID_MEDIA -> if (!mRequestedFlags.backupMediaObb()) {
                    return
                }
                BackupDataDirectoryInfo.TYPE_CREDENTIAL_PROTECTED, BackupDataDirectoryInfo.TYPE_CUSTOM, BackupDataDirectoryInfo.TYPE_DEVICE_PROTECTED -> {}
            }
        } else {
            // Skip if internal data restore is not requested.
            if (!mRequestedFlags.backupInternalData()) {
                return
            }
        }
        // Create data folder if not exists
        if (!dataSourceFile.exists()) {
            if (dataDirectoryInfo.isExternal() && !dataDirectoryInfo.isMounted) {
                if (!Utils.isRoboUnitTest()) {
                    throw BackupException("External directory containing $dataSource is not mounted.")
                } // else Skip checking for mounted partition for robolectric tests
            }
            if (!dataSourceFile.mkdirs()) {
                throw BackupException("Could not create directory $dataSourceFile")
            }
            if (!dataDirectoryInfo.isExternal()) {
                // Restore UID, GID
                dataSourceFile.setUidGid(uidGidPair)
            }
        }
        // Decrypt data
        try {
            dataFiles = mBackupItem.decrypt(dataFiles)
        } catch (e: IOException) {
            throw BackupException("Failed to decrypt ${dataFiles.contentToString()}", e)
        }
        // Extract data to the data directory
        try {
            val isDebuggable = mPackageInfo?.applicationInfo?.let {
                (it.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            } ?: false
            val isInternalData = !dataDirectoryInfo.isExternal()

            if (Ops.isAdb() && isDebuggable && isInternalData) {
                // In ADB mode (shell UID = 2000), run-as lets us extract directly into the
                // app's data dir as that app's UID/GID — no root chown needed afterward.
                // ProcessBuilder would fail here because run-as rejects callers that aren't
                // shell or root; Runner.runCommand() routes through the privileged IPC service
                // which runs as shell, so run-as accepts it.
                val pkgName = mPackageInfo!!.packageName
                val destPath = dataSourceFile.getFilePath()!!
                val result = SplitInputStream(dataFiles).use { sis ->
                    Runner.runCommand(arrayOf("run-as", pkgName, "tar", "-xf", "-", "-C", destPath), sis)
                }
                if (!result.isSuccessful) {
                    // run-as tar failed (app may not be installed yet, tar not in PATH, or app
                    // not truly debuggable at extraction time); fall back to TarUtils, which
                    // needs root or Shizuku for correct ownership.
                    Log.w(TAG, "run-as tar extraction failed for $pkgName, falling back to TarUtils")
                    val publicSourceDir = File(mPackageInfo!!.applicationInfo!!.publicSourceDir!!).parent
                    TarUtils.extract(
                        mBackupInfo.tarType, dataFiles, dataSourceFile, null, BackupUtils
                            .getExcludeDirs(!mRequestedFlags.backupCache(), null), publicSourceDir
                    )
                }
            } else {
                val publicSourceDir = File(mPackageInfo!!.applicationInfo!!.publicSourceDir!!).parent
                TarUtils.extract(
                    mBackupInfo.tarType, dataFiles, dataSourceFile, null, BackupUtils
                        .getExcludeDirs(!mRequestedFlags.backupCache(), null), publicSourceDir
                )
            }
        } catch (th: Throwable) {
            throw BackupException("Failed to restore data files for index $index.", th)
        }
        // Restore UID and GID
        if (!dataDirectoryInfo.isExternal()) {
            val chownSuccess = Runner.runCommand(
                String.format(Locale.ROOT, "chown -R %d:%d \"%s\"", uidGidPair.uid, uidGidPair.gid, dataSourceFile.getFilePath())
            ).isSuccessful
            if (!chownSuccess && !Utils.isRoboUnitTest() && !Ops.isAdb()) {
                throw BackupException("Failed to restore ownership info for index $index.")
            }
            // Restore context
            if (!Ops.isAdb()) {
                Runner.runCommand(arrayOf("restorecon", "-R", dataSourceFile.getFilePath()!!))
            }
        }
    }

    @Throws(BackupException::class)
    private fun restoreAdb(index: Int) {
        var dataFiles = mBackupItem.getDataFiles(index)
        if (dataFiles.size != 1) {
            throw BackupException("ADB restore is requested but there are no .ab files.")
        }
        // Decrypt data
        try {
            dataFiles = mBackupItem.decrypt(dataFiles)
        } catch (e: IOException) {
            throw BackupException("Failed to decrypt ${dataFiles.contentToString()}", e)
        }
        // Restore data
        try {
            dataFiles[0].openInputStream().use { isStream ->
                val fd = ParcelFileDescriptorUtil.pipeFrom(isStream)
                BackupCompat.adbRestore(mUserId, fd)
            }
        } catch (th: Throwable) {
            throw BackupException("Failed to restore ADB data", th)
        }
    }

    @Synchronized
    @Throws(BackupException::class)
    private fun restoreExtras() {
        if (!mIsInstalled) {
            throw BackupException("Misc restore is requested but the app isn't installed.")
        }
        val rules = PseudoRules(mPackageName, mUserId)
        // Backward compatibility for restoring permissions
        loadMiscRules(rules)
        // Apply rules
        val entries = rules.all
        val appOpsManager = AppOpsManagerCompat()
        val notificationManager = INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE))
        val magiskHideAvailable = MagiskHide.available()
        val canModifyAppOpMode = SelfPermissions.canModifyAppOpMode()
        val canChangeNetPolicy = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)
        for (entry in entries) {
            try {
                when (entry.type) {
                    RuleType.APP_OP -> if (canModifyAppOpMode) {
                        appOpsManager.setMode(
                            entry.name.toInt(), mUid, mPackageName,
                            (entry as AppOpRule).mode
                        )
                    }
                    RuleType.NET_POLICY -> if (canChangeNetPolicy) {
                        NetworkPolicyManagerCompat.setUidPolicy(
                            mUid,
                            (entry as NetPolicyRule).policies
                        )
                    }
                    RuleType.PERMISSION -> {
                        val permissionRule = entry as PermissionRule
                        val permission = permissionRule.getPermission(true)
                        permission.isAppOpAllowed = permission.appOp != AppOpsManagerCompat.OP_NONE && appOpsManager
                            .checkOperation(permission.appOp, mUid, mPackageName) == AppOpsManager.MODE_ALLOWED
                        if (permissionRule.isGranted) {
                            PermUtils.grantPermission(mPackageInfo, permission, appOpsManager, true, true)
                        } else {
                            PermUtils.revokePermission(mPackageInfo, permission, appOpsManager, true)
                        }
                    }
                    RuleType.BATTERY_OPT -> if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER)) {
                        DeviceIdleManagerCompat.disableBatteryOptimization(mPackageName)
                    }
                    RuleType.MAGISK_HIDE -> {
                        val magiskHideRule = entry as MagiskHideRule
                        if (magiskHideAvailable) {
                            MagiskHide.apply(magiskHideRule.magiskProcess, false)
                        } else {
                            // Fall-back to Magisk DenyList
                            MagiskDenyList.apply(magiskHideRule.magiskProcess, false)
                        }
                    }
                    RuleType.MAGISK_DENY_LIST -> {
                        MagiskDenyList.apply((entry as MagiskDenyListRule).magiskProcess, false)
                    }
                    RuleType.NOTIFICATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                        && SelfPermissions.checkNotificationListenerAccess()
                    ) {
                        notificationManager.setNotificationListenerAccessGrantedForUser(
                            ComponentName(mPackageName, entry.name), mUserId, true
                        )
                    }
                    RuleType.URI_GRANT -> {
                        val uriGrant = (entry as UriGrantRule).uriGrant
                        val newUriGrant = UriManager.UriGrant(
                            uriGrant.sourceUserId, mUserId, uriGrant.userHandle,
                            uriGrant.sourcePkg, uriGrant.targetPkg, uriGrant.uri,
                            uriGrant.prefix, uriGrant.modeFlags, uriGrant.createdTime
                        )
                        val uriManager = UriManager()
                        uriManager.grantUri(newUriGrant)
                        uriManager.writeGrantedUriPermissions()
                        mRequiresRestart = true
                    }
                    RuleType.SSAID -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        SsaidSettings(mUserId).setSsaid(
                            mPackageName, mUid,
                            (entry as SsaidRule).ssaid
                        )
                        mRequiresRestart = true
                    }
                    RuleType.FREEZE -> {
                        val freezeType = (entry as FreezeRule).freezeType
                        FreezeUtils.storeFreezeMethod(mPackageName, freezeType)
                    }
                    else -> {}
                }
            } catch (e: Throwable) {
                // There are several reason restoring these things go wrong, especially when
                // downgrading from an Android to another. It's better to simply suppress these
                // exceptions instead of causing a failure or worse, a crash
                Log.e(TAG, e)
            }
        }
    }

    @Throws(BackupException::class)
    private fun loadMiscRules(rules: PseudoRules) {
        var miscFile: Path = try {
            mBackupItem.miscFile
        } catch (e: IOException) {
            // There are no permissions, just skip
            return
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            val checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, miscFile)
            if (checksum != mChecksum[miscFile.getName()]) {
                throw BackupException(
                    "Couldn't verify misc file." +
                            "\nFile: " + miscFile +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum[miscFile.getName()]
                )
            }
        }
        // Decrypt permission file
        try {
            miscFile = mBackupItem.decrypt(arrayOf(miscFile))[0]
        } catch (e: IOException) {
            throw BackupException("Failed to decrypt ${miscFile.getName()}", e)
        } catch (e: IndexOutOfBoundsException) {
            throw BackupException("Failed to decrypt ${miscFile.getName()}", e)
        }
        try {
            rules.loadExternalEntries(miscFile)
        } catch (th: Throwable) {
            throw BackupException("Failed to load rules from misc.", th)
        }
    }

    @Throws(BackupException::class)
    private fun restoreRules() {
        // Apply rules
        if (!mIsInstalled) {
            throw BackupException("Rules restore is requested but the app isn't installed.")
        }
        var rulesFile: Path = try {
            mBackupItem.rulesFile
        } catch (e: IOException) {
            if (mBackupMetadata.hasRules) {
                throw BackupException("Rules file is missing.", e)
            } else {
                // There are no rules, just skip
                return
            }
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            val checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, rulesFile)
            if (checksum != mChecksum[rulesFile.getName()]) {
                throw BackupException(
                    "Couldn't verify permission file." +
                            "\nFile: " + rulesFile +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum[rulesFile.getName()]
                )
            }
        }
        // Decrypt rules file
        try {
            rulesFile = mBackupItem.decrypt(arrayOf(rulesFile))[0]
        } catch (e: IOException) {
            throw BackupException("Failed to decrypt ${rulesFile.getName()}", e)
        } catch (e: IndexOutOfBoundsException) {
            throw BackupException("Failed to decrypt ${rulesFile.getName()}", e)
        }
        try {
            RulesImporter(listOf(*RuleType.values()), intArrayOf(mUserId)).use { importer ->
                importer.addRulesFromPath(rulesFile)
                importer.setPackagesToImport(listOf(mPackageName))
                importer.applyRules(true)
            }
        } catch (e: IOException) {
            throw BackupException("Failed to restore rules file.", e)
        }
    }

    private fun deleteFiles(files: Array<Path>) {
        for (file in files) {
            file.delete()
        }
    }

    companion object {
        val TAG: String = RestoreOp::class.java.simpleName
        private val sLock = Any()

        private fun incrementProgress(progressHandler: ProgressHandler?) {
            if (progressHandler == null) {
                return
            }
            val current = progressHandler.lastProgress + 1
            progressHandler.postUpdate(current)
        }
    }
}
