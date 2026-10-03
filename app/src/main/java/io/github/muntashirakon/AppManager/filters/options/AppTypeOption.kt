// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.TextUtils
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.LangUtils

class AppTypeOption : FilterOption("app_type") {
    companion object {
        const val APP_TYPE_USER = 1 shl 0
        const val APP_TYPE_SYSTEM = 1 shl 1
        const val APP_TYPE_UPDATED_SYSTEM = 1 shl 2
        const val APP_TYPE_PRIVILEGED = 1 shl 3
        const val APP_TYPE_DATA_ONLY = 1 shl 4
        const val APP_TYPE_STOPPED = 1 shl 5
        const val APP_TYPE_SENSORS = 1 shl 6
        const val APP_TYPE_LARGE_HEAP = 1 shl 7
        const val APP_TYPE_DEBUGGABLE = 1 shl 8
        const val APP_TYPE_TEST_ONLY = 1 shl 9
        const val APP_TYPE_HAS_CODE = 1 shl 10
        const val APP_TYPE_PERSISTENT = 1 shl 11
        const val APP_TYPE_ALLOW_BACKUP = 1 shl 12
        const val APP_TYPE_INSTALLED_IN_EXTERNAL = 1 shl 13
        const val APP_TYPE_HTTP_ONLY = 1 shl 14
        const val APP_TYPE_BATTERY_OPT_ENABLED = 1 shl 15
        const val APP_TYPE_PLAY_APP_SIGNING = 1 shl 16
        const val APP_TYPE_SSAID = 1 shl 17
        const val APP_TYPE_KEYSTORE = 1 shl 18
        const val APP_TYPE_WITH_RULES = 1 shl 19
        const val APP_TYPE_PWA = 1 shl 20
        const val APP_TYPE_SHORT_CODE = 1 shl 21
        const val APP_TYPE_OVERLAY = 1 shl 22
        const val APP_TYPE_WITH_SPLITS = 1 shl 23

        /**
         * Returns true if the given info matches all the flags in 'flag'.
         * Only checks those flags that are set in 'flag' to minimize expensive calls.
         */
        @JvmStatic
        fun withFlagsCheck(info: IFilterableAppInfo, flag: Int): Boolean {
            if (flag and APP_TYPE_SYSTEM != 0) {
                if (!info.isSystemApp) return false
            }
            if (flag and APP_TYPE_USER != 0) {
                if (info.isSystemApp) return false
            }
            if (flag and APP_TYPE_UPDATED_SYSTEM != 0) {
                if (!info.isUpdatedSystemApp) return false
            }
            if (flag and APP_TYPE_PRIVILEGED != 0) {
                if (!info.isPrivileged) return false
            }
            if (flag and APP_TYPE_DATA_ONLY != 0) {
                if (!info.dataOnlyApp()) return false
            }
            if (flag and APP_TYPE_STOPPED != 0) {
                if (!info.isStopped) return false
            }
            if (flag and APP_TYPE_LARGE_HEAP != 0) {
                if (!info.requestedLargeHeap()) return false
            }
            if (flag and APP_TYPE_DEBUGGABLE != 0) {
                if (!info.isDebuggable) return false
            }
            if (flag and APP_TYPE_TEST_ONLY != 0) {
                if (!info.isTestOnly) return false
            }
            if (flag and APP_TYPE_HAS_CODE != 0) {
                if (!info.hasCode()) return false
            }
            if (flag and APP_TYPE_PERSISTENT != 0) {
                if (!info.isPersistent) return false
            }
            if (flag and APP_TYPE_ALLOW_BACKUP != 0) {
                if (!info.backupAllowed()) return false
            }
            if (flag and APP_TYPE_INSTALLED_IN_EXTERNAL != 0) {
                if (!info.installedInExternalStorage()) return false
            }
            if (flag and APP_TYPE_HTTP_ONLY != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!info.usesHttp()) return false
                }
                // On older versions, this is always true
            }
            if (flag and APP_TYPE_BATTERY_OPT_ENABLED != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!info.isBatteryOptEnabled) return false
                }
                // On older versions, this is always true
            }
            if (flag and APP_TYPE_SENSORS != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (!info.usesSensors()) return false
                }
                // On older versions, this is always true
            }
            if (flag and APP_TYPE_WITH_RULES != 0) {
                if (info.ruleCount == 0) return false
            }
            if (flag and APP_TYPE_KEYSTORE != 0) {
                if (!info.hasKeyStoreItems()) return false
            }
            if (flag and APP_TYPE_SSAID != 0) {
                if (TextUtils.isEmpty(info.ssaid)) return false
            }
            if (flag and APP_TYPE_WITH_SPLITS != 0) {
                if (!info.hasSplits()) return false
            }
            // All requested flags are matched
            return true
        }

        @JvmStatic
        fun withoutFlagsCheck(info: IFilterableAppInfo, flag: Int): Boolean {
            if (flag and APP_TYPE_SYSTEM != 0) {
                if (!info.isSystemApp) return true
            }
            if (flag and APP_TYPE_USER != 0) {
                if (info.isSystemApp) return true
            }
            if (flag and APP_TYPE_UPDATED_SYSTEM != 0) {
                if (!info.isUpdatedSystemApp) return true
            }
            if (flag and APP_TYPE_PRIVILEGED != 0) {
                if (!info.isPrivileged) return true
            }
            if (flag and APP_TYPE_DATA_ONLY != 0) {
                if (!info.dataOnlyApp()) return true
            }
            if (flag and APP_TYPE_STOPPED != 0) {
                if (!info.isStopped) return true
            }
            if (flag and APP_TYPE_LARGE_HEAP != 0) {
                if (!info.requestedLargeHeap()) return true
            }
            if (flag and APP_TYPE_DEBUGGABLE != 0) {
                if (!info.isDebuggable) return true
            }
            if (flag and APP_TYPE_TEST_ONLY != 0) {
                if (!info.isTestOnly) return true
            }
            if (flag and APP_TYPE_HAS_CODE != 0) {
                if (!info.hasCode()) return true
            }
            if (flag and APP_TYPE_PERSISTENT != 0) {
                if (!info.isPersistent) return true
            }
            if (flag and APP_TYPE_ALLOW_BACKUP != 0) {
                if (!info.backupAllowed()) return true
            }
            if (flag and APP_TYPE_INSTALLED_IN_EXTERNAL != 0) {
                if (!info.installedInExternalStorage()) return true
            }
            if (flag and APP_TYPE_HTTP_ONLY != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!info.usesHttp()) return true
                }
                // On older versions, this is always false
            }
            if (flag and APP_TYPE_BATTERY_OPT_ENABLED != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!info.isBatteryOptEnabled) return true
                }
                // On older versions, this is always false
            }
            if (flag and APP_TYPE_SENSORS != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (!info.usesSensors()) return true
                }
                // On older versions, this is always false
            }
            if (flag and APP_TYPE_WITH_RULES != 0) {
                if (info.ruleCount == 0) return true
            }
            if (flag and APP_TYPE_KEYSTORE != 0) {
                if (!info.hasKeyStoreItems()) return true
            }
            if (flag and APP_TYPE_SSAID != 0) {
                if (TextUtils.isEmpty(info.ssaid)) return true
            }
            if (flag and APP_TYPE_WITH_SPLITS != 0) {
                if (!info.hasSplits()) return true
            }
            // All requested flags are present, so "not all flags missing"\nreturn false
        }
    }

    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "with_flags" to TYPE_INT_FLAGS,
        "without_flags" to TYPE_INT_FLAGS
    )

    private val mFrozenFlags = linkedMapOf<Int, CharSequence>().apply {
        put(APP_TYPE_USER, "User app")
        put(APP_TYPE_SYSTEM, "System app")
        put(APP_TYPE_UPDATED_SYSTEM, "Updated system app")
        put(APP_TYPE_PRIVILEGED, "Privileged app")
        put(APP_TYPE_DATA_ONLY, "Data-only app")
        put(APP_TYPE_STOPPED, "Force-stopped app")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)
        ) {
            put(APP_TYPE_SENSORS, "Uses sensors")
        }
        put(APP_TYPE_LARGE_HEAP, "Requests large heap")
        put(APP_TYPE_DEBUGGABLE, "Debuggable app")
        put(APP_TYPE_TEST_ONLY, "Test-only app")
        put(APP_TYPE_HAS_CODE, "Has code")
        put(APP_TYPE_PERSISTENT, "Persistent app")
        put(APP_TYPE_ALLOW_BACKUP, "Backup allowed")
        put(APP_TYPE_INSTALLED_IN_EXTERNAL, "Installed in external storage")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            put(APP_TYPE_HTTP_ONLY, "Uses cleartext (HTTP) traffic")
            put(APP_TYPE_BATTERY_OPT_ENABLED, "Battery optimized")
        }
