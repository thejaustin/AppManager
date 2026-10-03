// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.os.Bundle
import android.view.View
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.filters.FilterItem
import io.github.muntashirakon.AppManager.filters.options.AppTypeOption
import io.github.muntashirakon.AppManager.filters.options.BackupOption
import io.github.muntashirakon.AppManager.filters.options.ComponentsOption
import io.github.muntashirakon.AppManager.filters.options.FreezeOption
import io.github.muntashirakon.AppManager.filters.options.ArchivableOption
import io.github.muntashirakon.AppManager.filters.options.InstalledOption
import io.github.muntashirakon.AppManager.filters.options.RunningAppsOption
import io.github.muntashirakon.AppManager.misc.ListOptions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import java.util.*
import java.util.concurrent.Future

class MainListOptions : ListOptions() {
    override fun getSortIdLocaleMap(): LinkedHashMap<Int, Int>? = SORT_ITEMS_MAP

    override fun getFilterFlagLocaleMap(): LinkedHashMap<Int, Int>? = FILTER_ITEMS_MAP

    override fun getOptionIdLocaleMap(): LinkedHashMap<Int, Int>? = null

    companion object {
        val TAG: String = MainListOptions::class.java.simpleName

        const val SORT_BY_DOMAIN = 0 // User/system app
        const val SORT_BY_APP_LABEL = 1
        const val SORT_BY_PACKAGE_NAME = 2
        const val SORT_BY_LAST_UPDATE = 3
        const val SORT_BY_SHARED_ID = 4
        const val SORT_BY_TARGET_SDK = 5
        const val SORT_BY_SHA = 6 // Signature
        const val SORT_BY_FROZEN_APP = 7
        const val SORT_BY_BLOCKED_COMPONENTS = 8
        const val SORT_BY_BACKUP = 9
        const val SORT_BY_TRACKERS = 10
        const val SORT_BY_LAST_ACTION = 11
        const val SORT_BY_INSTALLATION_DATE = 12
        const val SORT_BY_TOTAL_SIZE = 13
        const val SORT_BY_DATA_USAGE = 14
        const val SORT_BY_OPEN_COUNT = 15
        const val SORT_BY_SCREEN_TIME = 16
        const val SORT_BY_LAST_USAGE_TIME = 17
        const val SORT_BY_ARCHIVABLE = 18

        const val FILTER_NO_FILTER = 0
        const val FILTER_USER_APPS = 1
        const val FILTER_SYSTEM_APPS = 1 shl 1
        const val FILTER_FROZEN_APPS = 1 shl 2
        const val FILTER_APPS_WITH_RULES = 1 shl 3
        const val FILTER_APPS_WITH_ACTIVITIES = 1 shl 4
        const val FILTER_APPS_WITH_BACKUPS = 1 shl 5
        const val FILTER_RUNNING_APPS = 1 shl 6
        const val FILTER_APPS_WITH_SPLITS = 1 shl 7
        const val FILTER_INSTALLED_APPS = 1 shl 8
        const val FILTER_UNINSTALLED_APPS = 1 shl 9
        const val FILTER_APPS_WITHOUT_BACKUPS = 1 shl 10
        const val FILTER_APPS_WITH_KEYSTORE = 1 shl 11
        const val FILTER_APPS_WITH_SAF = 1 shl 12
        const val FILTER_APPS_WITH_SSAID = 1 shl 13
        const val FILTER_STOPPED_APPS = 1 shl 14
        const val FILTER_UNFROZEN_APPS = 1 shl 15
        const val FILTER_ARCHIVABLE_APPS = 1 shl 16
        const val FILTER_DISABLED_APPS = 1 shl 17

        private val SORT_ITEMS_MAP = LinkedHashMap<Int, Int>().apply {
            put(SORT_BY_DOMAIN, R.string.sort_by_domain_type)
            put(SORT_BY_APP_LABEL, R.string.sort_by_app_label)
            put(SORT_BY_PACKAGE_NAME, R.string.sort_by_package_name)
            put(SORT_BY_INSTALLATION_DATE, R.string.sort_by_installation_date)
            put(SORT_BY_LAST_UPDATE, R.string.sort_by_last_update_time)
            put(SORT_BY_SHARED_ID, R.string.sort_by_shared_id)
            put(SORT_BY_TARGET_SDK, R.string.sort_by_target_sdk)
            put(SORT_BY_SHA, R.string.sort_by_sha_256)
            put(SORT_BY_FROZEN_APP, R.string.sort_by_frozen_app)
            put(SORT_BY_BLOCKED_COMPONENTS, R.string.sort_by_blocked_components)
            put(SORT_BY_BACKUP, R.string.sort_by_backup)
            put(SORT_BY_TRACKERS, R.string.sort_by_trackers)
            put(SORT_BY_LAST_ACTION, R.string.sort_by_last_action_time)
            put(SORT_BY_TOTAL_SIZE, R.string.sort_by_total_size)
            put(SORT_BY_DATA_USAGE, R.string.sort_by_data_usage)
            put(SORT_BY_OPEN_COUNT, R.string.sort_by_open_count)
            put(SORT_BY_SCREEN_TIME, R.string.sort_by_screen_time)
            put(SORT_BY_LAST_USAGE_TIME, R.string.sort_by_last_usage_time)
            put(SORT_BY_ARCHIVABLE, R.string.sort_by_archivable)
        }

        private val FILTER_ITEMS_MAP = LinkedHashMap<Int, Int>().apply {
            put(FILTER_USER_APPS, R.string.filter_user_apps)
            put(FILTER_SYSTEM_APPS, R.string.filter_system_apps)
            put(FILTER_FROZEN_APPS, R.string.filter_frozen_apps)
            put(FILTER_UNFROZEN_APPS, R.string.filter_unfrozen_apps)
            put(FILTER_APPS_WITH_RULES, R.string.filter_apps_with_rules)
            put(FILTER_APPS_WITH_ACTIVITIES, R.string.filter_apps_with_activities)
            put(FILTER_APPS_WITH_BACKUPS, R.string.filter_apps_with_backups)
            put(FILTER_RUNNING_APPS, R.string.filter_running_apps)
            put(FILTER_APPS_WITH_SPLITS, R.string.filter_apps_with_splits)
            put(FILTER_INSTALLED_APPS, R.string.filter_installed_apps)
            put(FILTER_UNINSTALLED_APPS, R.string.filter_uninstalled_apps)
            put(FILTER_APPS_WITHOUT_BACKUPS, R.string.filter_apps_without_backups)
            put(FILTER_APPS_WITH_KEYSTORE, R.string.filter_apps_with_keystore)
            put(FILTER_APPS_WITH_SAF, R.string.filter_apps_with_saf)
            put(FILTER_APPS_WITH_SSAID, R.string.filter_apps_with_ssaid)
            put(FILTER_STOPPED_APPS, R.string.filter_force_stopped_apps)
            put(FILTER_DISABLED_APPS, R.string.filter_disabled_apps)
            put(FILTER_ARCHIVABLE_APPS, R.string.filter_archivable_apps)
        }

        @JvmStatic
        fun getFilterItemFromFlags(flags: Int): FilterItem {
            val filterItem = FilterItem()
            var appTypeWithFlags = 0
            if (flags and FILTER_USER_APPS != 0) appTypeWithFlags = appTypeWithFlags or AppTypeOption.APP_TYPE_USER
            if (flags and FILTER_SYSTEM_APPS != 0) appTypeWithFlags = appTypeWithFlags or AppTypeOption.APP_TYPE_SYSTEM
            if (flags and FILTER_FROZEN_APPS != 0) {
                val option = FreezeOption()
                option.setKeyValue("frozen", null)
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_UNFROZEN_APPS != 0) {
                val option = FreezeOption()
                option.setKeyValue("unfrozen", null)
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_APPS_WITH_RULES != 0) appTypeWithFlags = appTypeWithFlags or AppTypeOption.APP_TYPE_WITH_RULES
            if (flags and FILTER_APPS_WITH_ACTIVITIES != 0) {
                val option = ComponentsOption()
                option.setKeyValue("with_type", ComponentsOption.COMPONENT_TYPE_ACTIVITY.toString())
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_APPS_WITH_BACKUPS != 0) {
                val option = BackupOption()
                option.setKeyValue("backups", null)
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_RUNNING_APPS != 0) {
                val option = RunningAppsOption()
                option.setKeyValue("running", null)
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_APPS_WITH_SPLITS != 0) {
                appTypeWithFlags = appTypeWithFlags or AppTypeOption.APP_TYPE_WITH_SPLITS
            }
            if (flags and FILTER_INSTALLED_APPS != 0) {
                val option = InstalledOption()
                option.setKeyValue("installed", null)
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_UNINSTALLED_APPS != 0) {
                val option = InstalledOption()
                option.setKeyValue("uninstalled", null)
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_APPS_WITHOUT_BACKUPS != 0) {
                val option = BackupOption()
                option.setKeyValue("no_backups", null)
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_APPS_WITH_KEYSTORE != 0) appTypeWithFlags = appTypeWithFlags or AppTypeOption.APP_TYPE_KEYSTORE
            if (flags and FILTER_APPS_WITH_SAF != 0) {
                // SAF check reads /data/system/urigrants.xml which requires root; handled as a
                // post-filter in MainViewModel using ApplicationItem.usesSaf populated during sync.
            }
            if (flags and FILTER_APPS_WITH_SSAID != 0) appTypeWithFlags = appTypeWithFlags or AppTypeOption.APP_TYPE_SSAID
            if (flags and FILTER_STOPPED_APPS != 0) appTypeWithFlags = appTypeWithFlags or AppTypeOption.APP_TYPE_STOPPED
            if (flags and FILTER_DISABLED_APPS != 0) {
                val option = FreezeOption()
                option.setKeyValue("with_flags", FreezeOption.FREEZE_TYPE_DISABLED.toString())
                filterItem.addFilterOption(option)
            }
            if (flags and FILTER_ARCHIVABLE_APPS != 0) {
                val option = ArchivableOption()
                option.setKeyValue("archivable", null)
                filterItem.addFilterOption(option)
            }
            if (appTypeWithFlags > 0) {
                val appTypeWithFlagsOption = AppTypeOption()
                appTypeWithFlagsOption.setKeyValue("type", appTypeWithFlags.toString())
                filterItem.addFilterOption(appTypeWithFlagsOption)
            }
            return filterItem
        }
    }
}
