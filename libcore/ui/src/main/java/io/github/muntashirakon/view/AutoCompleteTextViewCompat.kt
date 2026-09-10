// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.widget.AutoCompleteTextView
import android.widget.ListPopupWindow
import com.google.android.material.textfield.MaterialAutoCompleteTextView

object AutoCompleteTextViewCompat {
    @JvmStatic
    @Suppress("JavaReflectionMemberAccess")
    @SuppressLint("DiscouragedPrivateApi")
    fun setListSelector(view: AutoCompleteTextView, listSelector: Drawable?) {
        try {
            val mPopup = AutoCompleteTextView::class.java.getDeclaredField("mPopup")
            mPopup.isAccessible = true
            val popupWindow = mPopup.get(view) as? ListPopupWindow
            popupWindow?.setListSelector(listSelector)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun setListSelectorMaterial(view: MaterialAutoCompleteTextView, listSelector: Drawable?) {
        setListSelector(view, listSelector)
        try {
            val mPopup = MaterialAutoCompleteTextView::class.java.getDeclaredField("modalListPopup")
            mPopup.isAccessible = true
            val popupWindow = mPopup.get(view) as? androidx.appcompat.widget.ListPopupWindow
            popupWindow?.setListSelector(listSelector)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
