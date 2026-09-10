// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnCancelListener
import android.content.DialogInterface.OnClickListener
import android.content.DialogInterface.OnDismissListener
import android.content.DialogInterface.OnKeyListener
import android.content.DialogInterface.OnMultiChoiceClickListener
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Build.VERSION_CODES
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ListAdapter
import androidx.annotation.ArrayRes
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.InsetDialogOnTouchListener
import com.google.android.material.dialog.MaterialDialogs
import com.google.android.material.resources.MaterialAttributes
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.UiUtils

/**
 * An extension of [AlertDialog.Builder] for use with a Material theme (e.g.,
 * Theme.MaterialComponents).
 *
 * <p>This Builder must be used in order for AlertDialog objects to respond to color and shape
 * theming provided by Material themes.
 *
 * <p>The type of dialog returned is still an [AlertDialog]; there is no specific Material
 * implementation of [AlertDialog].
 */
// Copyright 2018 The Android Open Source Project
open class AlertDialogBuilder : AlertDialog.Builder {
    private val mTitleBuilder: FullScreenDialogTitleBuilder?
    private val mBackgroundInsets: Rect
    private val mFullScreenMode: Boolean

    private var mBackground: Drawable? = null
    private var mExitOnButtonPress = true
    private var mPositiveButtonListener: OnClickListener? = null
    private var mNegativeButtonListener: OnClickListener? = null
    private var mNeutralButtonListener: OnClickListener? = null

    constructor(context: Context) : this(context, 0)

    constructor(context: Context, fullScreenMode: Boolean) : this(
        context,
        if (supportsFullScreen(context, fullScreenMode)) UiUtils.getStyle(context, R.attr.materialFullScreenAlertDialogTheme) else 0,
        fullScreenMode
    )

    constructor(context: Context, @StyleRes overrideThemeResId: Int) : this(context, overrideThemeResId, false)

    @SuppressLint("RestrictedApi")
    constructor(context: Context, @StyleRes overrideThemeResId: Int, fullScreenMode: Boolean) : super(
        // Only pass in 0 for overrideThemeResId if both overrideThemeResId and
        // MATERIAL_ALERT_DIALOG_THEME_OVERLAY are 0 otherwise alertDialogTheme will override both.
        createMaterialAlertDialogThemedContext(context),
        getOverridingThemeResId(context, overrideThemeResId)
    ) {
        mFullScreenMode = supportsFullScreen(context, fullScreenMode)
        // Ensure we are using the correctly themed context rather than the context that was passed in.
        val themedContext = getContext()
        val theme = themedContext.theme
        mTitleBuilder = if (fullScreenMode) FullScreenDialogTitleBuilder(themedContext) else null

        mBackgroundInsets = MaterialDialogs.getDialogBackgroundInsets(themedContext, DEF_STYLE_ATTR, DEF_STYLE_RES)

        val surfaceColor = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorSurface, javaClass.canonicalName)
        val materialShapeDrawable = MaterialShapeDrawable(themedContext, null, DEF_STYLE_ATTR, DEF_STYLE_RES)
        materialShapeDrawable.initializeElevationOverlay(themedContext)
        materialShapeDrawable.fillColor = ColorStateList.valueOf(surfaceColor)

