// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog

import java.util.Locale

// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
class ChangelogHeader(
    val versionName: String,
    val versionCode: Long,
    val releaseType: String,
    val releaseDate: String,
) : ChangelogItem(parseHeaderText(versionName, versionCode), HEADER) {
    init {
        isBulletedList = false
    }

    companion object {
        private fun parseHeaderText(versionName: String, versionCode: Long): CharSequence =
            String.format(Locale.getDefault(), "Version %s (%d)", versionName, versionCode)
    }
}
