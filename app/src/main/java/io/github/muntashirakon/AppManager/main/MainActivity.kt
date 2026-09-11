// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.collection.ArrayMap
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptDialog
import io.github.muntashirakon.AppManager.apk.list.ListExporter
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.batchops.struct.BatchFreezeOptions
import io.github.muntashirakon.AppManager.batchops.struct.BatchNetPolicyOptions
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions
import io.github.muntashirakon.AppManager.changelog.ChangelogParser
import io.github.muntashirakon.AppManager.changelog.ChangelogRecyclerAdapter
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat
import io.github.muntashirakon.AppManager.debloat.DebloaterActivity
import io.github.muntashirakon.AppManager.cache.CacheCleanerActivity
import io.github.muntashirakon.AppManager.filters.FinderActivity
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.misc.HelpActivity
import io.github.muntashirakon.AppManager.misc.LabsActivity
import io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity
import io.github.muntashirakon.AppManager.profiles.AddToProfileDialogFragment
import io.github.muntashirakon.AppManager.profiles.ProfilesActivity
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.settings.SettingsActivity
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.usage.AppUsageActivity
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.AppPref
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.StoragePermission
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ExpressiveHaptics
import io.github.muntashirakon.AppManager.utils.appearance.ExpressiveMotion
import io.github.muntashirakon.AppManager.view.FabMenu
import io.github.muntashirakon.AppManager.batchops.struct.BatchArchiveOptions
import io.github.muntashirakon.AppManager.batchops.struct.BatchTagOptions
import io.github.muntashirakon.AppManager.batchops.ArchiveHandler
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.dialog.AlertDialogBuilder
import io.github.muntashirakon.dialog.ScrollableDialogBuilder
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.multiselection.MultiSelectionActionsView
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.MultiSelectionView
import io.github.muntashirakon.widget.SwipeRefreshLayout
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*