//        put(APP_TYPE_PLAY_APP_SIGNING, "Uses Play App Signing"); // TODO: 11/21/24
        put(APP_TYPE_SSAID, "Has SSAID")
        put(APP_TYPE_KEYSTORE, "Uses Android KeyStore")
        put(APP_TYPE_WITH_RULES, "Has rules")
        put(APP_TYPE_WITH_SPLITS, "Has split APKs")
//        put(APP_TYPE_PWA, "Progressive web app (PWA)"); // TODO: 11/21/24
//        put(APP_TYPE_SHORT_CODE, "Uses short code"); // TODO: 11/21/24
//        put(APP_TYPE_OVERLAY, "Overlay app"); // TODO: 11/21/24
    }

    override fun getKeysWithType(): Map<String, Int> {
        return mKeysWithType
    }

    override fun getFlags(key: String): Map<Int, CharSequence> {
        return if (key == "with_flags" || key == "without_flags") {
            mFrozenFlags
        } else {
            super.getFlags(key)
        }
    }

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "with_flags" -> result.setMatched(withFlagsCheck(info, intValue))
            "without_flags" -> result.setMatched(withoutFlagsCheck(info, intValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Apps")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "with_flags" -> sb.append(" with flags: ").append(flagsToString("with_flags", intValue))
            "without_flags" -> sb.append(" without flags: ").append(flagsToString("without_flags", intValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
