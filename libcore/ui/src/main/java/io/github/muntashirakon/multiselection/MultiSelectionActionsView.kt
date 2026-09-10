// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.multiselection

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.DimenRes
import androidx.annotation.Dimension
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.menu.MenuView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.customview.view.AbsSavedState
import com.google.android.material.internal.ThemeEnforcement
import com.google.android.material.internal.ThemeEnforcement.obtainTintedStyledAttributes
import com.google.android.material.resources.MaterialResources
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.MaterialShapeUtils
import com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.UiUtils

@SuppressLint("RestrictedApi")
@Suppress("unused")
class MultiSelectionActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = R.attr.multiSelectionActionsView,
    @StyleRes defStyleRes: Int = R.style.Widget_AppTheme_MultiSelectionActionsView
) : LinearLayoutCompat(wrap(context, attrs, defStyleAttr, defStyleRes), attrs, defStyleAttr), MenuView {

    fun interface OnItemSelectedListener {
        fun onNavigationItemSelected(item: MenuItem): Boolean
    }

    private val mMenu: MultiSelectionActionsMenu
    private val mVisibleMenuItems: MutableList<MenuItemImpl> = ArrayList()
    private val mPresenter: MultiSelectionActionsMenuPresenter = MultiSelectionActionsMenuPresenter()
    private var mMenuInflater: MenuInflater? = null
    private var mMenuBuilder: MenuBuilder? = null
    var onItemSelectedListener: OnItemSelectedListener? = null

    private val mItemTextColorDefault: ColorStateList?
    private var mItemIconTint: ColorStateList? = null
    @Dimension private var mItemIconSize: Int = 0
    private var mItemTextColorFromUser: ColorStateList? = null
    @StyleRes private var mItemTextAppearanceInactive: Int = 0
    @StyleRes private var mItemTextAppearanceActive: Int = 0
    private var mItemBackground: Drawable? = null
    private var mItemBackgroundRes: Int = 0
    private var mItemActiveBackgroundEnabled: Boolean = false

    companion object {
        private const val MENU_PRESENTER_ID = 1
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
        private val DISABLED_STATE_SET = intArrayOf(-android.R.attr.state_enabled)
    }

    init {
        val wrappedContext = getContext()
        mItemTextColorDefault = createDefaultColorStateList(android.R.attr.textColorSecondary)

        val attributes = obtainTintedStyledAttributes(
            wrappedContext, attrs, R.styleable.MultiSelectionActionsView, defStyleAttr, defStyleRes,
            R.styleable.MultiSelectionActionsView_itemTextAppearanceInactive,
            R.styleable.MultiSelectionActionsView_itemTextAppearanceActive
        )

        mItemIconTint = if (attributes.hasValue(R.styleable.MultiSelectionActionsView_itemIconTint)) {
            attributes.getColorStateList(R.styleable.MultiSelectionActionsView_itemIconTint)
        } else {
            createDefaultColorStateList(android.R.attr.textColorSecondary)
        }

        mItemIconSize = attributes.getDimensionPixelSize(
            R.styleable.MultiSelectionActionsView_itemIconSize,
            resources.getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_navigation_bar_item_default_icon_size)
        )

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_itemTextAppearanceInactive)) {
            mItemTextAppearanceInactive = attributes.getResourceId(R.styleable.MultiSelectionActionsView_itemTextAppearanceInactive, 0)
        }
        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_itemTextAppearanceActive)) {
            mItemTextAppearanceActive = attributes.getResourceId(R.styleable.MultiSelectionActionsView_itemTextAppearanceActive, 0)
        }
        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_itemTextColor)) {
            mItemTextColorFromUser = attributes.getColorStateList(R.styleable.MultiSelectionActionsView_itemTextColor)
        }

        if (background == null || background is ColorDrawable) {
            ViewCompat.setBackground(this, createMaterialShapeDrawableBackground(wrappedContext))
        }

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_elevation)) {
            elevation = attributes.getDimensionPixelSize(R.styleable.MultiSelectionActionsView_elevation, 0).toFloat()
        }

        val backgroundTint = MaterialResources.getColorStateList(
            wrappedContext, attributes, R.styleable.MultiSelectionActionsView_backgroundTint
        )
        DrawableCompat.setTintList(background.mutate(), backgroundTint)

        val itemBackground = attributes.getResourceId(R.styleable.MultiSelectionActionsView_itemBackground, 0)
        if (itemBackground != 0) mItemBackgroundRes = itemBackground

        orientation = HORIZONTAL
        mPresenter.setMenuView(this)
        mPresenter.setId(MENU_PRESENTER_ID)
        mMenu = MultiSelectionActionsMenu(wrappedContext)
        mMenu.addMenuPresenter(mPresenter)
        mPresenter.initForMenu(wrappedContext, mMenu)

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_menu)) {
            inflateMenu(attributes.getResourceId(R.styleable.MultiSelectionActionsView_menu, 0))
        }
        attributes.recycle()

        mMenu.setCallback(object : MenuBuilder.Callback {
            override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem): Boolean =
                onItemSelectedListener != null && !onItemSelectedListener!!.onNavigationItemSelected(item)
            override fun onMenuModeChange(menu: MenuBuilder) {}
        })
    }

    override fun initialize(menu: MenuBuilder) { mMenuBuilder = menu }
    override fun getWindowAnimations(): Int = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) renderItems()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MaterialShapeUtils.setParentAbsoluteElevation(this)
    }

    override fun setElevation(elevation: Float) {
        super.setElevation(elevation)
        MaterialShapeUtils.setElevation(this, elevation)
    }

    fun getMenu(): MultiSelectionActionsMenu = mMenu

    @Deprecated("Use setItemBackground(Drawable) instead", ReplaceWith("getItemBackground()"))
    @DrawableRes
    fun getItemBackgroundResource(): Int = mItemBackgroundRes

    fun setItemBackgroundResource(@DrawableRes resId: Int) { mItemBackgroundRes = resId; renderItems() }

    var itemBackground: Drawable?
        get() = mItemBackground
        set(value) { mItemBackground = value; renderItems() }

    var itemActiveBackgroundEnabled: Boolean
        get() = mItemActiveBackgroundEnabled
        set(value) { mItemActiveBackgroundEnabled = value; renderItems() }

    var itemIconTintList: ColorStateList?
        get() = mItemIconTint
        set(value) { mItemIconTint = value; renderItems() }

    @get:Dimension
    var itemIconSize: Int
        get() = mItemIconSize
        set(value) { mItemIconSize = value; renderItems() }

    fun setItemIconSizeRes(@DimenRes iconSizeRes: Int) { itemIconSize = resources.getDimensionPixelSize(iconSizeRes) }

    var itemTextColor: ColorStateList?
        get() = mItemTextColorFromUser
        set(value) { mItemTextColorFromUser = value; renderItems() }

    @get:StyleRes
    var itemTextAppearanceInactive: Int
        get() = mItemTextAppearanceInactive
        set(@StyleRes value) { mItemTextAppearanceInactive = value; renderItems() }

    @get:StyleRes
    var itemTextAppearanceActive: Int
        get() = mItemTextAppearanceActive
        set(@StyleRes value) { mItemTextAppearanceActive = value; renderItems() }

    fun inflateMenu(resId: Int) {
        mPresenter.setUpdateSuspended(true)
        getMenuInflater().inflate(resId, mMenu)
        mPresenter.setUpdateSuspended(false)
        mPresenter.updateMenuView(true)
    }

    fun updateMenuView() {
        val menuBuilder = mMenuBuilder ?: return
        mVisibleMenuItems.clear()
        mVisibleMenuItems.addAll(menuBuilder.visibleItems)
        renderItems()
    }

    fun buildMenuView() = updateMenuView()

    @SuppressLint("ClickableViewAccessibility")
    private fun renderItems() {
        if (width == 0) return
        val wasLayoutRequested = isLayoutRequested
        val widthDp = UiUtils.pxToDp(context, width)
        val minButtonWidthDp = 80
        val maxButtons = widthDp / minButtonWidthDp
        val usableButtonCount = if (mVisibleMenuItems.size <= maxButtons) mVisibleMenuItems.size else maxButtons - 1
        val allocatedWidth = width / maxButtons
        val renderableItems = mVisibleMenuItems.subList(0, usableButtonCount)
        val overflowItems = if (renderableItems.size < mVisibleMenuItems.size)
            mVisibleMenuItems.subList(usableButtonCount, mVisibleMenuItems.size)
        else emptyList()
        removeAllViews()
        for (menuItem in renderableItems) addNewOptionItemFromMenuItem(menuItem, allocatedWidth)
        if (overflowItems.isNotEmpty()) {
            val overflowView = getNewOptionItem()
            addView(overflowView, LinearLayoutCompat.LayoutParams(allocatedWidth, ViewGroup.LayoutParams.MATCH_PARENT))
            val tooltipText = context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description)
            overflowView.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_more_horiz))
            overflowView.setTitle(tooltipText)
            overflowView.contentDescription = tooltipText
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) TooltipCompat.setTooltipText(this, tooltipText)
            overflowView.setOnClickListener {
                val overflowMenu = PopupMenu(context, overflowView)
                overflowMenu.setForceShowIcon(true)
                val menu: Menu = overflowMenu.menu
                for (i in overflowItems.size - 1 downTo 0) addMenuFromMenuItem(menu, overflowItems[i])
                overflowMenu.show()
            }
        }
        if (wasLayoutRequested) post { requestLayout() }
    }

    private fun addMenuFromMenuItem(menu: Menu, menuItem: MenuItemImpl) {
        val newItem = menu.add(menuItem.itemId)
            .setCheckable(menuItem.isCheckable)
            .setChecked(menuItem.isChecked)
            .setEnabled(menuItem.isEnabled)
            .setIcon(menuItem.icon)
            .setTitle(menuItem.title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !TextUtils.isEmpty(menuItem.contentDescription)) {
            newItem.contentDescription = menuItem.contentDescription
            newItem.tooltipText = if (!TextUtils.isEmpty(menuItem.tooltipText)) menuItem.tooltipText else menuItem.title
        }
        newItem.setOnMenuItemClickListener { mMenu.performItemAction(menuItem, mPresenter, 0) }
    }

    private fun addNewOptionItemFromMenuItem(menuItem: MenuItemImpl, width: Int) {
        val item = getNewOptionItem()
        item.initialize(menuItem)
        item.setOnClickListener { mMenu.performItemAction(menuItem, mPresenter, 0) }
        addView(item, LinearLayoutCompat.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun getNewOptionItem(): ReflowMenuItemView {
        return ReflowMenuItemView(context).also {
            it.setIconTintList(mItemIconTint)
            it.setIconSize(mItemIconSize)
            it.setTextColor(mItemTextColorDefault)
            it.setTextAppearanceInactive(mItemTextAppearanceInactive)
            it.setTextAppearanceActive(mItemTextAppearanceActive)
            it.setTextColor(mItemTextColorFromUser)
            it.setActiveBackgroundEnabled(mItemActiveBackgroundEnabled)
            if (mItemBackground != null) it.setItemBackground(mItemBackground) else it.setItemBackground(mItemBackgroundRes)
        }
    }

    private fun getMenuInflater(): MenuInflater {
        if (mMenuInflater == null) mMenuInflater = SupportMenuInflater(context)
        return mMenuInflater!!
    }

    fun createDefaultColorStateList(baseColorThemeAttr: Int): ColorStateList? {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(baseColorThemeAttr, value, true)) return null
        val baseColor = AppCompatResources.getColorStateList(context, value.resourceId) ?: return null
        if (!context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true)) return null
        val colorPrimary = value.data
        val defaultColor = baseColor.defaultColor
        return ColorStateList(
            arrayOf(DISABLED_STATE_SET, CHECKED_STATE_SET, EMPTY_STATE_SET),
            intArrayOf(baseColor.getColorForState(DISABLED_STATE_SET, defaultColor), colorPrimary, defaultColor)
        )
    }

    private fun createMaterialShapeDrawableBackground(context: Context): MaterialShapeDrawable {
        val drawable = MaterialShapeDrawable()
        val bg = background
        if (bg is ColorDrawable) drawable.fillColor = ColorStateList.valueOf(bg.color)
        drawable.initializeElevationOverlay(context)
        return drawable
    }

    override fun onSaveInstanceState(): Parcelable {
        val savedState = SavedState(super.onSaveInstanceState())
        savedState.menuPresenterState = Bundle()
        mMenu.savePresenterStates(savedState.menuPresenterState)
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) { super.onRestoreInstanceState(state); return }
        super.onRestoreInstanceState(state.superState)
        mMenu.restorePresenterStates(state.menuPresenterState)
    }

    internal class SavedState : AbsSavedState {
        var menuPresenterState: Bundle? = null

        constructor(superState: Parcelable?) : super(superState ?: EMPTY_STATE)

        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            readFromParcel(source, loader ?: this::class.java.classLoader)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeBundle(menuPresenterState)
        }

        private fun readFromParcel(source: Parcel, loader: ClassLoader?) {
            menuPresenterState = source.readBundle(loader)
        }

        companion object CREATOR : Parcelable.ClassLoaderCreator<SavedState> {
            override fun createFromParcel(source: Parcel, loader: ClassLoader?): SavedState = SavedState(source, loader)
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source, null)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
