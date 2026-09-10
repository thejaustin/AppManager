// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

// Copyright 2020 The Android Open Source Project

package io.github.muntashirakon.multiselection

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.view.ViewGroup
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.menu.MenuPresenter
import androidx.appcompat.view.menu.MenuView
import androidx.appcompat.view.menu.SubMenuBuilder

@SuppressLint("RestrictedApi")
internal class MultiSelectionActionsMenuPresenter : MenuPresenter {
    private lateinit var mMenuView: MultiSelectionActionsView
    private var mUpdateSuspended = false
    private var mId = 0

    fun setMenuView(menuView: MultiSelectionActionsView) {
        mMenuView = menuView
    }

    override fun initForMenu(context: Context, menu: MenuBuilder) {
        mMenuView.initialize(menu)
    }

    override fun getMenuView(root: ViewGroup?): MenuView = mMenuView

    override fun updateMenuView(cleared: Boolean) {
        if (mUpdateSuspended) return
        if (cleared) mMenuView.buildMenuView() else mMenuView.updateMenuView()
    }

    override fun setCallback(cb: MenuPresenter.Callback?) {}

    override fun onSubMenuSelected(subMenu: SubMenuBuilder?): Boolean = false

    override fun onCloseMenu(menu: MenuBuilder?, allMenusAreClosing: Boolean) {}

    override fun flagActionItems(): Boolean = false

    override fun expandItemActionView(menu: MenuBuilder?, item: MenuItemImpl?): Boolean = false

    override fun collapseItemActionView(menu: MenuBuilder?, item: MenuItemImpl?): Boolean = false

    fun setId(id: Int) { mId = id }

    override fun getId(): Int = mId

    override fun onSaveInstanceState(): Parcelable = SavedState()

    override fun onRestoreInstanceState(state: Parcelable) {}

    fun setUpdateSuspended(updateSuspended: Boolean) {
        mUpdateSuspended = updateSuspended
    }

    internal class SavedState() : Parcelable {
        constructor(parcel: Parcel) : this()

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {}

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState = SavedState(parcel)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
