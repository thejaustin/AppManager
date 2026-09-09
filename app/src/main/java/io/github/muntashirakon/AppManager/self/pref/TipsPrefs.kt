// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.pref

import io.github.muntashirakon.AppManager.utils.AppPref

class TipsPrefs private constructor() {
    private var mFlags: Int = AppPref.getInt(AppPref.PrefKey.PREF_TIPS_PREFS_INT)

    var displayInAppOpsTab: Boolean
        get() = (mFlags and TIPS_TAB_APP_OPS) != 0
        set(value) = save(TIPS_TAB_APP_OPS, value)

    var displayInUsesPermissionsTab: Boolean
        get() = (mFlags and TIPS_TAB_USES_PERMISSIONS) != 0
        set(value) = save(TIPS_TAB_USES_PERMISSIONS, value)

    var displayInPermissionsTab: Boolean
        get() = (mFlags and TIPS_TAB_PERMISSIONS) != 0
        set(value) = save(TIPS_TAB_PERMISSIONS, value)

    var displayInOverlaysTab: Boolean
        get() = (mFlags and TIPS_TAB_USES_PERMISSIONS) != 0
        set(value) = save(TIPS_TAB_OVERLAYS, value)

    private fun save(flag: Int, display: Boolean) {
        if (display) mFlags = mFlags or flag else mFlags = mFlags and flag.inv()
        AppPref.set(AppPref.PrefKey.PREF_TIPS_PREFS_INT, mFlags)
    }

    companion object {
        private const val TIPS_TAB_APP_OPS = 1 shl 0
        private const val TIPS_TAB_USES_PERMISSIONS = 1 shl 1
        private const val TIPS_TAB_PERMISSIONS = 1 shl 2
        private const val TIPS_TAB_OVERLAYS = 1 shl 3

        private var sInstance: TipsPrefs? = null

        @JvmStatic
        fun getInstance(): TipsPrefs = sInstance ?: TipsPrefs().also { sInstance = it }
    }
}
