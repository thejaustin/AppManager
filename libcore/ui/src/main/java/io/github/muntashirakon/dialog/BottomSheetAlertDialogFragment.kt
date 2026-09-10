// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.method.MovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.LinearLayoutCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.ui.R

open class BottomSheetAlertDialogFragment : CapsuleBottomSheetDialogFragment() {

    private lateinit var mMessageContainer: LinearLayoutCompat
    private lateinit var mMessageView: MaterialTextView
    private lateinit var mActionContainer: RelativeLayout
    private lateinit var mActionPrimary: MaterialButton
    private lateinit var mActionSecondary: MaterialButton
    private lateinit var mActionMore: MaterialButton
    private lateinit var mDialogTitleBuilder: DialogTitleBuilder

    override fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_bottom_sheet_alert, container, false)
        mDialogTitleBuilder = DialogTitleBuilder(view.context)
        mMessageContainer = view.findViewById(R.id.container)
        mMessageView = view.findViewById(android.R.id.text1)
        mActionContainer = view.findViewById(R.id.action_container)
        mActionPrimary = view.findViewById(R.id.action_primary)
        mActionSecondary = view.findViewById(R.id.action_secondary)
        mActionMore = view.findViewById(R.id.action_more)
        return view
    }

    @CallSuper
    override fun onBodyInitialized(bodyView: View, savedInstanceState: Bundle?) {
        val args = arguments ?: return
        val title = args.getCharSequence(ARG_TITLE)
        val subtitle = args.getCharSequence(ARG_SUBTITLE)
        val message = args.getCharSequence(ARG_MESSAGE)
        if (title != null) setTitle(title)
        if (subtitle != null) setSubtitle(subtitle)
        if (message != null) setMessage(message)
    }

    fun setTitle(@StringRes title: Int) {
        mDialogTitleBuilder.setTitle(title)
        setHeader(mDialogTitleBuilder.build())
    }

    fun setTitle(title: CharSequence?) {
        mDialogTitleBuilder.setTitle(title)
        setHeader(mDialogTitleBuilder.build())
    }

    fun setSubtitle(@StringRes subtitle: Int) {
        mDialogTitleBuilder.setSubtitle(subtitle)
        setHeader(mDialogTitleBuilder.build())
    }

    fun setSubtitle(subtitle: CharSequence?) {
        mDialogTitleBuilder.setSubtitle(subtitle)
        setHeader(mDialogTitleBuilder.build())
    }

    fun setStartIcon(@DrawableRes drawable: Int) {
        mDialogTitleBuilder.setStartIcon(drawable)
        setHeader(mDialogTitleBuilder.build())
    }

    fun setStartIcon(drawable: Drawable?) {
        mDialogTitleBuilder.setStartIcon(drawable)
        setHeader(mDialogTitleBuilder.build())
    }

    fun setEndIcon(@DrawableRes drawable: Int, @StringRes contentDescription: Int, clickListener: View.OnClickListener?) {
        mDialogTitleBuilder.setEndIcon(drawable, clickListener)
        mDialogTitleBuilder.setEndIconContentDescription(contentDescription)
        setHeader(mDialogTitleBuilder.build())
    }

    fun setEndIcon(drawable: Drawable?, contentDescription: CharSequence?, clickListener: View.OnClickListener?) {
        mDialogTitleBuilder.setEndIcon(drawable, clickListener)
        mDialogTitleBuilder.setEndIconContentDescription(contentDescription)
        setHeader(mDialogTitleBuilder.build())
    }

    fun setMessage(@StringRes message: Int) {
        mMessageView.setText(message)
    }

    fun setMessage(message: CharSequence?) {
        mMessageView.text = message
    }

    fun setMessageIsSelectable(selectable: Boolean) {
        mMessageView.setTextIsSelectable(selectable)
    }

    fun setMessageMovementMethod(movementMethod: MovementMethod) {
        mMessageView.movementMethod = movementMethod
    }

    fun setPrimaryAction(@StringRes title: Int, onClickListener: View.OnClickListener?) {
        if (mActionContainer.visibility == View.GONE) mActionContainer.visibility = View.VISIBLE
        if (mActionPrimary.visibility == View.GONE) mActionPrimary.visibility = View.VISIBLE
        mActionPrimary.setText(title)
        mActionPrimary.setOnClickListener(onClickListener)
    }

    fun setPrimaryAction(title: CharSequence?, onClickListener: View.OnClickListener?) {
        if (mActionContainer.visibility == View.GONE) mActionContainer.visibility = View.VISIBLE
        if (mActionPrimary.visibility == View.GONE) mActionPrimary.visibility = View.VISIBLE
        mActionPrimary.text = title
        mActionPrimary.setOnClickListener(onClickListener)
    }

    fun setSecondaryAction(@StringRes title: Int, onClickListener: View.OnClickListener?) {
        if (mActionContainer.visibility == View.GONE) mActionContainer.visibility = View.VISIBLE
        if (mActionSecondary.visibility == View.GONE) mActionSecondary.visibility = View.VISIBLE
        mActionSecondary.setText(title)
        mActionSecondary.setOnClickListener(onClickListener)
    }

    fun setSecondaryAction(title: CharSequence?, onClickListener: View.OnClickListener?) {
        if (mActionContainer.visibility == View.GONE) mActionContainer.visibility = View.VISIBLE
        if (mActionSecondary.visibility == View.GONE) mActionSecondary.visibility = View.VISIBLE
        mActionSecondary.text = title
        mActionSecondary.setOnClickListener(onClickListener)
    }

    fun prependView(view: View, layoutParams: LinearLayoutCompat.LayoutParams) {
        mMessageContainer.addView(view, 0, layoutParams)
    }

    fun appendView(view: View, layoutParams: ViewGroup.LayoutParams) {
        mMessageContainer.addView(view, layoutParams)
    }

    companion object {
        protected const val ARG_TITLE = "title"
        protected const val ARG_SUBTITLE = "subtitle"
        protected const val ARG_MESSAGE = "message"

        @JvmStatic
        protected fun getArgs(title: CharSequence?, subtitle: CharSequence?, message: CharSequence?): Bundle {
            return Bundle().apply {
                putCharSequence(ARG_TITLE, title)
                putCharSequence(ARG_SUBTITLE, subtitle)
                putCharSequence(ARG_MESSAGE, message)
            }
        }
    }
}
