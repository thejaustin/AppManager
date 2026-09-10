// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.content.Context
import android.view.View
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

class SearchableFlagsDialogBuilder<T : Number>(
    context: Context,
    items: List<T>,
    itemNames: List<CharSequence>,
    defaultFlags: T
) : SearchableMultiChoiceDialogBuilder<T>(context, items, itemNames) {

    constructor(context: Context, items: List<T>, @ArrayRes itemNames: Int, defaultFlags: T) :
            this(context, items, context.resources.getTextArray(itemNames).toList(), defaultFlags)

    constructor(context: Context, items: Array<T>, itemNames: Array<CharSequence>, defaultFlags: T) :
            this(context, items.toList(), itemNames.toList(), defaultFlags)

    constructor(context: Context, items: List<T>, itemNames: Array<CharSequence>, defaultFlags: T) :
            this(context, items, itemNames.toList(), defaultFlags)

    init {
        val flags = defaultFlags.toLong()
        val selectedFlags = items.filter { (flags and it.toLong()) != 0L }
        addSelections(selectedFlags)
    }

    override fun setOnMultiChoiceClickListener(onMultiChoiceClickListener: OnMultiChoiceClickListener<T>?): SearchableFlagsDialogBuilder<T> {
        super.setOnMultiChoiceClickListener(onMultiChoiceClickListener); return this
    }

    override fun addDisabledItems(disabledItems: List<T>?): SearchableFlagsDialogBuilder<T> {
        super.addDisabledItems(disabledItems); return this
    }

    override fun addSelections(selectedItems: List<T>?): SearchableFlagsDialogBuilder<T> {
        super.addSelections(selectedItems); return this
    }

    override fun addSelections(selectedIndexes: IntArray?): SearchableFlagsDialogBuilder<T> {
        super.addSelections(selectedIndexes); return this
    }

    override fun removeSelections(selectedIndexes: IntArray?): SearchableFlagsDialogBuilder<T> {
        super.removeSelections(selectedIndexes); return this
    }

    override fun reloadListUi(): SearchableFlagsDialogBuilder<T> {
        super.reloadListUi(); return this
    }

    override fun setTextSelectable(textSelectable: Boolean): SearchableFlagsDialogBuilder<T> {
        super.setTextSelectable(textSelectable); return this
    }

    override fun setCancelable(cancelable: Boolean): SearchableFlagsDialogBuilder<T> {
        super.setCancelable(cancelable); return this
    }

    override fun hideSearchBar(hide: Boolean): SearchableFlagsDialogBuilder<T> {
        super.hideSearchBar(hide); return this
    }

    override fun showSelectAll(show: Boolean): SearchableFlagsDialogBuilder<T> {
        super.showSelectAll(show); return this
    }

    override fun setTitle(title: CharSequence?): SearchableFlagsDialogBuilder<T> {
        super.setTitle(title); return this
    }

    override fun setTitle(@StringRes title: Int): SearchableFlagsDialogBuilder<T> {
        super.setTitle(title); return this
    }

    override fun setTitle(title: View?): SearchableFlagsDialogBuilder<T> {
        super.setTitle(title); return this
    }

    override fun setPositiveButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableFlagsDialogBuilder<T> {
        super.setPositiveButton(textId, listener); return this
    }

    override fun setPositiveButton(text: CharSequence, listener: OnClickListener<T>?): SearchableFlagsDialogBuilder<T> {
        super.setPositiveButton(text, listener); return this
    }

    override fun setNegativeButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableFlagsDialogBuilder<T> {
        super.setNegativeButton(textId, listener); return this
    }

    override fun setNegativeButton(text: CharSequence, listener: OnClickListener<T>?): SearchableFlagsDialogBuilder<T> {
        super.setNegativeButton(text, listener); return this
    }

    override fun setNeutralButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableFlagsDialogBuilder<T> {
        super.setNeutralButton(textId, listener); return this
    }

    override fun setNeutralButton(text: CharSequence, listener: OnClickListener<T>?): SearchableFlagsDialogBuilder<T> {
        super.setNeutralButton(text, listener); return this
    }
}
