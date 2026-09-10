// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.content.Context
import android.content.DialogInterface
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.util.LinkifyCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.ui.R

@Suppress("unused")
class ScrollableDialogBuilder @JvmOverloads constructor(
    context: Context,
    message: CharSequence? = null,
    fullScreen: Boolean = false,
) {
    private val mMessage: MaterialTextView
    private val mCheckBox: MaterialCheckBox
    private val mBuilder: AlertDialogBuilder

    fun interface OnClickListener {
        fun onClick(dialog: DialogInterface, which: Int, isChecked: Boolean)
    }

    constructor(context: Context, fullScreen: Boolean) : this(context, null, fullScreen)

    constructor(context: Context, @StringRes inputTextLabel: Int) : this(context, context.getText(inputTextLabel))

    init {
        val view = View.inflate(context, R.layout.dialog_scrollable_text_view, null)
        mMessage = view.findViewById(android.R.id.content)
        mMessage.text = message
        mCheckBox = view.findViewById(android.R.id.checkbox)
        mCheckBox.visibility = View.GONE
        mBuilder = AlertDialogBuilder(context, fullScreen).setView(view)
    }

    fun setTitle(title: View?): ScrollableDialogBuilder {
        mBuilder.setCustomTitle(title)
        return this
    }

    fun setTitle(title: CharSequence?): ScrollableDialogBuilder {
        mBuilder.setTitle(title)
        return this
    }

    fun setTitle(@StringRes title: Int): ScrollableDialogBuilder {
        mBuilder.setTitle(title)
        return this
    }

    fun setMessage(@StringRes message: Int): ScrollableDialogBuilder {
        mMessage.setText(message)
        return this
    }

    fun setMessage(message: CharSequence?): ScrollableDialogBuilder {
        mMessage.text = message
        return this
    }

    fun setSelectable(selectable: Boolean): ScrollableDialogBuilder {
        mMessage.setTextIsSelectable(selectable)
        return this
    }

    fun enableAnchors(): ScrollableDialogBuilder {
        mMessage.movementMethod = LinkMovementMethod.getInstance()
        return this
    }

    fun linkify(@LinkifyCompat.LinkifyMask mask: Int): ScrollableDialogBuilder {
        LinkifyCompat.addLinks(mMessage, mask)
        return this
    }

    fun linkifyAll(): ScrollableDialogBuilder = linkify(Linkify.ALL)

    fun setCheckboxLabel(checkboxLabel: CharSequence?): ScrollableDialogBuilder {
        if (checkboxLabel != null) {
            mCheckBox.visibility = View.VISIBLE
            mCheckBox.text = checkboxLabel
        } else {
            mCheckBox.visibility = View.GONE
        }
        return this
    }

    fun setCheckboxLabel(@StringRes checkboxLabel: Int): ScrollableDialogBuilder {
        if (checkboxLabel != 0) {
            mCheckBox.visibility = View.VISIBLE
            mCheckBox.setText(checkboxLabel)
        } else {
            mCheckBox.visibility = View.GONE
        }
        return this
    }

    fun setPositiveButton(@StringRes textId: Int, listener: OnClickListener?): ScrollableDialogBuilder {
        mBuilder.setPositiveButton(textId) { dialog, which ->
            listener?.onClick(dialog, which, mCheckBox.isChecked)
        }
        return this
    }

    fun setPositiveButton(text: CharSequence, listener: OnClickListener?): ScrollableDialogBuilder {
        mBuilder.setPositiveButton(text) { dialog, which ->
            listener?.onClick(dialog, which, mCheckBox.isChecked)
        }
        return this
    }

    fun setNegativeButton(@StringRes textId: Int, listener: OnClickListener?): ScrollableDialogBuilder {
        mBuilder.setNegativeButton(textId) { dialog, which ->
            listener?.onClick(dialog, which, mCheckBox.isChecked)
        }
        return this
    }

    fun setNegativeButton(text: CharSequence, listener: OnClickListener?): ScrollableDialogBuilder {
        mBuilder.setNegativeButton(text) { dialog, which ->
            listener?.onClick(dialog, which, mCheckBox.isChecked)
        }
        return this
    }

    fun setNeutralButton(@StringRes textId: Int, listener: OnClickListener?): ScrollableDialogBuilder {
        mBuilder.setNeutralButton(textId) { dialog, which ->
            listener?.onClick(dialog, which, mCheckBox.isChecked)
        }
        return this
    }

    fun setNeutralButton(text: CharSequence, listener: OnClickListener?): ScrollableDialogBuilder {
        mBuilder.setNeutralButton(text) { dialog, which ->
            listener?.onClick(dialog, which, mCheckBox.isChecked)
        }
        return this
    }

    fun create(): AlertDialog = mBuilder.create()

    fun show() {
        create().show()
    }
}