        // dialogCornerRadius first appeared in Android Pie
        if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
            val dialogCornerRadiusValue = TypedValue()
            theme.resolveAttribute(android.R.attr.dialogCornerRadius, dialogCornerRadiusValue, true)
            val dialogCornerRadius = dialogCornerRadiusValue.getDimension(getContext().resources.displayMetrics)
            if (dialogCornerRadiusValue.type == TypedValue.TYPE_DIMENSION && dialogCornerRadius >= 0) {
                materialShapeDrawable.setCornerSize(dialogCornerRadius)
            }
        }
        mBackground = materialShapeDrawable
    }

    @SuppressLint("RestrictedApi")
    override fun create(): AlertDialog {
        val alertDialog = super.create()
        val window = alertDialog.window
        // TODO: 28/1/22 Use Handler instead of lamda functions
        if (mFullScreenMode && mTitleBuilder != null) {
            alertDialog.setOnShowListener { dialog ->
                val neutralButton = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL)
                if (mNeutralButtonListener != null) {
                    neutralButton.setOnClickListener {
                        mNeutralButtonListener!!.onClick(alertDialog, DialogInterface.BUTTON_NEUTRAL)
                        if (mExitOnButtonPress) alertDialog.dismiss()
                    }
                }
            }
            mTitleBuilder.setOnPositiveButtonClickListener(mPositiveButtonListener)
            mTitleBuilder.setOnCloseButtonClickListener(mNegativeButtonListener)
            alertDialog.setCustomTitle(mTitleBuilder.build(alertDialog))
            window!!.setWindowAnimations(R.style.AppTheme_FullScreenDialog_Animation)
            // No need to set any insets
            return alertDialog
        }
        alertDialog.setOnShowListener {
            val neutralButton = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            if (mNeutralButtonListener != null) {
                neutralButton.setOnClickListener {
                    mNeutralButtonListener!!.onClick(alertDialog, DialogInterface.BUTTON_NEUTRAL)
                    if (mExitOnButtonPress) alertDialog.dismiss()
                }
            }
            val positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            if (mPositiveButtonListener != null) {
                positiveButton.setOnClickListener {
                    mPositiveButtonListener!!.onClick(alertDialog, DialogInterface.BUTTON_POSITIVE)
                    if (mExitOnButtonPress) alertDialog.dismiss()
                }
            }
            val negativeButton = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            if (mNegativeButtonListener != null) {
                negativeButton.setOnClickListener {
                    mNegativeButtonListener!!.onClick(alertDialog, DialogInterface.BUTTON_NEGATIVE)
                    if (mExitOnButtonPress) alertDialog.dismiss()
                }
            }
        }
        /* [Window.getDecorView] should be called before any changes are made to the Window
         * as it locks in attributes and affects layout. */
        val decorView = window!!.decorView
        if (mBackground is MaterialShapeDrawable) {
            (mBackground as MaterialShapeDrawable).elevation = ViewCompat.getElevation(decorView)
        }

        val insetDrawable = MaterialDialogs.insetDrawable(mBackground, mBackgroundInsets)
        window.setBackgroundDrawable(insetDrawable)
        decorView.setOnTouchListener(InsetDialogOnTouchListener(alertDialog, mBackgroundInsets))
        return alertDialog
    }

    val background: Drawable? get() = mBackground

    fun setBackground(background: Drawable?): AlertDialogBuilder {
        mBackground = background
        return this
    }

    fun setBackgroundInsetStart(@Px backgroundInsetStart: Int): AlertDialogBuilder {
        if (context.resources.configuration.layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) {
            mBackgroundInsets.right = backgroundInsetStart
        } else {
            mBackgroundInsets.left = backgroundInsetStart
        }
        return this
    }

    fun setBackgroundInsetTop(@Px backgroundInsetTop: Int): AlertDialogBuilder {
        mBackgroundInsets.top = backgroundInsetTop
        return this
    }

    fun setBackgroundInsetEnd(@Px backgroundInsetEnd: Int): AlertDialogBuilder {
        if (context.resources.configuration.layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) {
            mBackgroundInsets.left = backgroundInsetEnd
        } else {
            mBackgroundInsets.right = backgroundInsetEnd
        }
        return this
    }

    fun setBackgroundInsetBottom(@Px backgroundInsetBottom: Int): AlertDialogBuilder {
        mBackgroundInsets.bottom = backgroundInsetBottom
        return this
    }

    /**
     * Whether to exit after a button (negative, positive or neutral) has been pressed.
     */
    fun setExitOnButtonPress(exitOnButtonPress: Boolean): AlertDialogBuilder {
        mTitleBuilder?.setExitOnButtonPress(exitOnButtonPress)
        mExitOnButtonPress = exitOnButtonPress
        return this
    }

    // The following methods are all pass-through methods used to specify the return type for the
    // builder chain.

    override fun setTitle(@StringRes titleId: Int): AlertDialogBuilder {
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setTitle(titleId)
            return this
        }
        return super.setTitle(titleId) as AlertDialogBuilder
    }

    override fun setTitle(title: CharSequence?): AlertDialogBuilder {
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setTitle(title)
            return this
        }
        return super.setTitle(title) as AlertDialogBuilder
    }

    override fun setCustomTitle(customTitleView: View?): AlertDialogBuilder =
        super.setCustomTitle(customTitleView) as AlertDialogBuilder

    override fun setMessage(@StringRes messageId: Int): AlertDialogBuilder =
        super.setMessage(messageId) as AlertDialogBuilder

    override fun setMessage(message: CharSequence?): AlertDialogBuilder =
        super.setMessage(message) as AlertDialogBuilder

    override fun setIcon(@DrawableRes iconId: Int): AlertDialogBuilder =
        super.setIcon(iconId) as AlertDialogBuilder

    override fun setIcon(icon: Drawable?): AlertDialogBuilder =
        super.setIcon(icon) as AlertDialogBuilder

    override fun setIconAttribute(@AttrRes attrId: Int): AlertDialogBuilder =
        super.setIconAttribute(attrId) as AlertDialogBuilder

    override fun setPositiveButton(@StringRes textId: Int, listener: OnClickListener?): AlertDialogBuilder {
        mPositiveButtonListener = listener
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setPositiveButtonText(textId)
            return this
        }
        return super.setPositiveButton(textId, null) as AlertDialogBuilder
    }

    override fun setPositiveButton(text: CharSequence?, listener: OnClickListener?): AlertDialogBuilder {
        mPositiveButtonListener = listener
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setPositiveButtonText(text)
            return this
        }
        return super.setPositiveButton(text, null) as AlertDialogBuilder
    }

    override fun setPositiveButtonIcon(icon: Drawable?): AlertDialogBuilder {
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setPositiveButtonIcon(icon)
            return this
        }
        return super.setPositiveButtonIcon(icon) as AlertDialogBuilder
    }

    override fun setNegativeButton(@StringRes textId: Int, listener: OnClickListener?): AlertDialogBuilder {
        mNegativeButtonListener = listener
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setCloseIconContentDescription(textId)
            return this
        }
        return super.setNegativeButton(textId, null) as AlertDialogBuilder
    }

    override fun setNegativeButton(text: CharSequence?, listener: OnClickListener?): AlertDialogBuilder {
        mNegativeButtonListener = listener
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setCloseIconContentDescription(text)
            return this
        }
        return super.setNegativeButton(text, null) as AlertDialogBuilder
    }

    override fun setNegativeButtonIcon(icon: Drawable?): AlertDialogBuilder {
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setCloseButtonIcon(icon)
            return this
        }
        return super.setNegativeButtonIcon(icon) as AlertDialogBuilder
    }

    override fun setNeutralButton(@StringRes textId: Int, listener: OnClickListener?): AlertDialogBuilder {
        mNeutralButtonListener = listener
        return super.setNeutralButton(textId, null) as AlertDialogBuilder
    }

    override fun setNeutralButton(text: CharSequence?, listener: OnClickListener?): AlertDialogBuilder {
        mNeutralButtonListener = listener
        return super.setNeutralButton(text, null) as AlertDialogBuilder
    }

    override fun setNeutralButtonIcon(icon: Drawable?): AlertDialogBuilder =
        super.setNeutralButtonIcon(icon) as AlertDialogBuilder

    override fun setCancelable(cancelable: Boolean): AlertDialogBuilder =
        super.setCancelable(cancelable) as AlertDialogBuilder

    override fun setOnCancelListener(onCancelListener: OnCancelListener?): AlertDialogBuilder =
        super.setOnCancelListener(onCancelListener) as AlertDialogBuilder

    override fun setOnDismissListener(onDismissListener: OnDismissListener?): AlertDialogBuilder =
        super.setOnDismissListener(onDismissListener) as AlertDialogBuilder

    override fun setOnKeyListener(onKeyListener: OnKeyListener?): AlertDialogBuilder =
        super.setOnKeyListener(onKeyListener) as AlertDialogBuilder

    override fun setItems(@ArrayRes itemsId: Int, listener: OnClickListener?): AlertDialogBuilder =
        super.setItems(itemsId, listener) as AlertDialogBuilder

    override fun setItems(items: Array<out CharSequence>?, listener: OnClickListener?): AlertDialogBuilder =
        super.setItems(items, listener) as AlertDialogBuilder

    override fun setAdapter(adapter: ListAdapter?, listener: OnClickListener?): AlertDialogBuilder =
        super.setAdapter(adapter, listener) as AlertDialogBuilder

    override fun setCursor(cursor: Cursor?, listener: OnClickListener?, labelColumn: String): AlertDialogBuilder =
        super.setCursor(cursor, listener, labelColumn) as AlertDialogBuilder

    override fun setMultiChoiceItems(
        @ArrayRes itemsId: Int,
        checkedItems: BooleanArray?,
        listener: OnMultiChoiceClickListener?,
    ): AlertDialogBuilder = super.setMultiChoiceItems(itemsId, checkedItems, listener) as AlertDialogBuilder

    override fun setMultiChoiceItems(
        items: Array<out CharSequence>?,
        checkedItems: BooleanArray?,
        listener: OnMultiChoiceClickListener?,
    ): AlertDialogBuilder = super.setMultiChoiceItems(items, checkedItems, listener) as AlertDialogBuilder

    override fun setMultiChoiceItems(
        cursor: Cursor?,
        isCheckedColumn: String,
        labelColumn: String,
        listener: OnMultiChoiceClickListener?,
    ): AlertDialogBuilder = super.setMultiChoiceItems(cursor, isCheckedColumn, labelColumn, listener) as AlertDialogBuilder

    override fun setSingleChoiceItems(
        @ArrayRes itemsId: Int,
        checkedItem: Int,
        listener: OnClickListener?,
    ): AlertDialogBuilder = super.setSingleChoiceItems(itemsId, checkedItem, listener) as AlertDialogBuilder

    override fun setSingleChoiceItems(
        cursor: Cursor?,
        checkedItem: Int,
        labelColumn: String,
        listener: OnClickListener?,
    ): AlertDialogBuilder = super.setSingleChoiceItems(cursor, checkedItem, labelColumn, listener) as AlertDialogBuilder

    override fun setSingleChoiceItems(
        items: Array<out CharSequence>?,
        checkedItem: Int,
        listener: OnClickListener?,
    ): AlertDialogBuilder = super.setSingleChoiceItems(items, checkedItem, listener) as AlertDialogBuilder

    override fun setSingleChoiceItems(
        adapter: ListAdapter?,
        checkedItem: Int,
        listener: OnClickListener?,
    ): AlertDialogBuilder = super.setSingleChoiceItems(adapter, checkedItem, listener) as AlertDialogBuilder

    override fun setOnItemSelectedListener(listener: AdapterView.OnItemSelectedListener?): AlertDialogBuilder =
        super.setOnItemSelectedListener(listener) as AlertDialogBuilder

    override fun setView(layoutResId: Int): AlertDialogBuilder =
        super.setView(layoutResId) as AlertDialogBuilder

    override fun setView(view: View?): AlertDialogBuilder =
        super.setView(view) as AlertDialogBuilder

    companion object {
        @AttrRes
        private val DEF_STYLE_ATTR = androidx.appcompat.R.attr.alertDialogStyle

        @StyleRes
        private val DEF_STYLE_RES = com.google.android.material.R.style.MaterialAlertDialog_Material3

        @AttrRes
        private val MATERIAL_ALERT_DIALOG_THEME_OVERLAY = com.google.android.material.R.attr.materialAlertDialogTheme

        @SuppressLint("RestrictedApi")
        private fun getMaterialAlertDialogThemeOverlay(context: Context): Int {
            val materialAlertDialogThemeOverlay =
                MaterialAttributes.resolve(context, MATERIAL_ALERT_DIALOG_THEME_OVERLAY) ?: return 0
            return materialAlertDialogThemeOverlay.data
        }

        private fun createMaterialAlertDialogThemedContext(context: Context): Context {
            val themeOverlayId = getMaterialAlertDialogThemeOverlay(context)
            val themedContext = wrap(context, null, DEF_STYLE_ATTR, DEF_STYLE_RES)
            if (themeOverlayId == 0) return themedContext
            return ContextThemeWrapper(themedContext, themeOverlayId)
        }

        private fun getOverridingThemeResId(context: Context, overrideThemeResId: Int): Int =
            if (overrideThemeResId == 0) getMaterialAlertDialogThemeOverlay(context) else overrideThemeResId

        private fun supportsFullScreen(context: Context, fullScreenMode: Boolean): Boolean =
            fullScreenMode && !context.resources.getBoolean(R.bool.large_layout)
    }
}