@AndroidEntryPoint
class MainActivity : BaseActivity(), AdvancedSearchView.OnQueryTextListener,
    SwipeRefreshLayout.OnRefreshListener, MultiSelectionActionsView.OnItemSelectedListener,
    MultiSelectionView.OnSelectionModeChangeListener {

    var viewModel: MainViewModel? = null

    private var mAdapter: MainRecyclerAdapter? = null
    private var mSuggestionsAdapter: SuggestionsAdapter? = null
    private var mSuggestionsStub: ViewStub? = null
    private var mFilterChipsStub: ViewStub? = null
    private var mFilterChipsManager: FilterChipsManager? = null
    private var mSearchView: AdvancedSearchView? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mSwipeRefresh: SwipeRefreshLayout? = null
    private var mMultiSelectionView: MultiSelectionView? = null
    private var mFabMenu: FabMenu? = null
    private var mBatchOpsHandler: MainBatchOpsHandler? = null
    private var mAppUsageMenu: MenuItem? = null

    private val mStoragePermission = StoragePermission.init(this)

    private val mBatchExportRules = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/tab-separated-values")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        if (viewModel == null) return@registerForActivityResult
        val dialogFragment = RulesTypeSelectionDialogFragment()
        val args = Bundle()
        args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT)
        args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri)
        args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, ArrayList(viewModel!!.getSelectedPackages().keys))
        args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersIds())
        dialogFragment.arguments = args
        dialogFragment.show(supportFragmentManager, RulesTypeSelectionDialogFragment.TAG)
    }

    private val mExportAppListCsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        mProgressIndicator!!.show()
        viewModel!!.saveExportedAppList(ListExporter.EXPORT_TYPE_CSV, Paths.get(uri))
    }
    private val mExportAppListJson = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        mProgressIndicator!!.show()
        viewModel!!.saveExportedAppList(ListExporter.EXPORT_TYPE_JSON, Paths.get(uri))
    }
    private val mExportAppListXml = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        mProgressIndicator!!.show()
        viewModel!!.saveExportedAppList(ListExporter.EXPORT_TYPE_XML, Paths.get(uri))
    }
    private val mExportAppListMarkdown = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        mProgressIndicator!!.show()
        viewModel!!.saveExportedAppList(ListExporter.EXPORT_TYPE_MARKDOWN, Paths.get(uri))
    }

    private val mBatchOpsBroadCastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showProgressIndicator(false)
        }
    }

    private val mOnBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mAdapter != null && mMultiSelectionView != null && mAdapter!!.isInSelectionMode) {
                mMultiSelectionView!!.cancel()
                return
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true)
            actionBar.displayOptions = 0
            val searchView = AdvancedSearchView(actionBar.themedContext)
            searchView.id = R.id.action_search
            searchView.setOnQueryTextListener(this)
            val layoutParams = ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.gravity = Gravity.CENTER
            actionBar.setCustomView(searchView, layoutParams)
            mSearchView = searchView
            mSearchView!!.setIconifiedByDefault(false)
            mSearchView!!.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    UiUtils.hideKeyboard(v)
                }
            }
            val marketUri = intent.data
            if (marketUri != null && "market" == marketUri.scheme && "search" == marketUri.host) {
                val query = marketUri.getQueryParameter("q")
                if (query != null) mSearchView!!.setQuery(query, true)
            }
        }

        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE
        val recyclerView: RecyclerView = findViewById(R.id.item_list)
        recyclerView.requestFocus()
        mSwipeRefresh = findViewById(R.id.swipe_refresh)
        mSwipeRefresh!!.setOnRefreshListener(this)

        mAdapter = MainRecyclerAdapter(this)
        mAdapter!!.setHasStableIds(true)
        recyclerView.layoutManager = UIUtils.getGridLayoutAt450Dp(this)

        // Enhanced Material You item animations with expressive motion
        recyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            addDuration = 350
            removeDuration = 350
            moveDuration = 350
            changeDuration = 200
        }
        // Edge-to-edge Material You overscroll effect (stretch on Android 12+)
        recyclerView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        // Smooth CoordinatorLayout integration
        recyclerView.isNestedScrollingEnabled = true

        recyclerView.adapter = mAdapter
        
        // Initialize Material You 2026 Expressive Haptics
        ExpressiveHaptics.initialize(this)
        
        mMultiSelectionView = findViewById(R.id.selection_view)
        mMultiSelectionView!!.setOnItemSelectedListener(this)
        mMultiSelectionView!!.setOnSelectionModeChangeListener(this)
        mMultiSelectionView!!.adapter = mAdapter
        mMultiSelectionView!!.updateCounter(true)
        mBatchOpsHandler = MainBatchOpsHandler(mMultiSelectionView!!, viewModel!!)
        mMultiSelectionView!!.setOnSelectionChangeListener(mBatchOpsHandler)

        mFabMenu = findViewById(R.id.fab_menu)
        setupFabMenu()

        if (SHOW_DISCLAIMER && AppPref.getBoolean(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL)) {
            SHOW_DISCLAIMER = false
            val view = View.inflate(this, R.layout.dialog_disclaimer, null)
            MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.disclaimer_agree) { _, _ ->
                    if (view.findViewById<MaterialCheckBox>(R.id.agree_forever).isChecked) {
                        AppPref.set(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL, false)
                    }
                    displayChangelogIfRequired()
                }
                .setNegativeButton(R.string.disclaimer_exit) { _, _ -> finishAndRemoveTask() }
                .show()
        } else {
            displayChangelogIfRequired()
        }
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel!!.getApplicationItems().collect { applicationItems ->
            adapter.submitList(applicationItems)
        }
    }
}
            showProgressIndicator(false)
        }
        viewModel!!.getSuggestions().observe(this) { items ->
            if (items == null || items.isEmpty()) {
                mSuggestionsStub?.visibility = View.GONE
                return@observe
            }
            if (mSuggestionsAdapter == null) {
                if (mSuggestionsStub == null) mSuggestionsStub = findViewById(R.id.suggestions_stub)
                val suggestionsView = mSuggestionsStub!!.inflate()
                val suggestionsList: RecyclerView = suggestionsView.findViewById(R.id.suggestions_list)
                mSuggestionsAdapter = SuggestionsAdapter(items) { item ->
                    showArchiveDialog(Collections.singletonList(UserPackagePair(item.packageName, item.userIds[0])))
                }
                suggestionsList.adapter = mSuggestionsAdapter
            } else {
                mSuggestionsAdapter!!.updateItems(items)
                mSuggestionsStub!!.visibility = View.VISIBLE
            }
        }
        
        // Initialize filter chips
        mFilterChipsStub = findViewById(R.id.filter_chips_stub)
        mFilterChipsManager = FilterChipsManager(this, mFilterChipsStub, object : FilterChipsManager.OnFilterChangeListener {
            override fun onFilterChanged(filters: Set<Int>) {
                viewModel?.applyQuickFilters(filters)
            }
        })
        mFilterChipsManager?.initialize()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel!!.getFilterFlags().collect { flags ->
                    mFilterChipsManager?.restoreFromBitmask(flags)
                }
            }
        }

        viewModel!!.getOperationStatus().observe(this) { status ->
            mProgressIndicator!!.hide()
            if (status) UIUtils.displayShortToast(R.string.done)
            else UIUtils.displayLongToast(R.string.failed)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_main_actions, menu)
        mAppUsageMenu = menu.findItem(R.id.action_app_usage)
        val apkUpdaterMenu = menu.findItem(R.id.action_apk_updater)
        try {
            if (!packageManager.getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled) throw PackageManager.NameNotFoundException()
            apkUpdaterMenu.isVisible = true
        } catch (e: PackageManager.NameNotFoundException) {
            apkUpdaterMenu.isVisible = false
        }
        val finderMenu = menu.findItem(R.id.action_finder)
        finderMenu.isVisible = BuildConfig.DEBUG
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        mAppUsageMenu!!.isVisible = FeatureController.isUsageAccessEnabled()
        return true
    }

    @SuppressLint("InflateParams")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_instructions) {
            val helpIntent = Intent(this, HelpActivity::class.java)
            helpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(helpIntent)
        } else if (id == R.id.action_list_options) {
            val listOptions = MainListOptions()
            listOptions.setListOptionActions(viewModel)
            listOptions.show(supportFragmentManager, MainListOptions.TAG)
        } else if (id == R.id.action_refresh) {
            if (viewModel != null) {
                showProgressIndicator(true)
                viewModel!!.loadApplicationItems()
            }
        } else if (id == R.id.action_settings) {
            val settingsIntent = SettingsActivity.getSettingsIntent(this)
            startActivity(settingsIntent)
        } else if (id == R.id.action_app_usage) {
            val usageIntent = Intent(this, AppUsageActivity::class.java)
            startActivity(usageIntent)
        } else if (id == R.id.action_one_click_ops) {
            val onClickOpsIntent = Intent(this, OneClickOpsActivity::class.java)
            startActivity(onClickOpsIntent)
        } else if (id == R.id.action_cache_cleaner) {
            val cacheCleanerIntent = Intent(this, CacheCleanerActivity::class.java)
            startActivity(cacheCleanerIntent)
        } else if (id == R.id.action_finder) {
            val intent = Intent(this, FinderActivity::class.java)
            startActivity(intent)
        } else if (id == R.id.action_apk_updater) {
            try {
                if (!packageManager.getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled) throw PackageManager.NameNotFoundException()
                val intent = Intent().apply {
                    setClassName(PACKAGE_NAME_APK_UPDATER, ACTIVITY_NAME_APK_UPDATER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (ignored: Exception) {
            }
        } else if (id == R.id.action_running_apps) {
            val runningAppsIntent = Intent(this, RunningAppsActivity::class.java)
            startActivity(runningAppsIntent)
        } else if (id == R.id.action_profiles) {
            val profilesIntent = Intent(this, ProfilesActivity::class.java)
            startActivity(profilesIntent)
        } else if (id == R.id.action_labs) {
            val intent = Intent(applicationContext, LabsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else if (id == R.id.action_debloater) {
            val intent = Intent(applicationContext, DebloaterActivity::class.java)
            startActivity(intent)
        } else if (id == R.id.action_archived_apps) {
            val archivedAppsIntent = Intent(this, ArchivedAppsActivity::class.java)
            startActivity(archivedAppsIntent)
        } else return super.onOptionsItemSelected(item)
        return true
    }

    private fun setupFabMenu() {
        mFabMenu?.apply {
            setMenuItems(listOf(
                FabMenu.MenuItem(1, R.drawable.ic_archive, getString(R.string.fab_menu_archive)),
                FabMenu.MenuItem(2, R.drawable.ic_clean, getString(R.string.fab_menu_clean_cache)),
                FabMenu.MenuItem(3, R.drawable.ic_backup_restore, getString(R.string.fab_menu_backup)),
                FabMenu.MenuItem(4, R.drawable.ic_snowflake, getString(R.string.fab_menu_freeze)),
                FabMenu.MenuItem(5, R.drawable.ic_settings, getString(R.string.fab_menu_settings))
            ))
            setOnMenuItemClickListener { id ->
                when (id) {
                    1 -> startBatchArchiving()
                    2 -> {
                        val cacheCleanerIntent = Intent(this@MainActivity, CacheCleanerActivity::class.java)
                        startActivity(cacheCleanerIntent)
                    }
                    3 -> handleBatchOp(BatchOpsManager.OP_BACKUP)
                    4 -> showFreezeUnfreezeDialog(Prefs.Blocking.getDefaultFreezingMethod())
                    5 -> {
                        val settingsIntent = SettingsActivity.getSettingsIntent(this@MainActivity)
                        startActivity(settingsIntent)
                    }
                }
            }
        }
    }

    private fun startBatchArchiving() {
        // Sort by size
        viewModel?.setSortBy(MainListOptions.SORT_BY_TOTAL_SIZE)
        
        // Enter selection mode
        mAdapter?.setInSelectionMode(true)
        mAdapter?.notifySelectionChange()
        
        // Show instruction snackbar with haptics
        ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_SUCCESS)
        
        Snackbar.make(findViewById(android.R.id.content), R.string.batch_archive_instruction, Snackbar.LENGTH_LONG)
            .setAction(R.string.archive) {
                showArchiveDialog(ArrayList(viewModel!!.getSelectedPackageUserPairs()))
            }.show()
    }

    override fun onSelectionModeEnabled() {
        mOnBackPressedCallback.isEnabled = true
    }

    override fun onSelectionModeDisabled() {
        mOnBackPressedCallback.isEnabled = false
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_backup) {
            if (viewModel != null) {
                val fragment = BackupRestoreDialogFragment.getInstance(viewModel!!.getSelectedPackageUserPairs())
                fragment.setOnActionBeginListener { showProgressIndicator(true) }
                fragment.setOnActionCompleteListener { _, _ -> showProgressIndicator(false) }
                fragment.show(supportFragmentManager, BackupRestoreDialogFragment.TAG)
            }
        } else if (id == R.id.action_save_apk) {
            mStoragePermission.request { granted ->
                if (granted) handleBatchOp(BatchOpsManager.OP_BACKUP_APK)
            }
        } else if (id == R.id.action_block_unblock_trackers) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.block_unblock_trackers)
                .setMessage(R.string.choose_what_to_do)
                .setPositiveButton(R.string.block) { _, _ ->
                    handleBatchOp(BatchOpsManager.OP_BLOCK_TRACKERS)
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.unblock) { _, _ ->
                    handleBatchOp(BatchOpsManager.OP_UNBLOCK_TRACKERS)
                }
                .show()
        } else if (id == R.id.action_clear_data_cache) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear)
                .setMessage(R.string.choose_what_to_do)
                .setPositiveButton(R.string.clear_cache) { _, _ ->
                    handleBatchOp(BatchOpsManager.OP_CLEAR_CACHE)
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.clear_data) { _, _ ->
                    handleBatchOp(BatchOpsManager.OP_CLEAR_DATA)
                }
                .show()
        } else if (id == R.id.action_archive) {
            showArchiveDialog(ArrayList(viewModel!!.getSelectedPackageUserPairs()))
        } else if (id == R.id.action_edit_tags) {
            showEditTagsDialog(ArrayList(viewModel!!.getSelectedPackageUserPairs()))
        } else if (id == R.id.action_freeze_unfreeze) {
            showFreezeUnfreezeDialog(Prefs.Blocking.getDefaultFreezingMethod())
        } else if (id == R.id.action_disable_background) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.disable_background_run_description)
                .setPositiveButton(R.string.yes) { _, _ ->
                    handleBatchOp(BatchOpsManager.OP_DISABLE_BACKGROUND)
                }
                .setNegativeButton(R.string.no, null)
                .show()
        } else if (id == R.id.action_net_policy) {
            val netPolicyMap = NetworkPolicyManagerCompat.getAllReadablePolicies(this)
            val policies = arrayOfNulls<Int>(netPolicyMap.size)
            val policyStrings = arrayOfNulls<String>(netPolicyMap.size)
            val applicationItems = viewModel!!.getSelectedPackages().values
            val it = applicationItems.iterator()
            val selectedPolicies = if (applicationItems.size == 1 && it.hasNext()) {
                NetworkPolicyManagerCompat.getUidPolicy(it.next().uid)
            } else 0
            for (i in 0 until netPolicyMap.size) {
                policies[i] = netPolicyMap.keyAt(i)
                policyStrings[i] = netPolicyMap.valueAt(i)
            }
            SearchableFlagsDialogBuilder(this, policies, policyStrings, selectedPolicies)
                .setTitle(R.string.net_policy)
                .showSelectAll(false)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply) { _, _, selections ->
                    var flags = 0
                    for (flag in selections) {
                        flags = flags or flag
                    }
                    val options = BatchNetPolicyOptions(flags)
                    handleBatchOp(BatchOpsManager.OP_NET_POLICY, options)
                }
                .show()
        } else if (id == R.id.action_optimize) {
            val dialog = DexOptDialog.getInstance(viewModel!!.getSelectedPackages().keys.toTypedArray())
            dialog.show(supportFragmentManager, DexOptDialog.TAG)
        } else if (id == R.id.action_export_blocking_rules) {
            val fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(this, System.currentTimeMillis()) + ".am.tsv"\nmBatchExportRules.launch(fileName)
        } else if (id == R.id.action_export_app_list) {
            val exportTypes = listOf(
                ListExporter.EXPORT_TYPE_CSV,
                ListExporter.EXPORT_TYPE_JSON,
                ListExporter.EXPORT_TYPE_XML,
                ListExporter.EXPORT_TYPE_MARKDOWN
            )
            SearchableSingleChoiceDialogBuilder(this, exportTypes, R.array.export_app_list_options)
                .setTitle(R.string.export_app_list_select_format)
                .setOnSingleChoiceClickListener { _, _, item, isChecked ->
                    if (!isChecked) return@setOnSingleChoiceClickListener
                    val filename = "app_manager_app_list-" + DateUtils.formatLongDateTime(this, System.currentTimeMillis()) + ".am"\nwhen (item) {
                        ListExporter.EXPORT_TYPE_CSV -> mExportAppListCsv.launch("$filename.csv")
                        ListExporter.EXPORT_TYPE_JSON -> mExportAppListJson.launch("$filename.json")
                        ListExporter.EXPORT_TYPE_XML -> mExportAppListXml.launch("$filename.xml")
                        ListExporter.EXPORT_TYPE_MARKDOWN -> mExportAppListMarkdown.launch("$filename.md")
                    }
                }
                .setNegativeButton(R.string.close, null)
                .show()
        } else if (id == R.id.action_force_stop) {
            handleBatchOp(BatchOpsManager.OP_FORCE_STOP)
        } else if (id == R.id.action_uninstall) {
            handleBatchOpWithWarning(BatchOpsManager.OP_UNINSTALL)
        } else if (id == R.id.action_add_to_profile) {
            val dialog = AddToProfileDialogFragment.getInstance(viewModel!!.getSelectedPackages().keys.toTypedArray())
            dialog.show(supportFragmentManager, AddToProfileDialogFragment.TAG)
        } else if (id == R.id.action_archive) {
            handleBatchOpWithWarning(BatchOpsManager.OP_ARCHIVE)
        } else {
            return false
        }
        return true
    }

    override fun onRefresh() {
        showProgressIndicator(true)
        viewModel?.loadApplicationItems()
        mSwipeRefresh!!.isRefreshing = false
    }

    override fun onStart() {
        super.onStart()

        if (viewModel != null && mSearchView != null && !TextUtils.isEmpty(viewModel!!.getSearchQuery())) {
            if (mSearchView!!.isIconified) mSearchView!!.isIconified = false
            mSearchView!!.setQuery(viewModel!!.getSearchQuery(), false)
        }
        mAppUsageMenu?.isVisible = FeatureController.isUsageAccessEnabled()
        if (!Prefs.BackupRestore.backupDirectoryExists()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_volume)
                .setMessage(R.string.backup_volume_unavailable_warning)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.change_backup_volume) { _, _ ->
                    val intent = SettingsActivity.getSettingsIntent(this, "backup_restore_prefs", "backup_volume")
                    startActivity(intent)
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel?.onResume()
        if (mAdapter != null && mBatchOpsHandler != null && mAdapter!!.isInSelectionMode) {
            mBatchOpsHandler!!.updateConstraints()
            mMultiSelectionView!!.updateCounter(false)
        }
        ContextCompat.registerReceiver(this, mBatchOpsBroadCastReceiver,
            IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mBatchOpsBroadCastReceiver)
    }

    private fun displayChangelogIfRequired() {
        if (!AppPref.getBoolean(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL)) return
        if (FundingCampaignChecker.campaignRunning()) {
            ScrollableDialogBuilder(this)
                .setMessage(R.string.funding_campaign_dialog_message)
                .enableAnchors()
                .show()
        }
        Snackbar.make(findViewById(android.R.id.content), R.string.view_changelog, 3 * 60 * 1000)
            .setAction(R.string.ok) {
                val lastVersion = AppPref.getLong(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG)
                AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL, false)
                AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG, BuildConfig.VERSION_CODE.toLong())
                viewModel!!.executor.submit {
                    val changelog: ChangelogParser = try {
                        ChangelogParser(application, R.raw.changelog, lastVersion)
                    } catch (e: IOException) {
                        return@submit
                    } catch (e: XmlPullParserException) {
                        return@submit
                    }
                    runOnUiThread {
                        val view = View.inflate(this, R.layout.dialog_whats_new, null)
                        val recyclerView: RecyclerView = view.findViewById(android.R.id.list)
                        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
                        val adapter = ChangelogRecyclerAdapter()
                        recyclerView.adapter = adapter
                        adapter.setAdapterList(changelog.changelogItems)
                        AlertDialogBuilder(this, true)
                            .setTitle(R.string.changelog)
                            .setView(recyclerView)
                            .show()
                    }
                }
            }.show()
    }

    private fun showFreezeUnfreezeDialog(freezeType: Int) {
        val view = View.inflate(this, R.layout.item_checkbox, null)
        val checkBox: MaterialCheckBox = view.findViewById(R.id.checkbox)
        checkBox.setText(R.string.freeze_prefer_per_app_option)
        FreezeUnfreeze.getFreezeDialog(this, freezeType)
            .setIcon(R.drawable.ic_snowflake)
            .setTitle(R.string.freeze_unfreeze)
            .setView(view)
            .setPositiveButton(R.string.freeze) { _, _, selectedItem ->
                if (selectedItem == null) return@setPositiveButton
                val options = BatchFreezeOptions(selectedItem, checkBox.isChecked)
                handleBatchOp(BatchOpsManager.OP_ADVANCED_FREEZE, options)
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.unfreeze) { _, _ ->
                handleBatchOp(BatchOpsManager.OP_UNFREEZE)
            }
            .show()
    }

    private fun showArchiveDialog(pairs: List<UserPackagePair>) {
        val methods = arrayOf(
            getString(R.string.archive_method_system),
            getString(R.string.archive_method_shizuku),
            getString(R.string.archive_method_root),
            getString(R.string.archive_method_standard)
        )
        var selectedMethod = 0
        val view = View.inflate(this, R.layout.dialog_edit_tags, null)
        val input: com.google.android.material.textfield.TextInputEditText = view.findViewById(R.id.tags_input)
        input.setHint(R.string.archive_tags_hint)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_archive_method)
            .setView(view)
            .setSingleChoiceItems(methods, selectedMethod) { _, which ->
                selectedMethod = which
                ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_LIGHT)
            }
            .setPositiveButton(R.string.archive) { _, _ ->
                val tags = input.text.toString().takeIf { it.isNotBlank() }
                val options = BatchArchiveOptions(selectedMethod, tags)
                handleBatchOp(BatchOpsManager.OP_ARCHIVE, options, pairs)
                ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_SUCCESS)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditTagsDialog(pairs: List<UserPackagePair>) {
        val view = View.inflate(this, R.layout.dialog_edit_tags, null)
        val input: com.google.android.material.textfield.TextInputEditText = view.findViewById(R.id.tags_input)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.group)
            .setView(view)
            .setPositiveButton(R.string.apply) { _, _ ->
                val tags = input.text.toString()
                val options = BatchTagOptions(tags)
                handleBatchOp(BatchOpsManager.OP_EDIT_TAGS, options, pairs)
                ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_SUCCESS)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleBatchOp(op: Int) {
        handleBatchOp(op, null)
    }

    private fun handleBatchOp(op: Int, options: IBatchOpOptions?) {
        handleBatchOp(op, options, null)
    }

    private fun handleBatchOp(op: Int, options: IBatchOpOptions?, pairs: List<UserPackagePair>?) {
        handleBatchOp(op, options, pairs, null)
    }

    private fun handleBatchOp(op: Int, options: IBatchOpOptions?, pairs: List<UserPackagePair>?, extra: String?) {
        if (viewModel == null) return
        showProgressIndicator(true)
        val targets = pairs ?: ArrayList(viewModel!!.getSelectedPackageUserPairs())
        val input = BatchOpsManager.Result(targets)
        val item = BatchQueueItem.getBatchOpQueue(op, input.failedPackages, input.associatedUsers, options)
        val intent = BatchOpsService.getServiceIntent(this, item)
        if (extra != null) {
            intent.putExtra("extra", extra)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun handleBatchOpWithWarning(op: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.are_you_sure)
            .setMessage(R.string.this_action_cannot_be_undone)
            .setPositiveButton(R.string.yes) { _, _ -> handleBatchOp(op) }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    fun showProgressIndicator(show: Boolean) {
        if (show) mProgressIndicator!!.show()
        else mProgressIndicator!!.hide()
    }

    override fun onQueryTextChange(searchQuery: String, type: Int): Boolean {
        viewModel?.setSearchQuery(searchQuery, type)
        return true
    }

    override fun onQueryTextSubmit(query: String, type: Int): Boolean = false

    companion object {
        private const val PACKAGE_NAME_APK_UPDATER = "com.apkupdater"\nprivate const val ACTIVITY_NAME_APK_UPDATER = "com.apkupdater.activity.MainActivity"\nprivate var SHOW_DISCLAIMER = true

        const val ACTION_STOP_RECORDING_FROM_NOTIFICATION = BuildConfig.APPLICATION_ID + ".action.STOP_RECORDING_FROM_NOTIFICATION"
    }
}
