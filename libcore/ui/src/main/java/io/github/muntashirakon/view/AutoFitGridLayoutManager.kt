// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view

import android.content.Context
import androidx.annotation.Px
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Source: https://stackoverflow.com/a/55487382
// Copyright 2019 Vishal Nagvadiya
class AutoFitGridLayoutManager(context: Context, @Px columnWidth: Int) : GridLayoutManager(context, 1) {

    @Px
    private var mColumnWidth: Int = 0
    private var mColumnWidthChanged = true

    init {
        setColumnWidth(columnWidth)
    }

    fun setColumnWidth(@Px newColumnWidth: Int) {
        if (newColumnWidth > 0 && newColumnWidth != mColumnWidth) {
            mColumnWidth = newColumnWidth
            mColumnWidthChanged = true
        }
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (mColumnWidthChanged && mColumnWidth > 0) {
            val totalSpace = if (orientation == VERTICAL) {
                width - paddingRight - paddingLeft
            } else {
                height - paddingTop - paddingBottom
            }
            spanCount = maxOf(1, totalSpace / mColumnWidth)
            mColumnWidthChanged = false
        }
        super.onLayoutChildren(recycler, state)
    }
}
