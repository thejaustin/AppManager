// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.RemoteException
import android.text.TextUtils
import android.util.Pair
import androidx.annotation.AnyThread
import androidx.annotation.GuardedBy
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.muntashirakon.AppManager.apk.list.ListExporter
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.App
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.filters.FilterItem
import io.github.muntashirakon.AppManager.filters.options.FilterOption
import io.github.muntashirakon.AppManager.filters.options.PackageNameOption
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.misc.ListOptions
import io.github.muntashirakon.AppManager.profiles.ProfileManager
import io.github.muntashirakon.AppManager.profiles.struct.AppsFilterProfile
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo
import io.github.muntashirakon.AppManager.usage.TimeInterval
import io.github.muntashirakon.AppManager.usage.UsageUtils
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.AppExecutor
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.io.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val mAppDb: AppDb
) : AndroidViewModel(application), ListOptions.ListOptionActions {
    private val mPackageManager: PackageManager = application.packageManager
    private val mPackageObserver: PackageIntentReceiver
    @MainListOptions.SortOrder
    private var mSortBy: Int
    private var mReverseSort: Boolean
    @MainListOptions.Filter
    private var mFilterFlags: Int
    private var mFilterProfileName: String?
    private var mSelectedUsers: IntArray? = null
    private var mSearchQuery: String? = null
    @AdvancedSearchView.SearchType
    private var mSearchType: Int = AdvancedSearchView.SEARCH_TYPE_CONTAINS
    private var mFilterResult: Job? = null
    private val mSelectedPackageApplicationItemMap: MutableMap<String, ApplicationItem> = Collections.synchronizedMap(LinkedHashMap())
    val executor: ExecutorService = AppExecutor.getExecutor()

    private val mOperationStatus = MutableLiveData<Boolean>()
    private val mApplicationItemsState = MutableStateFlow<List<ApplicationItem>>(emptyList())
    private val mSuggestionsState = MutableStateFlow<List<ApplicationItem>>(emptyList())
    private val mFilterFlagsState = MutableStateFlow(Prefs.MainPage.getFilters())
    private val mApplicationItems: MutableList<ApplicationItem> = ArrayList()

    private val mCollator: Collator = Collator.getInstance()

    @Volatile
    private var mCachedUsageStats: Map<String, PackageUsageInfo>? = null
    @Volatile
    private var mUsageStatsCacheTimestamp: Long = 0
    private val mUsageCacheLock = Any()

    private val mAppListCache: AppListCache

    init {
        Log.d("MVM", "New instance created")
        mPackageObserver = PackageIntentReceiver(this)
        mSortBy = Prefs.MainPage.getSortOrder()
        mReverseSort = Prefs.MainPage.isReverseSort()
        mFilterFlags = Prefs.MainPage.getFilters()
        mFilterProfileName = Prefs.MainPage.getFilteredProfileName()
        if ("" == mFilterProfileName) mFilterProfileName = null
        mAppListCache = AppListCache(application)
    }

    val applicationItemCount: Int
        get() = mApplicationItems.size

    fun getApplicationItems(): StateFlow<List<ApplicationItem>> = mApplicationItemsState.asStateFlow()

    fun getOperationStatus(): LiveData<Boolean> = mOperationStatus

    fun getSuggestions(): StateFlow<List<ApplicationItem>> = mSuggestionsState.asStateFlow()

    fun getFilterFlags(): StateFlow<Int> = mFilterFlagsState.asStateFlow()

    fun deselect(item: ApplicationItem): ApplicationItem {
        synchronized(mApplicationItems) {
            val i = mApplicationItems.indexOf(item)
            if (i == -1) return item
            val updatedItem = mApplicationItems[i]
            mSelectedPackageApplicationItemMap.remove(updatedItem.packageName)
            updatedItem.isSelected = false
            mApplicationItems[i] = updatedItem
            return updatedItem
        }
    }

    fun select(item: ApplicationItem): ApplicationItem {
        synchronized(mApplicationItems) {
            val i = mApplicationItems.indexOf(item)
            if (i == -1) return item
            val updatedItem = mApplicationItems[i]
            mSelectedPackageApplicationItemMap.remove(updatedItem.packageName)
            mSelectedPackageApplicationItemMap[updatedItem.packageName] = updatedItem
            updatedItem.isSelected = true
            mApplicationItems[i] = updatedItem
            return updatedItem
        }
    }

    fun cancelSelection() {
        synchronized(mApplicationItems) {
            for (item in selectedApplicationItems) {
                val i = mApplicationItems.indexOf(item)
                if (i != -1) {
                    mApplicationItems[i].isSelected = false
                }
            }
            mSelectedPackageApplicationItemMap.clear()
        }
    }

    fun getLastSelectedPackage(): ApplicationItem? {
        var lastItem: ApplicationItem? = null
        val it = mSelectedPackageApplicationItemMap.values.iterator()
        while (it.hasNext()) {
            lastItem = it.next()
        }
        return lastItem
    }

    fun getSelectedPackages(): Map<String, ApplicationItem> = mSelectedPackageApplicationItemMap

    fun getSelectedPackageUserPairs(): ArrayList<UserPackagePair> {
        val userPackagePairs = ArrayList<UserPackagePair>()
        val myUserId = UserHandleHidden.myUserId()
        val userIds = Users.getUsersIds()
        for (packageName in mSelectedPackageApplicationItemMap.keys) {
            val item = mSelectedPackageApplicationItemMap[packageName]!!
            val itemUserIds = item.userIds
            if (itemUserIds.isEmpty()) {
                userPackagePairs.add(UserPackagePair(packageName, myUserId))
            } else {
                for (userHandle in itemUserIds) {
                    if (!ArrayUtils.contains(userIds, userHandle)) continue
                    userPackagePairs.add(UserPackagePair(packageName, userHandle))
                }
            }
        }
        return userPackagePairs
    }

    val selectedApplicationItems: Collection<ApplicationItem>
        get() = mSelectedPackageApplicationItemMap.values

    fun getSearchQuery(): String? = mSearchQuery

    fun setSearchQuery(searchQuery: String?, searchType: Int) {
        this.mSearchQuery = if (searchType != AdvancedSearchView.SEARCH_TYPE_REGEX) searchQuery?.lowercase(Locale.ROOT) else searchQuery
        this.mSearchType = searchType
        cancelIfRunning()
        mFilterResult = viewModelScope.launch(Dispatchers.IO) { filterItemsByFlags() }
    }

    override fun getSortBy(): Int = mSortBy

    override fun setReverseSort(reverseSort: Boolean) {
        cancelIfRunning()
        mFilterResult = viewModelScope.launch(Dispatchers.IO) {
            sortApplicationList(mSortBy, mReverseSort)
            filterItemsByFlags()
        }
        mReverseSort = reverseSort
        Prefs.MainPage.setReverseSort(mReverseSort)
    }

    override fun isReverseSort(): Boolean = mReverseSort

    override fun setSortBy(sortBy: Int) {
        if (mSortBy != sortBy) {
            cancelIfRunning()
            mFilterResult = viewModelScope.launch(Dispatchers.IO) {
                sortApplicationList(sortBy, mReverseSort)
                filterItemsByFlags()
            }
        }
        mSortBy = sortBy
        Prefs.MainPage.setSortOrder(mSortBy)
    }

    override fun hasFilterFlag(flag: Int): Boolean = (mFilterFlags and flag) != 0

    override fun addFilterFlag(filterFlag: Int) {
        mFilterFlags = mFilterFlags or filterFlag
        Prefs.MainPage.setFilters(mFilterFlags)
        mFilterFlagsState.value = mFilterFlags
        cancelIfRunning()
        mFilterResult = viewModelScope.launch(Dispatchers.IO) { filterItemsByFlags() }
    }

    override fun removeFilterFlag(filterFlag: Int) {
        mFilterFlags = mFilterFlags and filterFlag.inv()
        Prefs.MainPage.setFilters(mFilterFlags)
        mFilterFlagsState.value = mFilterFlags
        cancelIfRunning()
        mFilterResult = viewModelScope.launch(Dispatchers.IO) { filterItemsByFlags() }
    }

    fun setFilterProfileName(filterProfileName: String?) {
        if (mFilterProfileName == null) {
            if (filterProfileName == null) return
        } else if (mFilterProfileName == filterProfileName) return
        mFilterProfileName = filterProfileName
        Prefs.MainPage.setFilteredProfileName(filterProfileName)
        cancelIfRunning()
        mFilterResult = viewModelScope.launch(Dispatchers.IO) { filterItemsByFlags() }
    }

    fun getFilterProfileName(): String? = mFilterProfileName

    fun setSelectedUsers(selectedUsers: IntArray?) {
        if (selectedUsers == null) {
            if (mSelectedUsers == null) return
        } else if (mSelectedUsers != null) {
            if (mSelectedUsers!!.size == selectedUsers.size) {
                var differs = false
                for (user in selectedUsers) {
                    if (!ArrayUtils.contains(mSelectedUsers, user)) {
                        differs = true
                        break
                    }
                }
                if (!differs) return
            }
        }
        mSelectedUsers = selectedUsers
        cancelIfRunning()
        mFilterResult = viewModelScope.launch(Dispatchers.IO) { filterItemsByFlags() }
    }

    fun getSelectedUsers(): IntArray? = mSelectedUsers

    /**
     * Apply quick filters from filter chips UI
     * This replaces the current filter flags with the new set
     */
    fun applyQuickFilters(filters: Set<Int>) {
        var newFilterFlags = MainListOptions.FILTER_NO_FILTER
        
        for (filter in filters) {
            newFilterFlags = newFilterFlags or filter
        }
        
        // Only update if filters changed
        if (mFilterFlags != newFilterFlags) {
            mFilterFlags = newFilterFlags
            Prefs.MainPage.setFilters(mFilterFlags)
            mFilterFlagsState.value = mFilterFlags
            cancelIfRunning()
            mFilterResult = viewModelScope.launch(Dispatchers.IO) { filterItemsByFlags() }
        }
    }

    @AnyThread
    fun onResume() {
        if ((mFilterFlags and MainListOptions.FILTER_RUNNING_APPS) != 0) {
            cancelIfRunning()
            mFilterResult = viewModelScope.launch(Dispatchers.IO) { filterItemsByFlags() }
        }
    }

    fun saveExportedAppList(exportType: Int, path: Path) {
        executor.submit {
            try {
                BufferedWriter(OutputStreamWriter(path.openOutputStream(), StandardCharsets.UTF_8)).use { writer ->
                    val packageInfoList = ArrayList<PackageInfo>()
                    for (packageName in getSelectedPackages().keys) {
                        val item = getSelectedPackages()[packageName]!!
                        val userIds = item.userIds
                        for (userId in userIds) {
                            packageInfoList.add(PackageManagerCompat.getPackageInfo(packageName,
                                PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId))
                            break
                        }
                    }
                    ListExporter.export(getApplication(), writer, exportType, packageInfoList)
                    mOperationStatus.postValue(true)
                }
            } catch (e: Exception) {
                Log.e("MVM", "Failed to export app list", e)
                mOperationStatus.postValue(false)
            }
        }
    }

    fun loadApplicationItems() {
        loadApplicationItems(false)
    }

    fun loadApplicationItems(forceRefresh: Boolean) {
        cancelIfRunning()
        mFilterResult = viewModelScope.launch(Dispatchers.IO) {
            var updatedApplicationItems: List<ApplicationItem>? = null

            if (!forceRefresh && mAppListCache.cacheExists()) {
                val startTime = System.currentTimeMillis()
                updatedApplicationItems = mAppListCache.loadFromCache()
                if (updatedApplicationItems != null) {
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d("MVM", "Loaded ${updatedApplicationItems.size} apps from cache in ${loadTime}ms (FAST PATH)")
                }
            }

            if (updatedApplicationItems == null) {
                val startTime = System.currentTimeMillis()
                updatedApplicationItems = PackageUtils.getInstalledOrBackedUpApplicationsFromDb(getApplication(), true, true)
                val loadTime = System.currentTimeMillis() - startTime
                Log.d("MVM", "Loaded ${updatedApplicationItems!!.size} apps from database in ${loadTime}ms (SLOW PATH)")
                mAppListCache.saveToCache(updatedApplicationItems)
            }

            synchronized(mApplicationItems) {
                mApplicationItems.clear()
                mApplicationItems.addAll(updatedApplicationItems!!)
                for (item in selectedApplicationItems) {
                    select(item)
                }
                sortApplicationList(mSortBy, mReverseSort)
                filterItemsByFlags()
            }
        }
    }

    fun invalidateAppListCache() {
        mAppListCache.invalidateCache()
    }

    private fun cancelIfRunning() {
        mFilterResult?.cancel()
    }

    @WorkerThread
    private fun filterItemsByQuery(applicationItems: List<ApplicationItem>) {
        if (mSearchType == AdvancedSearchView.SEARCH_TYPE_REGEX) {
            val filteredApplicationItems = AdvancedSearchView.matches(mSearchQuery, applicationItems,
                { item ->
                    item.ensureLowerCaseFields()
                    listOf(item.packageNameLowerCase, item.labelLowerCase)
                }, AdvancedSearchView.SEARCH_TYPE_REGEX)
            mApplicationItemsState.value = filteredApplicationItems
            return
        }
        val queryLower = mSearchQuery?.lowercase(Locale.ROOT) ?: ""\nval filteredApplicationItems = ArrayList<ApplicationItem>()
        for (item in applicationItems) {
            if (ThreadUtils.isInterrupted()) return
            item.ensureLowerCaseFields()
            if (AdvancedSearchView.matches(queryLower, item.packageNameLowerCase, mSearchType)) {
                filteredApplicationItems.add(item)
            } else if (mSearchType == AdvancedSearchView.SEARCH_TYPE_CONTAINS) {
                if (Utils.containsOrHasInitials(mSearchQuery, item.label)) {
                    filteredApplicationItems.add(item)
                }
            } else if (AdvancedSearchView.matches(queryLower, item.labelLowerCase, mSearchType)) {
                filteredApplicationItems.add(item)
            }
        }
        mApplicationItemsState.value = filteredApplicationItems
    }

    @WorkerThread
    @GuardedBy("mApplicationItems")
    private fun filterItemsByFlags() {
        synchronized(mApplicationItems) {
            val candidateApplicationItems = ArrayList<ApplicationItem>()
            val profileFilterOptions = ArrayList<FilterOption>()
            if (mFilterProfileName != null) {
                val profileId = ProfileManager.getProfileIdCompat(mFilterProfileName)
                val profilePath = ProfileManager.findProfilePathById(profileId)
                try {
                    val profile = BaseProfile.fromPath(profilePath)
                    if (profile is AppsProfile) {
                        val option = PackageNameOption()
                        option.setKeyValue("eq_any", TextUtils.join("\n", profile.packages))
                        profileFilterOptions.add(option)
                    } else if (profile is AppsFilterProfile) {
                        val filterItem = profile.filterItem
                        for (i in 0 until filterItem.size) {
                            profileFilterOptions.add(filterItem.getFilterOptionAt(i))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MVM", "Failed to load profile", e)
                }
            }
            for (item in mApplicationItems) {
                if (ThreadUtils.isInterrupted()) return
                if (isAmongSelectedUsers(item)) {
                    candidateApplicationItems.add(item)
                }
            }
            if (profileFilterOptions.isEmpty() && mFilterFlags == MainListOptions.FILTER_NO_FILTER) {
                if (!TextUtils.isEmpty(mSearchQuery)) {
                    filterItemsByQuery(candidateApplicationItems)
                } else {
                    mApplicationItemsState.value = candidateApplicationItems
                }
            } else {
                val filteredApplicationItems = ArrayList<ApplicationItem>()
                val filterItem = MainListOptions.getFilterItemFromFlags(mFilterFlags)
                for (filterOption in profileFilterOptions) {
                    filterItem.addFilterOption(filterOption)
                }
                val packageUsageInfoList: Map<String, PackageUsageInfo>
                if (filterItem.timesUsageInfoUsed > 0) {
                    synchronized(mUsageCacheLock) {
                        val currentTime = System.currentTimeMillis()
                        val cacheValid = mCachedUsageStats != null && (currentTime - mUsageStatsCacheTimestamp) < USAGE_STATS_CACHE_TTL_MS
                        if (cacheValid) {
                            packageUsageInfoList = mCachedUsageStats!!
                        } else {
                            packageUsageInfoList = buildUsageStatsMap()
                            ensureActive()
                            mCachedUsageStats = packageUsageInfoList
                            mUsageStatsCacheTimestamp = currentTime
                        }
                    }
                } else {
                    packageUsageInfoList = HashMap()
                }
                val runningPackages = HashSet<String>()
                if (filterItem.timesRunningOptionUsed > 0) {
                    for (info in ActivityManagerCompat.getRunningAppProcesses()) {
                        if (info.pkgList != null) {
                            runningPackages.addAll(listOf(*info.pkgList))
                        }
                    }
                }
                for (item in candidateApplicationItems) {
                    item.setPackageUsageInfo(packageUsageInfoList[item.packageName])
                    item.setRunning(runningPackages.contains(item.packageName))
                }
                val result = filterItem.getFilteredList(candidateApplicationItems)
                for (item in result) {
                    if ((mFilterFlags and MainListOptions.FILTER_APPS_WITH_SAF) != 0 && !item.info.usesSaf) {
                        continue
                    }
                    filteredApplicationItems.add(item.info)
                }
                if (!TextUtils.isEmpty(mSearchQuery)) {
                    filterItemsByQuery(filteredApplicationItems)
                } else {
                    mApplicationItemsState.value = filteredApplicationItems
                }
            }
            mSuggestionsState.value = io.github.muntashirakon.AppManager.batchops.SuggestionHandler.getApplicationItemSuggestions(candidateApplicationItems)
        }
    }

    private fun buildUsageStatsMap(): Map<String, PackageUsageInfo> {
        val packageUsageInfoList = HashMap<String, PackageUsageInfo>()
        val hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission()
        if (hasUsageAccess) {
            val interval = UsageUtils.getLastWeek()
            for (userId in Users.getUsersIds()) {
                val usageInfoList = ExUtils.exceptionAsNull { AppUsageStatsManager.getInstance().getUsageStats(interval, userId) }
                if (usageInfoList != null) {
                    for (info in usageInfoList) {
                        ensureActive()
                        return@launch packageUsageInfoList
                        val oldInfo = packageUsageInfoList[info.packageName]
                        if (oldInfo != null) {
                            oldInfo.screenTime += info.screenTime
                            oldInfo.lastUsageTime += info.lastUsageTime
                            oldInfo.timesOpened += info.timesOpened
                            oldInfo.mobileData = AppUsageStatsManager.DataUsage.fromDataUsage(oldInfo.mobileData, info.mobileData)
                            oldInfo.wifiData = AppUsageStatsManager.DataUsage.fromDataUsage(oldInfo.wifiData, info.wifiData)
                            if (info.entries != null) {
                                if (oldInfo.entries == null) {
                                    oldInfo.entries = info.entries
                                } else oldInfo.entries.addAll(info.entries)
                            }
                        } else packageUsageInfoList[info.packageName] = info
                    }
                }
            }
        }
        return packageUsageInfoList
    }

    fun invalidateUsageStatsCache() {
        synchronized(mUsageCacheLock) {
            mCachedUsageStats = null
            mUsageStatsCacheTimestamp = 0
        }
    }

    private fun isAmongSelectedUsers(applicationItem: ApplicationItem): Boolean {
        if (mSelectedUsers == null) return true
        for (userId in mSelectedUsers!!) {
            if (ArrayUtils.contains(applicationItem.userIds, userId)) return true
        }
        return false
    }

    @GuardedBy("mApplicationItems")
    private fun sortApplicationList(@MainListOptions.SortOrder sortBy: Int, reverse: Boolean) {
        synchronized(mApplicationItems) {
            val mode = if (reverse) -1 else 1
            // Load archived package names once per sort pass so the SORT_BY_ARCHIVABLE comparator
            // can exclude apps that are already archived from the "archivable first" group.
            val archivedPackages: Set<String> = if (sortBy == MainListOptions.SORT_BY_ARCHIVABLE) {
                AppsDb.getInstance().archivedAppDao().getAllPackageNamesSync().toHashSet()
            } else emptySet()
            mApplicationItems.sortWith { o1, o2 ->
                var primaryComparison = 0
                when (sortBy) {
                    MainListOptions.SORT_BY_APP_LABEL -> return@sortWith mode * mCollator.compare(o1.label, o2.label)
                    MainListOptions.SORT_BY_PACKAGE_NAME -> primaryComparison = mode * o1.packageName.compareTo(o2.packageName)
                    MainListOptions.SORT_BY_DOMAIN -> {
                        val isSystem1 = (o1.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val isSystem2 = (o2.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        primaryComparison = mode * isSystem1.compareTo(isSystem2)
                    }
                    MainListOptions.SORT_BY_LAST_UPDATE -> primaryComparison = -mode * o1.lastUpdateTime.compareTo(o2.lastUpdateTime)
                    MainListOptions.SORT_BY_TOTAL_SIZE -> primaryComparison = -mode * o1.totalSize.compareTo(o2.totalSize)
                    MainListOptions.SORT_BY_DATA_USAGE -> primaryComparison = -mode * o1.dataUsage.compareTo(o2.dataUsage)
                    MainListOptions.SORT_BY_OPEN_COUNT -> primaryComparison = -mode * o1.openCount.compareTo(o2.openCount)
                    MainListOptions.SORT_BY_INSTALLATION_DATE -> primaryComparison = -mode * o1.firstInstallTime.compareTo(o2.firstInstallTime)
                    MainListOptions.SORT_BY_SCREEN_TIME -> primaryComparison = -mode * o1.screenTime.compareTo(o2.screenTime)
                    MainListOptions.SORT_BY_LAST_USAGE_TIME -> primaryComparison = -mode * o1.lastUsageTime.compareTo(o2.lastUsageTime)
                    MainListOptions.SORT_BY_TARGET_SDK -> {
                        primaryComparison = mode * o1.targetSdk.compareTo(o2.targetSdk)
                    }
                    MainListOptions.SORT_BY_SHARED_ID -> primaryComparison = mode * o1.uid.compareTo(o2.uid)
                    MainListOptions.SORT_BY_SHA -> {
                        if (o1.sha == null) primaryComparison = -mode
                        else if (o2.sha == null) primaryComparison = mode
                        else {
                            val i = o1.sha!!.first.compareTo(o2.sha!!.first, ignoreCase = true)
                            primaryComparison = if (i == 0) mode * o1.sha!!.second.compareTo(o2.sha!!.second, ignoreCase = true) else mode * i
                        }
                    }
                    MainListOptions.SORT_BY_BLOCKED_COMPONENTS -> primaryComparison = -mode * o1.blockedCount.compareTo(o2.blockedCount)
                    MainListOptions.SORT_BY_FROZEN_APP -> primaryComparison = -mode * o1.isDisabled.compareTo(o2.isDisabled)
                    MainListOptions.SORT_BY_BACKUP -> primaryComparison = -mode * (o1.backup != null).compareTo(o2.backup != null)
                    MainListOptions.SORT_BY_LAST_ACTION -> primaryComparison = -mode * o1.lastActionTime.compareTo(o2.lastActionTime)
                    MainListOptions.SORT_BY_TRACKERS -> primaryComparison = -mode * o1.trackerCount.compareTo(o2.trackerCount)
                    MainListOptions.SORT_BY_ARCHIVABLE -> {
                        // Archivable = user-installed, currently installed, NOT already archived
                        val isArchivable1 = o1.isInstalled && !o1.isSystem && o1.packageName !in archivedPackages
                        val isArchivable2 = o2.isInstalled && !o2.isSystem && o2.packageName !in archivedPackages
                        primaryComparison = isArchivable2.compareTo(isArchivable1) // true > false puts archivable first
                    }
                }
                if (primaryComparison == 0) mCollator.compare(o1.label, o2.label) else primaryComparison
            }
        }
    }

    @WorkerThread
    private fun updateInfoForUid(uid: Int, action: String?) {
        Log.d("updateInfoForUid", "Uid: %d", uid)
        val packages = if (Intent.ACTION_PACKAGE_REMOVED == action) getPackagesForUid(uid) else mPackageManager.getPackagesForUid(uid)
        updateInfoForPackages(packages, action ?: "")
    }

    @WorkerThread
    private fun updateInfoForPackages(packages: Array<String>?, action: String) {
        Log.d("updateInfoForPackages", "packages: %s", Arrays.toString(packages))
        if (packages == null || packages.isEmpty()) return
        var modified = false
        when (action) {
            PackageChangeReceiver.ACTION_DB_PACKAGE_REMOVED, PackageChangeReceiver.ACTION_DB_PACKAGE_ALTERED, PackageChangeReceiver.ACTION_DB_PACKAGE_ADDED -> {
                val appDb = mAppDb
                for (packageName in packages) {
                    val item = getNewApplicationItem(packageName, appDb.getAllApplications(packageName))
                    modified = modified or (if (item != null) insertOrAddApplicationItem(item) else deleteApplicationItem(packageName))
                }
            }
            PackageChangeReceiver.ACTION_PACKAGE_REMOVED, PackageChangeReceiver.ACTION_PACKAGE_ALTERED, PackageChangeReceiver.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE, Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE, Intent.ACTION_PACKAGE_CHANGED -> {
                val appList = mAppDb.updateApplications(getApplication(), packages)
                for (packageName in packages) {
                    val item = getNewApplicationItem(packageName, appList)
                    modified = modified or (if (item != null) insertOrAddApplicationItem(item) else deleteApplicationItem(packageName))
                }
            }
            else -> return
        }
        if (modified) {
            sortApplicationList(mSortBy, mReverseSort)
            filterItemsByFlags()
        }
    }

    private fun insertOrAddApplicationItem(item: ApplicationItem?): Boolean {
        if (item == null) return false
        synchronized(mApplicationItems) {
            if (insertApplicationItem(item)) return true
            val inserted = mApplicationItems.add(item)
            if (mSelectedPackageApplicationItemMap.containsKey(item.packageName)) select(item)
            return inserted
        }
    }

    private fun insertApplicationItem(item: ApplicationItem): Boolean {
        synchronized(mApplicationItems) {
            var isInserted = false
            for (i in mApplicationItems.indices) {
                if (item == mApplicationItems[i]) {
                    mApplicationItems[i] = item
                    isInserted = true
                    if (mSelectedPackageApplicationItemMap.containsKey(item.packageName)) select(item)
                }
            }
            return isInserted
        }
    }

    private fun deleteApplicationItem(packageName: String): Boolean {
        synchronized(mApplicationItems) {
            val it = mApplicationItems.listIterator()
            while (it.hasNext()) {
                val item = it.next()
                if (item.packageName == packageName) {
                    mSelectedPackageApplicationItemMap.remove(packageName)
                    it.remove()
                    return true
                }
            }
            return false
        }
    }

    @WorkerThread
    private fun getNewApplicationItem(packageName: String, apps: List<App>): ApplicationItem? {
        val item = ApplicationItem()
        val thisUser = UserHandleHidden.myUserId()
        for (app in apps) {
            if (packageName != app.packageName) continue
            if (app.isInstalled) {
                val newItem = item.packageName == null || !item.isInstalled
                if (item.packageName == null) item.packageName = app.packageName
                item.userIds = ArrayUtils.appendInt(item.userIds, app.userId)
                item.isInstalled = true
                item.isOnlyDataInstalled = false
                item.openCount += app.openCount
                item.screenTime += app.screenTime
                if (item.lastUsageTime == 0L || item.lastUsageTime < app.lastUsageTime) {
                    item.lastUsageTime = app.lastUsageTime
                }
                item.hasKeystore = item.hasKeystore or app.hasKeystore
                item.usesSaf = item.usesSaf or app.usesSaf
                if (app.ssaid != null) item.ssaid = app.ssaid
                item.totalSize += app.codeSize + app.dataSize
                item.dataUsage += app.wifiDataUsage + app.mobileDataUsage
                if (!newItem && app.userId != thisUser) continue
            } else {
                if (item.packageName != null) continue
                else {
                    item.packageName = app.packageName
                    item.isInstalled = false
                    item.isOnlyDataInstalled = app.isOnlyDataInstalled
                    item.hasKeystore = item.hasKeystore or app.hasKeystore
                }
            }
            item.flags = app.flags
            item.uid = app.uid
            item.debuggable = app.isDebuggable
            item.isUser = !app.isSystemApp
            item.isDisabled = !app.isEnabled
            item.label = app.packageLabel
            item.targetSdk = app.sdk
            item.versionName = app.versionName
            item.versionCode = app.versionCode
            item.sharedUserId = app.sharedUserId
            item.sha = Pair(app.certName, app.certAlgo)
            item.firstInstallTime = app.firstInstallTime
            item.lastUpdateTime = app.lastUpdateTime
            item.hasActivities = app.hasActivities
            item.hasSplits = app.hasSplits
            item.blockedCount = app.rulesCount
            item.trackerCount = app.trackerCount
            item.lastActionTime = app.lastActionTime
            if (item.backup == null) {
                item.backup = BackupUtils.getLatestBackupMetadataFromDbNoLockValidate(packageName)
            }
            item.generateOtherInfo()
        }
        return if (item.packageName == null) null else item
    }

    private fun getPackagesForUid(uid: Int): Array<String> {
        synchronized(mApplicationItems) {
            val packages = LinkedList<String>()
            for (item in mApplicationItems) {
                if (item.uid == uid) packages.add(item.packageName)
            }
            return packages.toTypedArray()
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(mPackageObserver)
        super.onCleared()
    }

    class PackageIntentReceiver(private val mModel: MainViewModel) : PackageChangeReceiver(mModel.getApplication()) {
        override fun onPackageChanged(intent: Intent, uid: Int?, packages: Array<String>?) {
            mModel.cancelIfRunning()
            if (uid != null) {
                mModel.updateInfoForUid(uid, intent.action)
            } else if (packages != null) {
                mModel.updateInfoForPackages(packages, intent.action ?: "")
            } else {
                mModel.loadApplicationItems()
            }
        }
    }

    companion object {
        private const val USAGE_STATS_CACHE_TTL_MS = 60_000L
    }
}
_TTL_MS = 60_000L
    }
}
