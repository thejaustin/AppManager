// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.ui.R

@Suppress("UnusedReturnValue")
class FullScreenDialogTitleBuilder(private val mContext: Context) {
    @StringRes
    private var mTitleRes: Int = 0
    private var mTitle: CharSequence? = null
    private var mOnCloseButtonClickListener: DialogInterface.OnClickListener? = null
    @StringRes
    private var mCloseIconDescriptionRes: Int = android.R.string.cancel
    private var mCloseIconDescription: CharSequence? = null
    private var mCloseButtonIcon: Drawable? = null
    private var mPositiveButtonIcon: Drawable? = null
    @StringRes
    private var mPositiveButtonTextRes: Int = 0
    private var mPositiveButtonText: CharSequence? = null
    private var mOnPositiveButtonClickListener: DialogInterface.OnClickListener? = null
    private var mExitOnButtonPress: Boolean = true

    fun setTitle(title: CharSequence?): FullScreenDialogTitleBuilder {
        mTitle = title
        return this
    }

    fun setTitle(@StringRes titleId: Int): FullScreenDialogTitleBuilder {
        mTitleRes = titleId
        return this
    }

    fun setExitOnButtonPress(exitOnButtonPress: Boolean): FullScreenDialogTitleBuilder {
        mExitOnButtonPress = exitOnButtonPress
        return this
    }

    fun setCloseButtonIcon(closeButtonIcon: Drawable?): FullScreenDialogTitleBuilder {
        mCloseButtonIcon = closeButtonIcon
        return this
    }

    fun setOnCloseButtonClickListener(listener: DialogInterface.OnClickListener?): FullScreenDialogTitleBuilder {
        mOnCloseButtonClickListener = listener
        return this
    }

    fun setCloseIconContentDescription(closeIconDescription: CharSequence?): FullScreenDialogTitleBuilder {
        mCloseIconDescription = closeIconDescription
        return this
    }

    fun setCloseIconContentDescription(@StringRes strRes: Int): FullScreenDialogTitleBuilder {
        mCloseIconDescriptionRes = strRes
        return this
    }

    fun setPositiveButtonIcon(positiveButtonIcon: Drawable): FullScreenDialogTitleBuilder {
        mPositiveButtonIcon = positiveButtonIcon
        return this
    }

    fun setPositiveButtonText(text: CharSequence?): FullScreenDialogTitleBuilder {
        mPositiveButtonText = text
        return this
    }

    fun setPositiveButtonText(@StringRes textId: Int): FullScreenDialogTitleBuilder {
        mPositiveButtonTextRes = textId
        return this
    }

    fun setOnPositiveButtonClickListener(listener: DialogInterface.OnClickListener): FullScreenDialogTitleBuilder {
        mOnPositiveButtonClickListener = listener
        return this
    }

    fun build(dialog: DialogInterface): View {
        val v = View.inflate(mContext, R.layout.dialog_title_toolbar, null)
        val toolbar: MaterialToolbar = v.findViewById(R.id.toolbar)
        if (mTitle != null) toolbar.title = mTitle
        else if (mTitleRes != 0) toolbar.setTitle(mTitleRes)
        if (mCloseButtonIcon != null) {
            toolbar.navigationIcon = mCloseButtonIcon
        }
        // TODO: 28/1/22 Use Handler instead of lamda functions
        if (mOnCloseButtonClickListener != null) {
            toolbar.setNavigationOnClickListener {
                mOnCloseButtonClickListener!!.onClick(dialog, AlertDialog.BUTTON_NEGATIVE)
                if (mExitOnButtonPress) dialog.dismiss()
            }
        } else {
            toolbar.setNavigationOnClickListener { dialog.dismiss() }
        }
        if (mCloseIconDescription != null) {
            toolbar.navigationContentDescription = mCloseIconDescription
        } else {
            toolbar.setNavigationContentDescription(mCloseIconDescriptionRes)
        }
        val positiveButton: MaterialButton = v.findViewById(android.R.id.button1)
        if (mOnPositiveButtonClickListener != null) {
            positiveButton.setOnClickListener {
                mOnPositiveButtonClickListener!!.onClick(dialog, AlertDialog.BUTTON_POSITIVE)
                if (mExitOnButtonPress) dialog.dismiss()
            }
        } else {
            positiveButton.setOnClickListener { dialog.dismiss() }
        }
        if (mPositiveButtonIcon != null) {
            // Set icon only, remove text
            positiveButton.icon = mPositiveButtonIcon
            positiveButton.iconPadding = 0
            positiveButton.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_TOP
            positiveButton.text = null
            if (mPositiveButtonText != null) {
                positiveButton.contentDescription = mPositiveButtonText
            } else if (mPositiveButtonTextRes != 0) {
                positiveButton.contentDescription = mContext.getString(mPositiveButtonTextRes)
            }
        } else if (mPositiveButtonText != null) {
            positiveButton.text = mPositiveButtonText
        } else if (mPositiveButtonTextRes != 0) {
            positiveButton.setText(mPositiveButtonTextRes)
        } else {
            positiveButton.visibility = View.GONE
        }
        return v
    }
}
