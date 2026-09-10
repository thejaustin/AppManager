// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.View
import androidx.annotation.Px
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import io.github.muntashirakon.util.UiUtils

@SuppressLint("RestrictedApi")
class M3PreferenceGroupDecoration(@Px private val radius: Float, @Px private val innerRadius: Float) :
    RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        if (view !is MaterialCardView) return
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        if (isCategoryHeader(adapter, position)) return

        val isFirst = isFirstInGroup(adapter, position)
        val isLast = isLastInGroup(adapter, position)

        when {
            isFirst && isLast -> UiUtils.setCardRadius(view, radius, radius)
            isFirst -> UiUtils.setCardRadius(view, radius, innerRadius)
            isLast -> UiUtils.setCardRadius(view, innerRadius, radius)
            else -> UiUtils.setCardRadius(view, innerRadius, innerRadius)
        }
    }

    private fun isCategoryHeader(adapter: PreferenceGroupAdapter, position: Int) =
        adapter.getItem(position) is PreferenceCategory

    private fun isFirstInGroup(adapter: PreferenceGroupAdapter, position: Int) =
        position == 0 || isCategoryHeader(adapter, position - 1)

    private fun isLastInGroup(adapter: PreferenceGroupAdapter, position: Int) =
        position == adapter.itemCount - 1 || isCategoryHeader(adapter, position + 1)
}
