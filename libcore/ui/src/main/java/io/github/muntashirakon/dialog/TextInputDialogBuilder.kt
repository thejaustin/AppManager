// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.text.Editable
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.UiUtils

@Suppress("unused")
class TextInputDialogBuilder @SuppressLint("InflateParams") constructor(
    context: Context,
    inputTextLabel: CharSequence?
) {
    private val mContext: Context = context
    private val mTextInputLayout: TextInputLayout
    private val mEditText: TextInputEditText
    private val mCheckBox: MaterialCheckBox
    private val mBuilder: MaterialAlertDialogBuilder
    private var mShowListener: DialogInterface.OnShowListener? = null

    fun interface OnClickListener {
        fun onClick(dialog: DialogInterface, which: Int, inputText: Editable?, isChecked: Boolean)
    }

    constructor(context: Context, @StringRes inputTextLabel: Int) :
            this(context, context.getText(inputTextLabel))

    init {
        val view = View.inflate(context, R.layout.dialog_text_input, null)
        mTextInputLayout = view.findViewById(android.R.id.text1)
        mTextInputLayout.hint = inputTextLabel
        mEditText = view.findViewById(android.R.id.input)
        mCheckBox = view.findViewById(android.R.id.checkbox)
        mCheckBox.visibility = View.GONE
        mBuilder = MaterialAlertDialogBuilder(context).setView(view)
    }

    fun setTitle(title: CharSequence?): TextInputDialogBuilder { mBuilder.setTitle(title); return this }
    fun setTitle(@StringRes title: Int): TextInputDialogBuilder { mBuilder.setTitle(title); return this }
    fun setInputText(inputText: CharSequence?): TextInputDialogBuilder { mEditText.setText(inputText); return this }
    fun setInputText(@StringRes inputText: Int): TextInputDialogBuilder { mEditText.setText(inputText); return this }
    fun setInputTypeface(tf: Typeface): TextInputDialogBuilder { mEditText.typeface = tf; return this }
    fun setInputInputType(inputType: Int): TextInputDialogBuilder { mEditText.inputType = inputType; return this }
    fun setInputImeOptions(options: Int): TextInputDialogBuilder { mEditText.imeOptions = options; return this }

    fun setHelperText(helperText: CharSequence?): TextInputDialogBuilder {
        mTextInputLayout.helperText = helperText; return this
    }

    fun setHelperText(@StringRes helperText: Int): TextInputDialogBuilder {
        mTextInputLayout.helperText = mContext.getText(helperText); return this
    }

    fun setChecked(checked: Boolean): TextInputDialogBuilder { mCheckBox.isChecked = checked; return this }

    fun setCheckboxLabel(checkboxLabel: CharSequence?): TextInputDialogBuilder {
        if (checkboxLabel != null) { mCheckBox.visibility = View.VISIBLE; mCheckBox.text = checkboxLabel }
        else mCheckBox.visibility = View.GONE
        return this
    }

    fun setCheckboxLabel(@StringRes checkboxLabel: Int): TextInputDialogBuilder {
        if (checkboxLabel != 0) { mCheckBox.visibility = View.VISIBLE; mCheckBox.setText(checkboxLabel) }
        else mCheckBox.visibility = View.GONE
        return this
    }

    fun setPositiveButton(@StringRes textId: Int, listener: OnClickListener?): TextInputDialogBuilder {
        mBuilder.setPositiveButton(textId) { d, w -> listener?.onClick(d, w, mEditText.text, mCheckBox.isChecked) }
        return this
    }

    fun setPositiveButton(text: CharSequence, listener: OnClickListener?): TextInputDialogBuilder {
        mBuilder.setPositiveButton(text) { d, w -> listener?.onClick(d, w, mEditText.text, mCheckBox.isChecked) }
        return this
    }

    fun setNegativeButton(@StringRes textId: Int, listener: OnClickListener?): TextInputDialogBuilder {
        mBuilder.setNegativeButton(textId) { d, w -> listener?.onClick(d, w, mEditText.text, mCheckBox.isChecked) }
        return this
    }

    fun setNegativeButton(text: CharSequence, listener: OnClickListener?): TextInputDialogBuilder {
        mBuilder.setNegativeButton(text) { d, w -> listener?.onClick(d, w, mEditText.text, mCheckBox.isChecked) }
        return this
    }

    fun setNeutralButton(@StringRes textId: Int, listener: OnClickListener?): TextInputDialogBuilder {
        mBuilder.setNeutralButton(textId) { d, w -> listener?.onClick(d, w, mEditText.text, mCheckBox.isChecked) }
        return this
    }

    fun setNeutralButton(text: CharSequence, listener: OnClickListener?): TextInputDialogBuilder {
        mBuilder.setNeutralButton(text) { d, w -> listener?.onClick(d, w, mEditText.text, mCheckBox.isChecked) }
        return this
    }

    fun setOnShowListener(listener: DialogInterface.OnShowListener?): TextInputDialogBuilder {
        mShowListener = listener; return this
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener?): TextInputDialogBuilder {
        mBuilder.setOnDismissListener(listener); return this
    }

    fun setCancelable(cancelable: Boolean): TextInputDialogBuilder {
        mBuilder.setCancelable(cancelable); return this
    }

    val inputText: Editable? get() = mEditText.text

    fun create(): AlertDialog {
        val dialog = mBuilder.create()
        dialog.setOnShowListener { dialog1 ->
            mShowListener?.onShow(dialog1)
            mEditText.postDelayed({
                mEditText.requestFocus()
                mEditText.requestFocusFromTouch()
                mEditText.setSelection(mEditText.length())
                UiUtils.showKeyboard(mEditText)
            }, 200)
        }
        return dialog
    }

    fun show() = create().show()
}
