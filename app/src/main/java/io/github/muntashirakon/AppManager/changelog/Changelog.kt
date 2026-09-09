// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog

// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
class Changelog {
    val changelogItems: ArrayDeque<ChangelogItem> = ArrayDeque()
    var isBulletedList: Boolean = false

    fun addItem(row: ChangelogItem) {
        changelogItems.add(row)
    }

    fun clearAllRows() {
        changelogItems.clear()
    }
}
