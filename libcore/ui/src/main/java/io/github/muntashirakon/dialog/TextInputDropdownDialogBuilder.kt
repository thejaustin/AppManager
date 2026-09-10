// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.text.Editable
import android.text.InputType
import android.view.View
import android.widget.AutoCompleteTextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter
import io.github.muntashirakon.adapters.NoFilterArrayAdapter
import io.github.muntashirakon.ui.R

@Suppress("unused")
class TextInputDropdownDialogBuilder @SuppressLint("InflateParams") constructor(
    private val mActivity: FragmentActivity,
    inputTextLabel: CharSequence
) {
    private val mMainInputLayout: TextInputLayout
    private val mMainInput: AutoCompleteTextView
    private val mAuxiliaryInputLayout: TextInputLayout
    private val mAuxiliaryInput: AutoCompleteTextView
    private val mCheckBox: MaterialCheckBox
    private val mBuilder: MaterialAlertDialogBuilder

    fun interface OnClickListener {
        fun onClick(dialog: DialogInterface, which: Int, inputText: Editable?, isChecked: Boolean)
    }

    constructor(activity: FragmentActivity, @StringRes inputTextLabel: Int) :
            this(activity, activity.getText(inputTextLabel))

    init {
        val view = mActivity.layoutInflater.inflate(R.layout.dialog_text_input_dropdown, null)
        mMainInputLayout = view.findViewById(android.R.id.text1)
        mMainInputLayout.hint = inputTextLabel
        mMainInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
        mMainInput = view.findViewById(android.R.id.input)
        mAuxiliaryInputLayout = view.findViewById(android.R.id.text2)
        mAuxiliaryInput = view.findViewById(android.R.id.custom)
        mAuxiliaryInputLayout.visibility = View.GONE
        mCheckBox = view.findViewById(android.R.id.checkbox)
        mCheckBox.visibility = View.GONE
        mBuilder = MaterialAlertDialogBuilder(mActivity).setView(view)
    }

    fun setTitle(title: View?): TextInputDropdownDialogBuilder { mBuilder.setCustomTitle(title); return this }
    fun setTitle(title: CharSequence?): TextInputDropdownDialogBuilder { mBuilder.setTitle(title); return this }
    fun setTitle(@StringRes title: Int): TextInputDropdownDialogBuilder { mBuilder.setTitle(title); return this }

    fun <T> setDropdownItems(items: List<T>, choice: Int, filterable: Boolean): TextInputDropdownDialogBuilder {
        val adapter = if (filterable) AnyFilterArrayAdapter(mActivity, R.layout.auto_complete_dropdown_item, items)
        else NoFilterArrayAdapter(mActivity, R.layout.auto_complete_dropdown_item, items)
        mMainInput.setAdapter(adapter)
        if (choice >= 0) mMainInput.setText(adapter.getItem(choice)?.toString() ?: "")
        mMainInputLayout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        return this
    }

    fun <T> setAuxiliaryInput(
        inputLabel: CharSequence, helperText: CharSequence?, inputText: CharSequence?,
        dropdownItems: List<T>?, isEnabled: Boolean
    ): TextInputDropdownDialogBuilder {
        mAuxiliaryInputLayout.visibility = View.VISIBLE
        mAuxiliaryInputLayout.hint = inputLabel
        mAuxiliaryInputLayout.helperText = helperText
        mAuxiliaryInput.setText(inputText)
        if (dropdownItems != null) {
            mAuxiliaryInput.setAdapter(NoFilterArrayAdapter(mActivity, R.layout.auto_complete_dropdown_item, dropdownItems))
            mAuxiliaryInputLayout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        } else {
            mAuxiliaryInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
        }
        mAuxiliaryInput.inputType = if (isEnabled)
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else InputType.TYPE_NULL
        return this
    }

    fun <T> setAuxiliaryInput(
        @StringRes inputLabel: Int, @StringRes helperText: Int?, @StringRes inputText: Int?,
        dropdownItems: List<T>?, isEnabled: Boolean
    ): TextInputDropdownDialogBuilder = setAuxiliaryInput(
        mActivity.getString(inputLabel),
        helperText?.let { mActivity.getText(it) },
        inputText?.let { mActivity.getText(it) },
        dropdownItems, isEnabled
    )

    fun setAuxiliaryInputLabel(inputText: CharSequence?): TextInputDropdownDialogBuilder {
        mAuxiliaryInputLayout.visibility = View.VISIBLE; mAuxiliaryInputLayout.hint = inputText; return this
    }

    fun setAuxiliaryInputLabel(@StringRes inputText: Int): TextInputDropdownDialogBuilder {
        mAuxiliaryInputLayout.visibility = View.VISIBLE; mAuxiliaryInputLayout.setHint(inputText); return this
    }

    fun setAuxiliaryInputHelperText(helperText: CharSequence?): TextInputDropdownDialogBuilder {
        mAuxiliaryInputLayout.helperText = helperText; return this
    }

    fun setAuxiliaryInputHelperText(@StringRes helperText: Int): TextInputDropdownDialogBuilder {
        mAuxiliaryInputLayout.helperText = mActivity.getText(helperText); return this
    }

    fun setAuxiliaryInputText(inputText: CharSequence?): TextInputDropdownDialogBuilder {
        mAuxiliaryInput.setText(inputText); return this
    }

    fun setAuxiliaryInputText(@StringRes inputText: Int): TextInputDropdownDialogBuilder {
        mAuxiliaryInput.setText(inputText); return this
    }

    val auxiliaryInput: Editable? get() = mAuxiliaryInput.text
    val inputText: Editable? get() = mMainInput.text

    fun setEnable(enable: Boolean): TextInputDropdownDialogBuilder {
        mMainInput.inputType = if (enable)
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else InputType.TYPE_NULL
        return this
    }

    fun setInputText(inputText: CharSequence?): TextInputDropdownDialogBuilder { mMainInput.setText(inputText); return this }
    fun setInputText(@StringRes inputText: Int): TextInputDropdownDialogBuilder { mMainInput.setText(inputText); return this }

    fun setHelperText(helperText: CharSequence?): TextInputDropdownDialogBuilder {
        mMainInputLayout.helperText = helperText; return this
    }

    fun setHelperText(@StringRes helperText: Int): TextInputDropdownDialogBuilder {
        mMainInputLayout.helperText = mActivity.getText(helperText); return this
    }

    fun setCheckboxLabel(checkboxLabel: CharSequence?): TextInputDropdownDialogBuilder {
        if (checkboxLabel != null) { mCheckBox.visibility = View.VISIBLE; mCheckBox.text = checkboxLabel }
        else mCheckBox.visibility = View.GONE
        return this
    }

    fun setCheckboxLabel(@StringRes checkboxLabel: Int): TextInputDropdownDialogBuilder {
        if (checkboxLabel != 0) { mCheckBox.visibility = View.VISIBLE; mCheckBox.setText(checkboxLabel) }
        else mCheckBox.visibility = View.GONE
        return this
    }

    fun setPositiveButton(@StringRes textId: Int, listener: OnClickListener?): TextInputDropdownDialogBuilder {
        mBuilder.setPositiveButton(textId) { d, w -> listener?.onClick(d, w, mMainInput.text, mCheckBox.isChecked) }; return this
    }

    fun setPositiveButton(text: CharSequence, listener: OnClickListener?): TextInputDropdownDialogBuilder {
        mBuilder.setPositiveButton(text) { d, w -> listener?.onClick(d, w, mMainInput.text, mCheckBox.isChecked) }; return this
    }

    fun setNegativeButton(@StringRes textId: Int, listener: OnClickListener?): TextInputDropdownDialogBuilder {
        mBuilder.setNegativeButton(textId) { d, w -> listener?.onClick(d, w, mMainInput.text, mCheckBox.isChecked) }; return this
    }

    fun setNegativeButton(text: CharSequence, listener: OnClickListener?): TextInputDropdownDialogBuilder {
        mBuilder.setNegativeButton(text) { d, w -> listener?.onClick(d, w, mMainInput.text, mCheckBox.isChecked) }; return this
    }

    fun setNeutralButton(@StringRes textId: Int, listener: OnClickListener?): TextInputDropdownDialogBuilder {
        mBuilder.setNeutralButton(textId) { d, w -> listener?.onClick(d, w, mMainInput.text, mCheckBox.isChecked) }; return this
    }

    fun setNeutralButton(text: CharSequence, listener: OnClickListener?): TextInputDropdownDialogBuilder {
        mBuilder.setNeutralButton(text) { d, w -> listener?.onClick(d, w, mMainInput.text, mCheckBox.isChecked) }; return this
    }

    fun create(): AlertDialog = mBuilder.create()
    fun show() = create().show()
}
