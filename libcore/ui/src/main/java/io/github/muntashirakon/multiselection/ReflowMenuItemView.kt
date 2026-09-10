// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

// Copyright 2020 The Android Open Source Project

package io.github.muntashirakon.multiselection

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.PointerIconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import androidx.core.widget.TextViewCompat
import io.github.muntashirakon.ui.R

@SuppressLint("RestrictedApi")
class ReflowMenuItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val mIcon: ImageView
    private val mLabel: TextView
    private var mItemData: MenuItemImpl? = null
    private var mIconTint: ColorStateList? = null
    private var mOriginalIconDrawable: Drawable? = null
    private var mWrappedIconDrawable: Drawable? = null
    private var mActiveBackgroundEnabled = false

    companion object {
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.item_reflow_menu, this, true)
        mIcon = findViewById(R.id.icon)
        mLabel = findViewById(R.id.label)
        ViewCompat.setImportantForAccessibility(mLabel, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
        isFocusable = true
    }

    fun initialize(itemData: MenuItemImpl) {
        mItemData = itemData
        setCheckable(itemData.isCheckable)
        setChecked(itemData.isChecked)
        isEnabled = itemData.isEnabled
        setIcon(itemData.icon)
        setTitle(itemData.title)
        id = itemData.itemId
        if (!TextUtils.isEmpty(itemData.contentDescription)) {
            contentDescription = itemData.contentDescription
        }
        val tooltipText = if (!TextUtils.isEmpty(itemData.tooltipText)) itemData.tooltipText else itemData.title
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            TooltipCompat.setTooltipText(this, tooltipText)
        }
        visibility = if (itemData.isVisible) View.VISIBLE else View.GONE
    }

    fun setTitle(title: CharSequence?) {
        mLabel.text = title
        if (mItemData == null || TextUtils.isEmpty(mItemData!!.contentDescription)) {
            contentDescription = title
        }
        val tooltipText = if (mItemData == null || TextUtils.isEmpty(mItemData!!.tooltipText)) title else mItemData!!.tooltipText
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            TooltipCompat.setTooltipText(this, tooltipText)
        }
    }

    fun setCheckable(checkable: Boolean) { refreshDrawableState() }

    fun setChecked(checked: Boolean) {
        mLabel.pivotX = mLabel.width / 2F
        mLabel.pivotY = mLabel.baseline.toFloat()
        refreshDrawableState()
        isSelected = checked
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val infoCompat = AccessibilityNodeInfoCompat.wrap(info)
        infoCompat.setCollectionItemInfo(
            CollectionItemInfoCompat.obtain(0, 1, getItemVisiblePosition(), 1, false, isSelected)
        )
        if (isSelected) {
            infoCompat.isClickable = false
            infoCompat.removeAction(AccessibilityActionCompat.ACTION_CLICK)
        }
        infoCompat.roleDescription = resources.getString(com.google.android.material.R.string.item_view_role_description)
    }

    private fun getItemVisiblePosition(): Int {
        val parent = parent as? android.view.ViewGroup ?: return 0
        val index = parent.indexOfChild(this)
        var visiblePosition = 0
        for (i in 0 until index) {
            val child = parent.getChildAt(i)
            if (child is ReflowMenuItemView && child.visibility == View.VISIBLE) {
                visiblePosition++
            }
        }
        return visiblePosition
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        mLabel.isEnabled = enabled
        mIcon.isEnabled = enabled
        if (enabled) {
            ViewCompat.setPointerIcon(this, PointerIconCompat.getSystemIcon(context, PointerIconCompat.TYPE_HAND))
        } else {
            ViewCompat.setPointerIcon(this, null)
        }
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (mActiveBackgroundEnabled && mItemData != null && mItemData!!.isCheckable && mItemData!!.isChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }
        return drawableState
    }

    fun setIcon(iconDrawable: Drawable?) {
        if (iconDrawable === mOriginalIconDrawable) return
        mOriginalIconDrawable = iconDrawable
        var wrapped = iconDrawable
        if (wrapped != null) {
            val state = wrapped.constantState
            wrapped = DrawableCompat.wrap(if (state == null) wrapped else state.newDrawable()).mutate()
            mWrappedIconDrawable = wrapped
            if (mIconTint != null) DrawableCompat.setTintList(mWrappedIconDrawable!!, mIconTint)
        }
        mIcon.setImageDrawable(wrapped)
    }

    fun setIconTintList(tint: ColorStateList?) {
        mIconTint = tint
        if (mItemData != null && mWrappedIconDrawable != null) {
            DrawableCompat.setTintList(mWrappedIconDrawable!!, mIconTint)
            mWrappedIconDrawable!!.invalidateSelf()
        }
    }

    fun setIconSize(iconSize: Int) {
        val iconParams = mIcon.layoutParams as LinearLayoutCompat.LayoutParams
        iconParams.width = iconSize
        iconParams.height = iconSize
        mIcon.layoutParams = iconParams
    }

    fun setTextAppearanceInactive(@StyleRes inactiveTextAppearance: Int) {
        TextViewCompat.setTextAppearance(mLabel, inactiveTextAppearance)
    }

    fun setTextAppearanceActive(@StyleRes activeTextAppearance: Int) {
        TextViewCompat.setTextAppearance(mLabel, activeTextAppearance)
    }

    fun setTextColor(color: ColorStateList?) {
        if (color != null) mLabel.setTextColor(color)
    }

    fun setItemBackground(background: Int) {
        setItemBackground(if (background == 0) null else ContextCompat.getDrawable(context, background))
    }

    fun setItemBackground(background: Drawable?) {
        val bg = if (background?.constantState != null) background.constantState!!.newDrawable().mutate() else background
        ViewCompat.setBackground(this, bg)
    }

    fun setActiveBackgroundEnabled(enabled: Boolean) {
        mActiveBackgroundEnabled = enabled
        requestLayout()
    }
}
