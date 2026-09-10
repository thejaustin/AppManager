// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandleHidden
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.collection.ArrayMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer
import io.github.muntashirakon.AppManager.accessibility.NoRootAccessibilityService
import io.github.muntashirakon.AppManager.apk.ApkFile
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.apk.ApkUtils
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreezeShortcutInfo
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptDialog
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.compat.*
import io.github.muntashirakon.AppManager.compat.ManifestCompat.permission.TERMUX_RUN_COMMAND
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.details.AppDetailsFragment
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel
import io.github.muntashirakon.AppManager.details.manifest.ManifestViewerActivity
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.fm.dialogs.OpenWithDialogFragment
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList
import io.github.muntashirakon.AppManager.magisk.MagiskHide
import io.github.muntashirakon.AppManager.magisk.MagiskProcess
import io.github.muntashirakon.AppManager.profiles.AddToProfileDialogFragment
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.runner.RunnerUtils
import io.github.muntashirakon.AppManager.scanner.ScannerActivity
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.sharedpref.SharedPrefsActivity
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment
import io.github.muntashirakon.AppManager.ssaid.ChangeSsaidDialog
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.uri.GrantUriUtils
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager
import io.github.muntashirakon.AppManager.usage.UsageUtils
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.*
import io.github.muntashirakon.AppManager.utils.Utils.openAsFolderInFM
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.dialog.*
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.view.ProgressIndicatorCompat
import io.github.muntashirakon.widget.RecyclerView
import io.github.muntashirakon.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@AndroidEntryPoint
class AppInfoFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener, MenuProvider {
    private lateinit var mPackageManager: PackageManager
    private var mPackageName: String? = null
    private var mUserId: Int = 0
    private var mInstallerPackageName: String? = null
    private var mPackageInfo: PackageInfo? = null
    private var mInstalledPackageInfo: PackageInfo? = null
    private lateinit var mActivity: AppDetailsActivity
    private var mApplicationInfo: ApplicationInfo? = null
    private lateinit var mHorizontalLayout: ViewGroup
    private lateinit var mTagCloud: ViewGroup
    private lateinit var mSwipeRefresh: SwipeRefreshLayout
    private var mAppLabel: CharSequence? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mMainModel: AppDetailsViewModel? = null
    private lateinit var mAppInfoModel: AppInfoViewModel
    private lateinit var mAdapter: AppInfoRecyclerAdapter

    private lateinit var mLabelView: TextView
    private lateinit var mPackageNameView: TextView
    private lateinit var mVersionView: TextView
    private lateinit var mIconView: ImageView
    private var mMagiskHiddenProcesses: List<MagiskProcess>? = null
    private var mMagiskDeniedProcesses: List<MagiskProcess>? = null
    private var mTagCloudFuture: Future<*>? = null
    private var mActionsFuture: Future<*>? = null
    private var mListFuture: Future<*>? = null
    private var mMenuPreparationResult: Future<*>? = null

    private var mIsExternalApk: Boolean = false
    private var mLoadedItemCount: Int = 0

