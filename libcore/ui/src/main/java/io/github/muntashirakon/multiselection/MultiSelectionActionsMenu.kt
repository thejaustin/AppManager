// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

// Copyright 2020 The Android Open Source Project

package io.github.muntashirakon.multiselection

import android.annotation.SuppressLint
import android.content.Context
import android.view.MenuItem
import android.view.SubMenu
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl

@SuppressLint("RestrictedApi")
internal class MultiSelectionActionsMenu(context: Context) : MenuBuilder(context) {
    override fun addSubMenu(group: Int, id: Int, categoryOrder: Int, title: CharSequence): SubMenu {
        throw UnsupportedOperationException("MultiSelectionActionsView does not support submenus")
    }

    override fun addInternal(group: Int, id: Int, categoryOrder: Int, title: CharSequence): MenuItem {
        stopDispatchingItemsChanged()
        val item = super.addInternal(group, id, categoryOrder, title)
        if (item is MenuItemImpl) {
            item.isExclusiveCheckable = true
        }
        startDispatchingItemsChanged()
        return item
    }
}