    private val mListItems = mutableListOf<ListItem>()
    private val mExport = BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.CreateDocument("*/*"))
    private val mRequestPerm = BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.RequestPermission())
    private val mActivityLauncher = BetterActivityResult.registerActivityForResult(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAppInfoModel = ViewModelProvider(this).get(AppInfoViewModel::class.java)
        mMainModel = ViewModelProvider(requireActivity()).get(AppDetailsViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.pager_app_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mActivity = requireActivity() as AppDetailsActivity
        mAppInfoModel.setMainModel(mMainModel!!)
        mPackageManager = mActivity.packageManager
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh)
        mSwipeRefresh.setOnRefreshListener(this)
        val recyclerView: io.github.muntashirakon.widget.RecyclerView = view.findViewById(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(mActivity)
        mHorizontalLayout = view.findViewById(R.id.horizontal_layout)
        mProgressIndicator = view.findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE
        showProgressIndicator(true)
        mTagCloud = view.findViewById(R.id.tag_cloud)
        mLabelView = view.findViewById(R.id.label)
        mPackageNameView = view.findViewById(R.id.packageName)
        mIconView = view.findViewById(R.id.icon)
        mVersionView = view.findViewById(R.id.version)
        mAdapter = AppInfoRecyclerAdapter(requireContext())
        recyclerView.adapter = mAdapter
        mActivity.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        mMainModel!!.get(AppDetailsFragment.APP_INFO).observe(viewLifecycleOwner) { appDetailsItems ->
            mLoadedItemCount = 0
            if (appDetailsItems == null || appDetailsItems.isEmpty() || !mMainModel!!.isPackageExist) {
                showProgressIndicator(false)
                return@observe
            }
            mLoadedItemCount++
            val appDetailsItem = appDetailsItems[0]
            mPackageInfo = appDetailsItem.item as PackageInfo
            mApplicationInfo = mPackageInfo!!.applicationInfo
            mPackageName = appDetailsItem.name
            mUserId = mMainModel!!.getUserId()
            mInstalledPackageInfo = mMainModel!!.mInstalledPackageInfo
            mIsExternalApk = mMainModel!!.isExternalApk
            if (!mIsExternalApk) {
                mInstallerPackageName = PackageManagerCompat.getInstallerPackageName(mPackageName!!, mUserId)
            }
            ImageLoader.getInstance().displayImage(mPackageName!!, mApplicationInfo!!, mIconView)
            mPackageNameView.text = mPackageName
            mPackageNameView.setOnClickListener { Utils.copyToClipboard(ContextUtils.getContext(), "Package name", mPackageName) }
            val version = getString(R.string.version_name_with_code, mPackageInfo!!.versionName, PackageInfoCompat.getLongVersionCode(mPackageInfo!!))
            mVersionView.text = version
            mAppInfoModel.loadAppLabel(mApplicationInfo!!)
            mAppInfoModel.loadTagCloud(mPackageInfo!!, mIsExternalApk)
            setupHorizontalActions()
            mAppInfoModel.loadAppInfo(mPackageInfo!!, mIsExternalApk)
        }
        mAppInfoModel.getAppLabel().observe(viewLifecycleOwner) { appLabel ->
            mLoadedItemCount++
            if (mLoadedItemCount >= 4) showProgressIndicator(false)
            mAppLabel = appLabel
            mLabelView.text = mAppLabel
        }
        mMainModel!!.getFreezeTypeLiveData().observe(viewLifecycleOwner) { freezeType ->
            val freezeTypeN = freezeType ?: Prefs.Blocking.getDefaultFreezingMethod()
            showFreezeDialog(freezeTypeN, freezeType != null)
        }
        mIconView.setOnClickListener {
            ThreadUtils.postOnBackgroundThread {
                val data = ClipboardUtils.readHashValueFromClipboard(ContextUtils.getContext())
                if (data != null) {
                    val signerInfo = PackageUtils.getSignerInfo(mPackageInfo!!, mIsExternalApk)
                    if (signerInfo != null) {
                        val certs = signerInfo.currentSignerCerts
                        if (certs != null && certs.size == 1) {
                            try {
                                val digests = DigestUtils.getDigests(certs[0].encoded)
                                for (digest in digests) {
                                    if (digest.second == data) {
                                        if (digest.first == DigestUtils.MD5 || digest.first == DigestUtils.SHA_1) {
                                            ThreadUtils.postOnMainThread { displayLongToast(R.string.verified_using_unreliable_hash) }
                                        } else ThreadUtils.postOnMainThread { displayLongToast(R.string.verified) }
                                        return@postOnBackgroundThread
                                    }
                                }
                            } catch (ignore: CertificateEncodingException) {}
                        }
                    }
                    ThreadUtils.postOnMainThread { displayLongToast(R.string.not_verified) }
                }
            }
        }
        mAppInfoModel.getTagCloud().observe(viewLifecycleOwner) { setupTagCloud(it) }
        mAppInfoModel.getAppInfo().observe(viewLifecycleOwner) { setupVerticalView(it) }
        mAppInfoModel.getInstallExistingResult().observe(viewLifecycleOwner) { statusMessagePair ->
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(mAppLabel)
                .setIcon(mApplicationInfo!!.loadIcon(mPackageManager))
                .setMessage(statusMessagePair.second)
                .setNegativeButton(R.string.close, null)
                .show()
        }
        mMainModel!!.getTagsAlteredLiveData().observe(viewLifecycleOwner) { mAppInfoModel.loadTagCloud(mPackageInfo!!, mIsExternalApk) }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        if (mMainModel != null && !mMainModel!!.isExternalApk) {
            inflater.inflate(R.menu.fragment_app_info_actions, menu)
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        if (mIsExternalApk) return
        val magiskHideMenu = menu.findItem(R.id.action_magisk_hide)
        val magiskDenyListMenu = menu.findItem(R.id.action_magisk_denylist)
        val openInTermuxMenu = menu.findItem(R.id.action_open_in_termux)
        val runInTermuxMenu = menu.findItem(R.id.action_run_in_termux)
        val batteryOptMenu = menu.findItem(R.id.action_battery_opt)
        val sensorsMenu = menu.findItem(R.id.action_sensor)
        val netPolicyMenu = menu.findItem(R.id.action_net_policy)
        val installMenu = menu.findItem(R.id.action_install)
        val optimizeMenu = menu.findItem(R.id.action_optimize)
        mMenuPreparationResult = ThreadUtils.postOnBackgroundThread {
            val magiskHideAvailable = MagiskHide.available()
            val magiskDenyListAvailable = MagiskDenyList.available()
            val rootAvailable = RunnerUtils.isRootAvailable()
            if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
            ThreadUtils.postOnMainThread {
                magiskHideMenu?.isVisible = magiskHideAvailable
                magiskDenyListMenu?.isVisible = magiskDenyListAvailable
                openInTermuxMenu?.isVisible = rootAvailable
            }
        }
        val isDebuggable = mApplicationInfo?.let { (it.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 } ?: false
        runInTermuxMenu?.isVisible = isDebuggable
        batteryOptMenu?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        sensorsMenu?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)
        netPolicyMenu?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        installMenu?.isVisible = Users.getUsersIds().size > 1 && SelfPermissions.canInstallExistingPackages()
        optimizeMenu?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (SelfPermissions.isSystemOrRootOrShell() || BuildConfig.APPLICATION_ID == mInstallerPackageName)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh_detail -> { refreshDetails(); true }
            R.id.action_share_apk -> {
                showProgressIndicator(true)
                ThreadUtils.postOnBackgroundThread {
                    try {
                        val tmpApkSource = ApkUtils.getSharableApkFile(requireContext(), mPackageInfo!!)
                        ThreadUtils.postOnMainThread {
                            showProgressIndicator(false)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/*"\nputExtra(Intent.EXTRA_STREAM, FmProvider.getContentUri(tmpApkSource))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ContextUtils.getContext().startActivity(Intent.createChooser(intent, ContextUtils.getContext().getString(R.string.share_apk)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e)
                        displayLongToast(R.string.failed_to_extract_apk_file)
                    }
                }
                true
            }
            R.id.action_backup -> {
                mMainModel?.let { mm ->
                    val fragment = BackupRestoreDialogFragment.getInstanceWithPref(listOf(UserPackagePair(mPackageName!!, mUserId)), mUserId)
                    fragment.setOnActionBeginListener { showProgressIndicator(true) }
                    fragment.setOnActionCompleteListener { _, _ -> showProgressIndicator(false); mm.getTagsAlteredLiveData().value = true }
                    fragment.show(parentFragmentManager, BackupRestoreDialogFragment.TAG)
                }
                true
            }
            R.id.action_view_settings -> {
                try { ActivityManagerCompat.startActivity(IntentUtils.getAppDetailsSettings(mPackageName), mUserId) }
                catch (th: Throwable) { UIUtils.displayLongToast("Error: ${th.localizedMessage}") }
                true
            }
            R.id.action_export_blocking_rules -> {
                val fileName = "app_manager_rules_export-${DateUtils.formatDateTime(mActivity, System.currentTimeMillis())}.am.tsv"\nmExport.launch(fileName) { uri ->
                    if (uri == null || mMainModel == null) return@launch
                    val dialogFragment = RulesTypeSelectionDialogFragment()
                    dialogFragment.arguments = Bundle().apply {
                        putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT)
                        putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri)
                        putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, arrayListOf(mPackageName!!))
                        putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, intArrayOf(mUserId))
                    }
                    dialogFragment.show(mActivity.supportFragmentManager, RulesTypeSelectionDialogFragment.TAG)
                }
                true
            }
            R.id.action_open_in_termux -> {
                if (SelfPermissions.checkSelfPermission(TERMUX_RUN_COMMAND)) openInTermux()
                else mRequestPerm.launch(TERMUX_RUN_COMMAND) { if (it) openInTermux() }
                true
            }
            R.id.action_run_in_termux -> {
                if (SelfPermissions.checkSelfPermission(TERMUX_RUN_COMMAND)) runInTermux()
                else mRequestPerm.launch(TERMUX_RUN_COMMAND) { if (it) runInTermux() }
                true
            }
            R.id.action_magisk_hide -> { displayMagiskHideDialog(); true }
            R.id.action_magisk_denylist -> { displayMagiskDenyListDialog(); true }
            R.id.action_battery_opt -> {
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER)) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.battery_optimization)
                        .setMessage(R.string.choose_what_to_do)
                        .setPositiveButton(R.string.enable) { _, _ ->
                            if (DeviceIdleManagerCompat.enableBatteryOptimization(mPackageName)) {
                                UIUtils.displayShortToast(R.string.done); mMainModel?.getTagsAlteredLiveData()?.value = true
                            } else UIUtils.displayShortToast(R.string.failed)
                        }
                        .setNegativeButton(R.string.disable) { _, _ ->
                            if (DeviceIdleManagerCompat.disableBatteryOptimization(mPackageName)) {
                                UIUtils.displayShortToast(R.string.done); mMainModel?.getTagsAlteredLiveData()?.value = true
                            } else UIUtils.displayShortToast(R.string.failed)
                        }
                        .show()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try { startActivity(IntentUtils.getBatteryOptSettings(mPackageName)) }
                    catch (th: Throwable) { UIUtils.displayShortToast("No DEVICE_POWER permission.") }
                }
                true
            }
            R.id.action_sensor -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.sensors)
                        .setMessage(R.string.choose_what_to_do)
                        .setPositiveButton(R.string.enable) { _, _ -> ThreadUtils.postOnBackgroundThread {
                            try { SensorServiceCompat.enableSensor(mPackageName!!, mUserId, true); mMainModel?.getTagsAlteredLiveData()?.postValue(true); ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.done) } }
                            catch (e: IOException) { ThreadUtils.postOnMainThread { UIUtils.displayLongToast("${getString(R.string.failed)}${LangUtils.getSeparatorString()}${e.message}") } }
                        } }
                        .setNegativeButton(R.string.disable) { _, _ -> ThreadUtils.postOnBackgroundThread {
                            try { SensorServiceCompat.enableSensor(mPackageName!!, mUserId, false); mMainModel?.getTagsAlteredLiveData()?.postValue(true); ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.done) } }
                            catch (e: IOException) { ThreadUtils.postOnMainThread { UIUtils.displayLongToast("${getString(R.string.failed)}${LangUtils.getSeparatorString()}${e.message}") } }
                        } }
                        .show()
                }
                true
            }
            R.id.action_net_policy -> {
                if (!UserHandleHidden.isApp(mApplicationInfo!!.uid)) { UIUtils.displayLongToast(R.string.netpolicy_cannot_be_modified_for_core_apps); return true }
                if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try { startActivity(IntentUtils.getNetPolicySettings(mPackageName)) }
                        catch (th: Throwable) { UIUtils.displayShortToast("No MANAGE_NETWORK_POLICY permission.") }
                    }
                    return true
                }
                val netPolicyMap = NetworkPolicyManagerCompat.getAllReadablePolicies(ContextUtils.getContext())
                val policies = Array(netPolicyMap.size()) { netPolicyMap.keyAt(it) }
                val policyStrings = Array(netPolicyMap.size()) { netPolicyMap.valueAt(it) as CharSequence }
                val selected = NetworkPolicyManagerCompat.getUidPolicy(mApplicationInfo!!.uid)
                SearchableFlagsDialogBuilder(mActivity, policies, policyStrings, selected)
                    .setTitle(R.string.net_policy)
                    .showSelectAll(false)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save) { _, _, selections ->
                        var f = 0
                        for (sel in selections) f = f or sel
                        NetworkPolicyManagerCompat.setUidPolicy(mApplicationInfo!!.uid, f)
                        mMainModel?.getTagsAlteredLiveData()?.value = true
                    }
                    .show()
                true
            }
            R.id.action_extract_icon -> {
                mExport.launch("${mAppLabel}_icon.png") { uri ->
                    if (uri == null) return@launch
                    ThreadUtils.postOnBackgroundThread {
                        try {
                            Paths.get(uri).openOutputStream()?.use { os ->
                                getBitmapFromDrawable(mApplicationInfo!!.loadIcon(mPackageManager)).compress(Bitmap.CompressFormat.PNG, 100, os)
                                os.flush()
                                ThreadUtils.postOnMainThread { displayShortToast(R.string.saved_successfully) }
                            } ?: throw IOException("Unable to open output stream.")
                        } catch (e: IOException) {
                            Log.e(TAG, e); ThreadUtils.postOnMainThread { displayShortToast(R.string.saving_failed) }
                        }
                    }
                }
                true
            }
            R.id.action_archive -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.are_you_sure)
                    .setMessage(R.string.this_action_cannot_be_undone)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        showProgressIndicator(true)
                        val packages = arrayListOf(mPackageName!!)
                        val users = arrayListOf(mUserId)
                        val archiveBatchQueueItem = BatchQueueItem.getBatchOpQueue(
                            BatchOpsManager.OP_ARCHIVE, packages, users, null
                        )
                        val intent = BatchOpsService.getServiceIntent(requireContext(), archiveBatchQueueItem)
                        ContextCompat.startForegroundService(requireContext(), intent)
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            R.id.action_install -> {
                val users = Users.getUsers()
                val userNames = Array(users.size) { users[it].toLocalizedString(requireContext()) }
                SearchableItemsDialogBuilder(mActivity, userNames)
                    .setTitle(R.string.select_user)
                    .setOnItemClickListener { dialog, which, _ -> mAppInfoModel.installExisting(mPackageName!!, users[which].id); dialog.dismiss() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            R.id.action_add_to_profile -> {
                AddToProfileDialogFragment.getInstance(arrayOf(mPackageName!!)).show(childFragmentManager, AddToProfileDialogFragment.TAG)
                true
            }
            R.id.action_optimize -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (SelfPermissions.isSystemOrRootOrShell() || BuildConfig.APPLICATION_ID == mInstallerPackageName)) {
                    DexOptDialog.getInstance(arrayOf(mPackageName!!)).show(childFragmentManager, DexOptDialog.TAG)
                } else UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode)
                true
            }
            else -> false
        }
    }

    override fun onMenuClosed(menu: Menu) { mMenuPreparationResult?.cancel(true) }

    override fun onStart() {
        super.onStart()
        mActivity.searchView?.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        mActivity.searchView?.visibility = View.GONE
    }

    override fun onRefresh() {
        refreshDetails()
        mSwipeRefresh.isRefreshing = false
    }

    override fun onDetach() {
        mTagCloudFuture?.cancel(true)
        mActionsFuture?.cancel(true)
        mListFuture?.cancel(true)
        super.onDetach()
    }

    private fun refreshDetails() {
        val pi = mPackageInfo ?: return
        showProgressIndicator(true)
        mAppInfoModel.loadTagCloud(pi, mIsExternalApk)
        mAppInfoModel.loadAppInfo(pi, mIsExternalApk)
    }

    @WorkerThread
    private fun getHorizontalActions(): List<ActionItem> {
        val pn = mPackageName ?: return emptyList()
        if (mIsExternalApk) return emptyList()
        val userId = mUserId
        val items = mutableListOf<ActionItem>()

        // Launch
        mAppInfoModel.getAppInfo().value?.mainActivity?.let { launchIntent ->
            items.add(ActionItem(R.string.launch_app, R.drawable.ic_open_in_new)
                .setOnClickListener { _ -> startActivity(launchIntent) })
        }

        // Force Stop
        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
            items.add(ActionItem(R.string.force_stop, R.drawable.ic_stop)
                .setOnClickListener { _ ->
                    MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.force_stop)
                        .setMessage(R.string.force_stop_message)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            ThreadUtils.postOnBackgroundThread {
                                try {
                                    PackageManagerCompat.forceStopPackage(pn, userId)
                                    mMainModel?.getTagsAlteredLiveData()?.postValue(true)
                                    ThreadUtils.postOnMainThread { displayShortToast(R.string.done) }
                                } catch (e: Exception) {
                                    Log.e(TAG, e)
                                    ThreadUtils.postOnMainThread {
                                        displayLongToast(R.string.failed_to_stop, mAppLabel)
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.no, null)
                        .show()
                })
        }

        // Clear Cache — reloads size info on completion (fixes upstream #2023)
        items.add(ActionItem(R.string.clear_cache, R.drawable.ic_clear_cache)
            .setOnClickListener { _ ->
                ThreadUtils.postOnBackgroundThread {
                    val success = PackageManagerCompat.deleteApplicationCacheFilesAsUser(pn, userId)
                    ThreadUtils.postOnMainThread {
                        displayShortToast(if (success) R.string.done else R.string.failed)
                        mPackageInfo?.let { mAppInfoModel.loadAppInfo(it, mIsExternalApk) }
                    }
                }
            })

        // Clear Data
        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)) {
            items.add(ActionItem(R.string.clear_data, R.drawable.ic_clear_data)
                .setOnClickListener { _ ->
                    MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.clear_data)
                        .setMessage(R.string.clear_data_message)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            ThreadUtils.postOnBackgroundThread {
                                val success = PackageManagerCompat.clearApplicationUserData(pn, userId)
                                ThreadUtils.postOnMainThread {
                                    displayShortToast(if (success) R.string.done else R.string.failed)
                                    mPackageInfo?.let { mAppInfoModel.loadAppInfo(it, mIsExternalApk) }
                                }
                            }
                        }
                        .setNegativeButton(R.string.no, null)
                        .show()
                })
        }

        return items
    }

    private fun openInTermux() { runWithTermux(arrayOf("su", "-", mApplicationInfo!!.uid.toString())) }
    private fun runInTermux() { runWithTermux(arrayOf("su", "-c", "run-as", mPackageName!!)) }
    private fun runWithTermux(command: Array<String>) {
        val intent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"\nputExtra("com.termux.RUN_COMMAND_PATH", Utils.TERMUX_LOGIN_PATH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", command)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }
        try { ActivityCompat.startForegroundService(mActivity, intent) }
        catch (e: Exception) { UIUtils.displayLongToast("Error: ${e.message}") }
    }

    private fun install() {
        mMainModel?.apkSource?.let { src ->
            try { startActivity(PackageInstallerActivity.getLaunchableInstance(requireContext(), src)) }
            catch (e: Exception) { UIUtils.displayLongToast("Error: ${e.message}") }
        }
    }

    private fun setupTagCloud(tagCloud: AppInfoViewModel.TagCloud) {
        mTagCloudFuture?.cancel(true)
        mTagCloudFuture = ThreadUtils.postOnBackgroundThread {
            val tagItems = getTagCloudItems(tagCloud)
            ThreadUtils.postOnMainThread {
                if (isDetached) return@postOnMainThread
                mLoadedItemCount++
                if (mLoadedItemCount >= 4) showProgressIndicator(false)
                mTagCloud.removeAllViews()
                tagItems.forEach { mTagCloud.addView(it.toChip(mTagCloud.context, mTagCloud)) }
            }
        }
    }

    private fun getTagCloudItems(tagCloud: AppInfoViewModel.TagCloud): List<TagItem> {
        val mm = mMainModel!!
        val context = mTagCloud.context
        val tagItems = mutableListOf<TagItem>()
        tagCloud.trackerComponents?.let { trackers ->
            if (trackers.isNotEmpty()) {
                val names = Array(trackers.size) { i -> val r = trackers[i]; if (r.isBlocked) getColoredText(r.name, ColorCodes.getComponentTrackerBlockedIndicatorColor(context)) else r.name }
                tagItems.add(TagItem().apply {
                    setText(resources.getQuantityString(R.plurals.no_of_trackers, trackers.size, trackers.size))
                    setColor(if (tagCloud.areAllTrackersBlocked) ColorCodes.getComponentTrackerBlockedIndicatorColor(context) else ColorCodes.getComponentTrackerIndicatorColor(context))
                    setOnClickListener {
                        if (!mIsExternalApk && SelfPermissions.canModifyAppComponentStates(mUserId, mPackageName!!, mm.isTestOnlyApp())) {
                            SearchableMultiChoiceDialogBuilder(it.context, trackers, names).setTitle(R.string.trackers).addSelections(trackers).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.block) { _, _, selected ->
                                showProgressIndicator(true)
                                ThreadUtils.postOnBackgroundThread { mm.addRules(selected, true); ThreadUtils.postOnMainThread { if (!isDetached) showProgressIndicator(false); displayShortToast(R.string.done) } }
                            }.setNeutralButton(R.string.unblock) { _, _, selected ->
                                showProgressIndicator(true)
                                ThreadUtils.postOnBackgroundThread { mm.removeRules(selected, true); ThreadUtils.postOnMainThread { if (!isDetached) showProgressIndicator(false); displayShortToast(R.string.done) } }
                            }.show()
                        } else SearchableItemsDialogBuilder(it.context, names).setTitle(R.string.trackers).setNegativeButton(R.string.close, null).show()
                    }
                })
            }
        }
        if (tagCloud.isSystemApp) {
            tagItems.add(TagItem().setTextRes(if (tagCloud.isSystemlessPath) R.string.systemless_app else R.string.system_app))
            if (tagCloud.isUpdatedSystemApp) tagItems.add(TagItem().setTextRes(R.string.updated_app))
        } else if (!mIsExternalApk) tagItems.add(TagItem().setTextRes(R.string.user_app))
        if (tagCloud.splitCount > 0) {
            tagItems.add(TagItem().apply {
                setText(resources.getQuantityString(R.plurals.no_of_splits, tagCloud.splitCount, tagCloud.splitCount))
                setOnClickListener { mm.apkFile?.let { af ->
                    val entries = af.entries
                    val names = Array(tagCloud.splitCount) { i -> entries[it + 1].toLocalizedString(it.context) }
                    SearchableItemsDialogBuilder(it.context, names).setTitle(R.string.splits).setNegativeButton(R.string.close, null).show()
                } }
            })
        }
        if (tagCloud.isDebuggable) tagItems.add(TagItem().setTextRes(R.string.debuggable))
        if (tagCloud.isTestOnly) tagItems.add(TagItem().setTextRes(R.string.test_only))
        if (!tagCloud.hasCode) tagItems.add(TagItem().setTextRes(R.string.no_code))
        if (tagCloud.isOverlay) {
            tagItems.add(TagItem().apply {
                setTextRes(R.string.title_overlay)
                setOnClickListener {
                    val target = PackageInfoCompat2.getOverlayTarget(mPackageInfo!!)!!
                    val targetName = PackageInfoCompat2.getTargetOverlayableName(mPackageInfo!!)
                    val category = PackageInfoCompat2.getOverlayCategory(mPackageInfo!!)
                    val priority = PackageInfoCompat2.getOverlayPriority(mPackageInfo!!)
                    val isStatic = PackageInfoCompat2.isStaticOverlayPackage(mPackageInfo!!)
                    val ssb = SpannableStringBuilder().apply {
                        if (targetName != null) append(getStyledKeyValue(it.context, R.string.overlay_target, targetName)).append("\n").append(getSmallerText(target))
                        else append(getStyledKeyValue(it.context, R.string.overlay_target, target))
                        category?.let { c -> append("\n").append(getSmallerText(getStyledKeyValue(it.context, R.string.overlay_category, c))) }
                        if (!isStatic) append("\n").append(getSmallerText(getStyledKeyValue(it.context, R.string.priority, priority.toString())))
                    }
                    MaterialAlertDialogBuilder(it.context).setTitle(R.string.title_overlay).setMessage(ssb).setNeutralButton(R.string.app_info) { _, _ -> startActivity(AppDetailsActivity.getIntent(it.context, target, mUserId)) }.setNegativeButton(R.string.close, null).show()
                }
            })
        }
        if (tagCloud.hasRequestedLargeHeap) tagItems.add(TagItem().setTextRes(R.string.requested_large_heap))
        tagCloud.hostsToOpen?.let { hosts ->
            tagItems.add(TagItem().apply {
                setTextRes(R.string.app_info_tag_open_links)
                setColor(if (tagCloud.canOpenLinks) ColorCodes.getFailureColor(context) else ColorCodes.getSuccessColor(context))
                setOnClickListener {
                    val builder = SearchableItemsDialogBuilder(it.context, hosts.keys.toList()).setTitle(R.string.title_domains_supported_by_the_app).setNegativeButton(R.string.close, null)
                    if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION)) {
                        builder.setPositiveButton(if (tagCloud.canOpenLinks) R.string.disable else R.string.enable) { _, _ -> ThreadUtils.postOnBackgroundThread {
                            try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DomainVerificationManagerCompat.setDomainVerificationLinkHandlingAllowed(mPackageName!!, !tagCloud.canOpenLinks, mUserId); mm.getTagsAlteredLiveData().postValue(true); ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.done) } }
                            catch (th: Throwable) { th.printStackTrace(); ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.failed) } }
                        } }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setPositiveButton(R.string.app_settings) { _, _ -> try { startActivity(IntentUtils.getSettings(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, mPackageName!!)) } catch (th: Throwable) { ExUtils.exceptionAsIgnored { startActivity(IntentUtils.getAppDetailsSettings(mPackageName!!)) } } }
                    }
                    builder.show()
                }
            })
        }
        tagCloud.runningServices?.let { services ->
            if (services.isNotEmpty()) tagItems.add(TagItem().apply {
                setTextRes(R.string.running)
                setColor(ColorCodes.getComponentRunningIndicatorColor(context))
                setOnClickListener { displayRunningServices(services, it.context) }
            }) else if (tagCloud.isRunning) tagItems.add(TagItem().apply {
                setTextRes(R.string.running)
                setColor(ColorCodes.getComponentRunningIndicatorColor(context))
            })
        }
        if (tagCloud.isForceStopped) tagItems.add(TagItem().setTextRes(R.string.stopped).setColor(ColorCodes.getAppForceStoppedIndicatorColor(context)))
        if (!tagCloud.isAppEnabled) tagItems.add(TagItem().setTextRes(R.string.disabled_app).setColor(ColorCodes.getAppDisabledIndicatorColor(context)))
        if (tagCloud.isAppSuspended) tagItems.add(TagItem().setTextRes(R.string.suspended).setColor(ColorCodes.getAppSuspendedIndicatorColor(context)))
        if (tagCloud.isAppHidden) tagItems.add(TagItem().setTextRes(R.string.hidden).setColor(ColorCodes.getAppHiddenIndicatorColor(context)))
        mMagiskHiddenProcesses = tagCloud.magiskHiddenProcesses
        if (tagCloud.isMagiskHideEnabled) tagItems.add(TagItem().setTextRes(R.string.magisk_hide_enabled).setOnClickListener { displayMagiskHideDialog() })
        mMagiskDeniedProcesses = tagCloud.magiskDeniedProcesses
        if (tagCloud.isMagiskDenyListEnabled) tagItems.add(TagItem().setTextRes(R.string.magisk_denylist).setOnClickListener { displayMagiskDenyListDialog() })
        if (tagCloud.canWriteAndExecute) tagItems.add(TagItem().apply {
            setText("WX")
            setColor(ColorCodes.getAppWriteAndExecuteIndicatorColor(context))
            setOnClickListener { ScrollableDialogBuilder(it.context).setTitle("WX").setMessage(R.string.app_can_write_and_execute_in_same_place).enableAnchors().setNegativeButton(R.string.close, null).show() }
        })
        if (tagCloud.bloatwareRemovalType != 0) tagItems.add(TagItem().apply {
            setText("Bloatware")
            setColor(ColorCodes.getBloatwareIndicatorColor(context, tagCloud.bloatwareRemovalType))
            setOnClickListener { BloatwareDetailsDialog.getInstance(mPackageName!!).show(childFragmentManager, BloatwareDetailsDialog.TAG) }
        })
        if (tagCloud.hasKeyStoreItems) {
            val ksTag = TagItem().setTextRes(R.string.keystore).setOnClickListener { SearchableItemsDialogBuilder(it.context, KeyStoreUtils.getKeyStoreFiles(mApplicationInfo!!.uid, mUserId)).setTitle(R.string.keystore).setNegativeButton(R.string.close, null).show() }
            if (tagCloud.hasMasterKeyInKeyStore) ksTag.setColor(ColorCodes.getAppKeystoreIndicatorColor(context))
            tagItems.add(ksTag)
        }
        if (tagCloud.backups?.isNotEmpty() == true) tagItems.add(TagItem().apply {
            setTextRes(R.string.backup)
            setOnClickListener {
                val fragment = BackupRestoreDialogFragment.getInstance(listOf(UserPackagePair(mPackageName!!, mUserId)), BackupRestoreDialogFragment.MODE_RESTORE or BackupRestoreDialogFragment.MODE_DELETE)
                fragment.setOnActionBeginListener { showProgressIndicator(true) }
                fragment.setOnActionCompleteListener { _, _ -> showProgressIndicator(false) }
                fragment.show(parentFragmentManager, BackupRestoreDialogFragment.TAG)
            }
        })
        if (!tagCloud.isBatteryOptimized) {
            val bTag = TagItem().setTextRes(R.string.no_battery_optimization).setColor(ColorCodes.getAppNoBatteryOptimizationIndicatorColor(context))
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER)) bTag.setOnClickListener {
                MaterialAlertDialogBuilder(it.context).setTitle(R.string.battery_optimization).setMessage(R.string.enable_battery_optimization).setNegativeButton(R.string.no, null).setPositiveButton(R.string.yes) { _, _ -> if (DeviceIdleManagerCompat.enableBatteryOptimization(mPackageName)) { UIUtils.displayShortToast(R.string.done); mm.getTagsAlteredLiveData().value = true } else UIUtils.displayShortToast(R.string.failed) }.show()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) bTag.setOnClickListener { ExUtils.exceptionAsIgnored { startActivity(IntentUtils.getBatteryOptSettings(mPackageName!!)) } }
            tagItems.add(bTag)
        }
        if (!tagCloud.sensorsEnabled) tagItems.add(TagItem().setTextRes(R.string.tag_sensors_disabled))
        if (tagCloud.netPolicies > 0) tagItems.add(TagItem().apply {
            setTextRes(R.string.has_net_policy)
            setOnClickListener { SearchableItemsDialogBuilder(it.context, NetworkPolicyManagerCompat.getReadablePolicies(context, tagCloud.netPolicies).values.toTypedArray()).setTitle(R.string.net_policy).setNegativeButton(R.string.ok, null).show() }
        })
        tagCloud.ssaid?.let { s -> tagItems.add(TagItem().apply {
            setTextRes(R.string.ssaid)
            setColor(ColorCodes.getAppSsaidIndicatorColor(context))
            setOnClickListener {
                val dialog = ChangeSsaidDialog.getInstance(mPackageName!!, mApplicationInfo!!.uid, s)
                dialog.setSsaidChangedInterface { newSsaid, success -> displayLongToast(if (success) R.string.restart_to_reflect_changes else R.string.failed_to_change_ssaid); if (success) tagCloud.ssaid = newSsaid }
                dialog.show(childFragmentManager, ChangeSsaidDialog.TAG)
            }
        }) }
        tagCloud.uriGrants?.let { grants -> tagItems.add(TagItem().apply {
            setTextRes(R.string.saf)
            setOnClickListener {
                val names = Array(grants.size) { i -> GrantUriUtils.toLocalisedString(it.context, grants[it].uri) }
                SearchableItemsDialogBuilder(it.context, names).setTitle(R.string.saf).setTextSelectable(true).setListBackgroundColorOdd(ColorCodes.getListItemColor0(mActivity)).setListBackgroundColorEven(ColorCodes.getListItemColor1(mActivity)).setNegativeButton(R.string.close, null).show()
            }
        }) }
        if (tagCloud.usesPlayAppSigning) tagItems.add(TagItem().apply {
            setTextRes(R.string.uses_play_app_signing)
            setColor(ColorCodes.getAppPlayAppSigningIndicatorColor(context))
            setOnClickListener { ScrollableDialogBuilder(mActivity).setTitle(R.string.uses_play_app_signing).setMessage(R.string.uses_play_app_signing_description).setNegativeButton(R.string.close, null).show() }
        })
        tagCloud.xposedModuleInfo?.let { xmi -> tagItems.add(TagItem().apply {
            setText("Xposed")
            setOnClickListener { ScrollableDialogBuilder(it.context).setTitle(R.string.xposed_module_info).setMessage(xmi.toLocalizedString(it.context)).setNegativeButton(R.string.close, null).show() }
        }) }
        tagCloud.staticSharedLibraryNames?.let { names -> tagItems.add(TagItem().apply {
            setTextRes(R.string.static_shared_library)
            setOnClickListener { SearchableMultiChoiceDialogBuilder(it.context, names, names).setTitle(R.string.shared_libs).setPositiveButton(R.string.close, null).setNeutralButton(R.string.uninstall) { _, _, selected ->
                val isSys = ApplicationInfoCompat.isSystemApp(mApplicationInfo!!)
                ScrollableDialogBuilder(mActivity, if (isSys) R.string.uninstall_system_app_message else R.string.uninstall_app_message).setTitle(mAppLabel).setPositiveButton(R.string.uninstall) { _, _, keepData ->
                    if (selected.size == 1) ThreadUtils.postOnBackgroundThread {
                        val inst = PackageInstallerCompat.getNewInstance().apply { setAppLabel(mAppLabel) }
                        val success = inst.uninstall(selected[0], mUserId, false)
                        ThreadUtils.postOnMainThread { if (success) { displayLongToast(R.string.uninstalled_successfully, mAppLabel); mActivity.finish() } else displayLongToast(R.string.failed_to_uninstall, mAppLabel) }
                    } else {
                        val ids = List(selected.size) { mUserId }
                        val item = BatchQueueItem.getBatchOpQueue(BatchOpsManager.OP_UNINSTALL, selected, ids, null)
                        ContextCompat.startForegroundService(mActivity, BatchOpsService.getServiceIntent(mActivity, item))
                    }
                }.setNegativeButton(R.string.cancel, null).show()
            }.show() }
        }) }
        return tagItems
    }

    private fun displayRunningServices(runningServices: List<ActivityManager.RunningServiceInfo>, ctx: Context) {
        showProgressIndicator(true)
        ThreadUtils.postOnBackgroundThread {
            val names = Array(runningServices.size) { i ->
                val info = runningServices[i]
                val desc = SpannableStringBuilder().append(getStyledKeyValue(ctx, R.string.process_name, info.process)).append("\n").append(getStyledKeyValue(ctx, R.string.pid, info.pid.toString()))
                SpannableStringBuilder(info.service.shortClassName).append("\n").append(getSmallerText(desc))
            }
            val logAvail = FeatureController.isLogViewerEnabled() && SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DUMP)
            val tb = DialogTitleBuilder(ctx).setTitle(R.string.running_services)
            if (logAvail) tb.setSubtitle(R.string.running_services_logcat_hint)
            ThreadUtils.postOnMainThread {
                if (isDetached) return@postOnMainThread
                showProgressIndicator(false)
                val b = SearchableItemsDialogBuilder(mActivity, names).setTitle(tb.build())
                if (logAvail) b.setOnItemClickListener { _, which, _ -> mActivity.startActivity(Intent(mActivity.applicationContext, LogViewerActivity::class.java).putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + runningServices[which].pid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) b.setNeutralButton(R.string.force_stop) { _, _ -> ThreadUtils.postOnBackgroundThread { try { PackageManagerCompat.forceStopPackage(mPackageName!!, mUserId) } catch (e: SecurityException) { Log.e(TAG, e); ThreadUtils.postOnMainThread { displayLongToast(R.string.failed_to_stop, mAppLabel) } } } }
                b.setNegativeButton(R.string.close, null).show()
            }
        }
    }

    private fun displayMagiskHideDialog() {
        getMagiskProcessDialog(mMagiskHiddenProcesses) { _, _, mp, isChecked -> ThreadUtils.postOnBackgroundThread {
            mp.isEnabled = isChecked
            if (MagiskHide.apply(mp, true)) {
                ComponentsBlocker.getMutableInstance(mPackageName!!, mUserId).use { it.setMagiskHide(mp) }
                mMainModel?.getTagsAlteredLiveData()?.postValue(true)
            } else {
                mp.isEnabled = !isChecked
                ThreadUtils.postOnMainThread { displayLongToast(if (isChecked) R.string.failed_to_enable_magisk_hide else R.string.failed_to_disable_magisk_hide) }
            }
        } }?.setTitle(R.string.magisk_hide_enabled)?.show()
    }

    private fun displayMagiskDenyListDialog() {
        getMagiskProcessDialog(mMagiskDeniedProcesses) { _, _, mp, isChecked -> ThreadUtils.postOnBackgroundThread {
            mp.isEnabled = isChecked
            if (MagiskDenyList.apply(mp, true)) {
                ComponentsBlocker.getMutableInstance(mPackageName!!, mUserId).use { it.setMagiskDenyList(mp) }
                mMainModel?.getTagsAlteredLiveData()?.postValue(true)
            } else {
                mp.isEnabled = !isChecked
                ThreadUtils.postOnMainThread { displayLongToast(if (isChecked) R.string.failed_to_enable_magisk_deny_list else R.string.failed_to_disable_magisk_deny_list) }
            }
        } }?.setTitle(R.string.magisk_denylist)?.show()
    }

    private fun getMagiskProcessDialog(magiskProcesses: List<MagiskProcess>?, listener: SearchableMultiChoiceDialogBuilder.OnMultiChoiceClickListener<MagiskProcess>): SearchableMultiChoiceDialogBuilder<MagiskProcess>? {
        if (magiskProcesses == null || magiskProcesses.isEmpty()) return null
        val procs = Array(magiskProcesses.size) { i ->
            val mp = magiskProcesses[it]
            val sb = SpannableStringBuilder().apply {
                if (mp.isIsolatedProcess) { append("\n").append(UIUtils.getSecondaryText(mActivity, getString(R.string.isolated))); if (mp.isRunning) append(", ").append(UIUtils.getSecondaryText(mActivity, getString(R.string.running))) }
                else if (mp.isRunning) append("\n").append(UIUtils.getSecondaryText(mActivity, getString(R.string.running)))
            }
            SpannableStringBuilder(mp.name).append(UIUtils.getSmallerText(sb))
        }
        val sel = magiskProcesses.indices.filter { magiskProcesses[it].isEnabled }.toIntArray()
        return SearchableMultiChoiceDialogBuilder(mActivity, magiskProcesses, procs).addSelections(sel).setTextSelectable(true).setOnMultiChoiceClickListener(listener).setNegativeButton(R.string.close, null)
    }

    private fun setupHorizontalActions() {
        mActionsFuture?.cancel(true)
        mActionsFuture = ThreadUtils.postOnBackgroundThread {
            val items = getHorizontalActions()
            ThreadUtils.postOnMainThread {
                if (isDetached) return@postOnMainThread
                mLoadedItemCount++
                if (mLoadedItemCount >= 4) showProgressIndicator(false)
                mHorizontalLayout.removeAllViews()
                items.forEach { mHorizontalLayout.addView(it.toActionButton(mHorizontalLayout.context, mHorizontalLayout)) }
                mHorizontalLayout.getChildAt(0)?.requestFocus()
            }
        }
    }

    private fun setupVerticalView(appInfo: AppInfoViewModel.AppInfo) {
        mListFuture?.cancel(true)
        mListFuture = ThreadUtils.postOnBackgroundThread {
            synchronized(mListItems) {
                mListItems.clear()
                if (!mIsExternalApk) {
                    setPathsAndDirectories(appInfo)
                    setDataUsage(appInfo)
                    if (FeatureController.isUsageAccessEnabled()) setStorageAndCache(appInfo)
                }
                setMoreInfo(appInfo)
                ThreadUtils.postOnMainThread {
                    if (isDetached) return@postOnMainThread
                    mLoadedItemCount++
                    if (mLoadedItemCount >= 4) showProgressIndicator(false)
                    mAdapter.setAdapterList(mListItems)
                }
            }
        }
    }

    private fun setPathsAndDirectories(appInfo: AppInfoViewModel.AppInfo) {
        mListItems.add(ListItem.newGroupStart(getString(R.string.paths_and_directories)))
        appInfo.sourceDir?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.source_dir), it, openAsFolderInFM(requireContext(), File(it))).apply { actionContentDescriptionRes = R.string.open }) }
        appInfo.dataDir?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.data_dir), it, openAsFolderInFM(requireContext(), File(it))).apply { actionContentDescriptionRes = R.string.open }) }
        appInfo.dataDeDir?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.dev_protected_data_dir), it, openAsFolderInFM(requireContext(), File(it))).apply { actionContentDescriptionRes = R.string.open }) }
        appInfo.extDataDirs?.let { dirs ->
            if (dirs.size == 1) mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.external_data_dir), dirs[0], openAsFolderInFM(requireContext(), File(dirs[0]))).apply { actionContentDescriptionRes = R.string.open })
            else dirs.forEachIndexed { i, dir -> mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.external_multiple_data_dir, i), dir, openAsFolderInFM(requireContext(), File(dir))).apply { actionContentDescriptionRes = R.string.open }) }
        }
        appInfo.jniDir?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.native_library_dir), it, openAsFolderInFM(requireContext(), File(it))).apply { actionContentDescriptionRes = R.string.open }) }
    }

    private fun setDataUsage(appInfo: AppInfoViewModel.AppInfo) {
        val du = appInfo.dataUsage ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mUserId != UserHandleHidden.myUserId() && !SelfPermissions.isSystem()) return
        mListItems.add(ListItem.newGroupStart(getString(R.string.data_usage_msg)))
        mListItems.add(ListItem.newInlineItem(getString(R.string.data_transmitted), getReadableSize(du.getTx())))
        mListItems.add(ListItem.newInlineItem(getString(R.string.data_received), getReadableSize(du.getRx())))
    }

    private fun setStorageAndCache(appInfo: AppInfoViewModel.AppInfo) {
        if (AppUsageStatsManager.requireReadPhoneStatePermission()) ThreadUtils.postOnMainThread { mRequestPerm.launch(Manifest.permission.READ_PHONE_STATE) { if (it) mAppInfoModel.loadAppInfo(mPackageInfo!!, mIsExternalApk) } }
        if (!SelfPermissions.checkUsageStatsPermission()) {
            ThreadUtils.postOnMainThread { MaterialAlertDialogBuilder(mActivity).setTitle(R.string.grant_usage_access).setMessage(R.string.grant_usage_acess_message).setPositiveButton(R.string.go) { _, _ -> try { mActivityLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) { if (SelfPermissions.checkUsageStatsPermission()) { FeatureController.getInstance().modifyState(FeatureController.FEAT_USAGE_ACCESS, true); mAppInfoModel.loadAppInfo(mPackageInfo!!, mIsExternalApk) } } } catch (ignore: Exception) {} }.setNegativeButton(R.string.cancel, null).setNeutralButton(R.string.never_ask) { _, _ -> FeatureController.getInstance().modifyState(FeatureController.FEAT_USAGE_ACCESS, false) }.setCancelable(false).show() }
            return
        }
        appInfo.sizeInfo?.let { si ->
            mListItems.add(ListItem.newGroupStart(getString(R.string.storage_and_cache)))
            mListItems.add(ListItem.newInlineItem(getString(R.string.app_size), getReadableSize(si.codeSize)))
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_size), getReadableSize(si.dataSize)))
            mListItems.add(ListItem.newInlineItem(getString(R.string.cache_size), getReadableSize(si.cacheSize)))
            if (si.obbSize != 0L) mListItems.add(ListItem.newInlineItem(getString(R.string.obb_size), getReadableSize(si.obbSize)))
            if (si.mediaSize != 0L) mListItems.add(ListItem.newInlineItem(getString(R.string.media_size), getReadableSize(si.mediaSize)))
            mListItems.add(ListItem.newInlineItem(getString(R.string.total_size), getReadableSize(si.totalSize)))
        }
    }

    private fun setMoreInfo(appInfo: AppInfoViewModel.AppInfo) {
        mListItems.add(ListItem.newGroupStart(getString(R.string.more_info)))
        if (mIsExternalApk && mInstalledPackageInfo != null) {
            mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.installed_version), getString(R.string.version_name_with_code, mInstalledPackageInfo!!.versionName, PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo!!))) { startActivity(AppDetailsActivity.getIntent(mActivity, mPackageName!!, UserHandleHidden.myUserId())) }.apply { actionIconRes = io.github.muntashirakon.ui.R.drawable.ic_information; actionContentDescriptionRes = R.string.app_info })
        }
        val sdk = StringBuilder("${getString(R.string.sdk_max)}${LangUtils.getSeparatorString()}${mApplicationInfo!!.targetSdkVersion}")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) sdk.append(", ${getString(R.string.sdk_min)}${LangUtils.getSeparatorString()}${mApplicationInfo!!.minSdkVersion}")
        mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.sdk), sdk.toString()))
        val flags = mutableListOf<String>()
        if ((mPackageInfo!!.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) flags.add("FLAG_DEBUGGABLE")
        if ((mPackageInfo!!.applicationInfo.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0) flags.add("FLAG_TEST_ONLY")
        if ((mPackageInfo!!.applicationInfo.flags and ApplicationInfo.FLAG_MULTIARCH) != 0) flags.add("FLAG_MULTIARCH")
        if ((mPackageInfo!!.applicationInfo.flags and ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0) flags.add("FLAG_HARDWARE_ACCELERATED")
        if (flags.isNotEmpty()) mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.sdk_flags), flags.joinToString("|")).apply { isMonospace = true })
        if (mIsExternalApk) return
        mListItems.add(ListItem.newRegularItem(getString(R.string.date_installed), getTime(mPackageInfo!!.firstInstallTime)))
        mListItems.add(ListItem.newRegularItem(getString(R.string.date_updated), getTime(mPackageInfo!!.lastUpdateTime)))
        if (mPackageName != mApplicationInfo!!.processName) mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.process_name), mApplicationInfo!!.processName))
        appInfo.installerApp?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.installer_app), it) { displayInstallerDialog(appInfo.installSource!!) }.apply { actionIconRes = R.drawable.ic_information_circle; actionContentDescriptionRes = R.string.more_info }) }
        mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.user_id), String.format(Locale.getDefault(), "%d", mApplicationInfo!!.uid)))
        mPackageInfo!!.sharedUserId?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.shared_user_id), it)) }
        appInfo.primaryCpuAbi?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.primary_abi), it)) }
        appInfo.zygotePreloadName?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.zygote_preload_name), it)) }
        mListItems.add(ListItem.newRegularItem(getString(R.string.hidden_api_enforcement_policy), getHiddenApiEnforcementPolicy(appInfo.hiddenApiEnforcementPolicy)))
        appInfo.seInfo?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.selinux), it)) }
        appInfo.mainActivity?.component?.let { mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.main_activity), it.className) { startActivity(appInfo.mainActivity) }.apply { actionContentDescriptionRes = R.string.open }) }
    }

    private fun getHiddenApiEnforcementPolicy(policy: Int): String {
        return when (policy) {
            ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_DEFAULT -> getString(R.string.hidden_api_enf_default_policy)
            ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_JUST_WARN -> getString(R.string.hidden_api_enf_policy_warn)
            ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_ENABLED -> getString(R.string.hidden_api_enf_policy_dark_grey_and_black)
            ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_BLACK -> getString(R.string.hidden_api_enf_policy_black)
            else -> getString(R.string.hidden_api_enf_policy_none)
        }
    }

    private fun freeze(freeze: Boolean) {
        if (freeze) mMainModel?.loadFreezeType()
        else ThreadUtils.postOnBackgroundThread { try { FreezeUtils.unfreeze(mPackageName!!, mUserId) } catch (th: Throwable) { Log.e(TAG, th); ThreadUtils.postOnMainThread { displayLongToast(R.string.failed_to_unfreeze, mAppLabel) } } }
    }

    private fun showFreezeDialog(freezeType: Int, isCustom: Boolean) {
        val view = View.inflate(mActivity, R.layout.item_checkbox, null)
        val checkBox: MaterialCheckBox = view.findViewById(R.id.checkbox)
        checkBox.setText(R.string.remember_option_for_this_app)
        checkBox.isChecked = isCustom
        FreezeUnfreeze.getFreezeDialog(mActivity, freezeType).setIcon(R.drawable.ic_snowflake).setTitle(R.string.freeze).setView(view).setPositiveButton(R.string.freeze) { _, _, sel -> sel?.let { ThreadUtils.postOnBackgroundThread { try { if (checkBox.isChecked) FreezeUtils.storeFreezeMethod(mPackageName!!, it) else FreezeUtils.deleteFreezeMethod(mPackageName!!); FreezeUtils.freeze(mPackageName!!, mUserId, it) } catch (th: Throwable) { Log.e(TAG, th); ThreadUtils.postOnMainThread { displayLongToast(R.string.failed_to_freeze, mAppLabel) } } } } }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun createFreezeShortcut(isFrozen: Boolean) {
        val flags = (0 until 3).map { 1 shl it }
        SearchableMultiChoiceDialogBuilder(mActivity, flags, R.array.freeze_unfreeze_flags).setTitle(R.string.freeze_unfreeze).setPositiveButton(R.string.create_shortcut) { _, _, sel ->
            var f = 0
            for (s in sel) f = f or s
            val icon = getBitmapFromDrawable(mIconView.drawable)
            val si = FreezeUnfreezeShortcutInfo(mPackageName!!, mUserId, f).apply { name = mAppLabel; this.icon = if (isFrozen) getDimmedBitmap(icon) else icon }
            CreateShortcutDialogFragment.getInstance(si).show(childFragmentManager, CreateShortcutDialogFragment.TAG)
        }.show()
    }

    private fun displayInstallerDialog(isInfo: InstallSourceInfoCompat) {
        val infoList = mutableListOf<CharSequence>()
        val pkgs = mutableListOf<String>()
        fun add(label: CharSequence?, pkg: String?, titleRes: Int) { pkg?.let { p -> infoList.add(SpannableStringBuilder(getSmallerText(getString(titleRes))).append("\n").append(getTitleText(requireContext(), label ?: p)).append("\n").append(p)); pkgs.add(p) } }
        add(isInfo.installingPackageLabel, isInfo.installingPackageName, R.string.installer)
        add(isInfo.initiatingPackageLabel, isInfo.initiatingPackageName, R.string.actual_installer)
        add(isInfo.originatingPackageLabel, isInfo.originatingPackageName, R.string.apk_source)
        SearchableItemsDialogBuilder(requireContext(), infoList).setTitle(R.string.installer).setOnItemClickListener { dialog, which, _ -> startActivity(AppDetailsActivity.getIntent(requireContext(), pkgs[which], mUserId)) }.setNegativeButton(R.string.close, null).show()
    }

    private fun getTime(time: Long): String = DateUtils.formatLongDateTime(requireContext(), time)
    private fun getReadableSize(size: Long): String = Formatter.formatFileSize(mActivity, size)
    private fun showProgressIndicator(show: Boolean) { if (show) mProgressIndicator?.show() else mProgressIndicator?.hide() }
}
