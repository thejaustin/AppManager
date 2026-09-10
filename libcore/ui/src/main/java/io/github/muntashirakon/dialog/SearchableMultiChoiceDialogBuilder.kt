// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.annotation.ArrayRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.resources.MaterialAttributes
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.CheckBox
import io.github.muntashirakon.widget.SearchView
import java.util.Locale

class SearchableMultiChoiceDialogBuilder<T> {
    private val mView: View
    private val mBuilder: MaterialAlertDialogBuilder
    private val mSearchView: SearchView
    private val mSelectAll: CheckBox
    private val mAdapter: SearchableRecyclerViewAdapter
    private var mDialog: AlertDialog? = null
    private var mOnMultiChoiceClickListener: OnMultiChoiceClickListener<T>? = null
    private var mIsTextSelectable = false

    fun interface OnClickListener<T> {
        fun onClick(dialog: DialogInterface, which: Int, selectedItems: ArrayList<T>)
    }

    fun interface OnMultiChoiceClickListener<T> {
        fun onClick(dialog: DialogInterface, which: Int, item: T, isChecked: Boolean)
    }

    internal class MultiChoiceItem<E>(
        val id: Int,
        val name: CharSequence,
        val rawItem: E,
        var isSelected: Boolean = false,
        var isDisabled: Boolean = false,
    ) {
        constructor(other: MultiChoiceItem<E>) : this(
            id = other.id,
            name = other.name,
            rawItem = other.rawItem,
            isSelected = other.isSelected,
            isDisabled = other.isDisabled,
        )
    }

    constructor(context: Context, items: List<T>, @ArrayRes itemNames: Int) :
            this(context, items, context.resources.getTextArray(itemNames).toList())

    constructor(context: Context, items: Array<T>, itemNames: Array<CharSequence>) :
            this(context, items.toList(), itemNames.toList())

    constructor(context: Context, items: List<T>, itemNames: Array<CharSequence>) :
            this(context, items, itemNames.toList())

    constructor(context: Context, items: List<T>, itemNames: List<CharSequence>) {
        mView = View.inflate(context, R.layout.dialog_searchable_multi_choice, null)
        val recyclerView: RecyclerView = mView.findViewById(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        mSearchView = mView.findViewById(R.id.action_search)
        mSelectAll = mView.findViewById(android.R.id.checkbox)
        mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean = false
            override fun onQueryTextChange(newText: String): Boolean {
                mAdapter.setFilteredItems(newText)
                return true
            }
        })
        // Don't display search bar if items are less than 6
        if (items.size < 6) {
            mSearchView.visibility = View.GONE
        }
        mBuilder = MaterialAlertDialogBuilder(context).setView(mView)
        @SuppressLint("RestrictedApi", "PrivateResource")
        val layoutId = MaterialAttributes.resolveInteger(
            context,
            androidx.appcompat.R.attr.multiChoiceItemLayout,
            com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice
        )
        mAdapter = SearchableRecyclerViewAdapter(itemNames, items, layoutId)
        recyclerView.adapter = mAdapter
        mSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) mAdapter.selectAll() else mAdapter.deselectAll()
        }
        if (items.size < 2) {
            // No need to display select all if only one item is present
            mSelectAll.visibility = View.GONE
        }
        checkSelections()
    }

    val view: View get() = mView

    fun setOnMultiChoiceClickListener(
        onMultiChoiceClickListener: OnMultiChoiceClickListener<T>?,
    ): SearchableMultiChoiceDialogBuilder<T> {
        mOnMultiChoiceClickListener = onMultiChoiceClickListener
        return this
    }

    fun addDisabledItems(disabledItems: List<T>?): SearchableMultiChoiceDialogBuilder<T> {
        mAdapter.addDisabledItems(disabledItems)
        checkSelections()
        return this
    }

    fun addSelections(selectedItems: List<T>?): SearchableMultiChoiceDialogBuilder<T> {
        mAdapter.addSelectedItems(selectedItems)
        checkSelections()
        return this
    }

    fun addSelections(selectedIndexes: IntArray?): SearchableMultiChoiceDialogBuilder<T> {
        mAdapter.addSelectedIndexes(selectedIndexes)
        checkSelections()
        return this
    }

    fun removeSelections(selectedIndexes: IntArray?): SearchableMultiChoiceDialogBuilder<T> {
        mAdapter.removeSelectedIndexes(selectedIndexes)
        checkSelections()
        return this
    }

    fun reloadListUi(): SearchableMultiChoiceDialogBuilder<T> {
        mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount, AdapterUtils.STUB)
        return this
    }

    fun setTextSelectable(textSelectable: Boolean): SearchableMultiChoiceDialogBuilder<T> {
        mIsTextSelectable = textSelectable
        return this
    }

    fun setCancelable(cancelable: Boolean): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setCancelable(cancelable)
        return this
    }

    fun hideSearchBar(hide: Boolean): SearchableMultiChoiceDialogBuilder<T> {
        mSearchView.visibility = if (hide) View.GONE else View.VISIBLE
        return this
    }

    fun showSelectAll(show: Boolean): SearchableMultiChoiceDialogBuilder<T> {
        mSelectAll.visibility = if (show) View.VISIBLE else View.GONE
        return this
    }

    fun setTitle(title: CharSequence?): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setTitle(title)
        return this
    }

    fun setTitle(@StringRes title: Int): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setTitle(title)
        return this
    }

    fun setTitle(title: View?): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setCustomTitle(title)
        return this
    }

    fun setPositiveButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setPositiveButton(textId) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelectedItems()) }
        return this
    }

    fun setPositiveButton(text: CharSequence, listener: OnClickListener<T>?): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setPositiveButton(text) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelectedItems()) }
        return this
    }

    fun setNegativeButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setNegativeButton(textId) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelectedItems()) }
        return this
    }

    fun setNegativeButton(text: CharSequence, listener: OnClickListener<T>?): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setNegativeButton(text) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelectedItems()) }
        return this
    }

    fun setNeutralButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setNeutralButton(textId) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelectedItems()) }
        return this
    }

    fun setNeutralButton(text: CharSequence, listener: OnClickListener<T>?): SearchableMultiChoiceDialogBuilder<T> {
        mBuilder.setNeutralButton(text) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelectedItems()) }
        return this
    }

    fun create(): AlertDialog = mBuilder.create().also { mDialog = it }

    fun show() {
        create().show()
    }

    private fun checkSelections() {
        mSelectAll.setChecked(mAdapter.areAllSelected(), false)
    }

    private fun triggerMultiChoiceClickListener(index: Int, isChecked: Boolean) {
        val dialog = mDialog ?: return
        mOnMultiChoiceClickListener?.onClick(dialog, index, mAdapter.mMasterList[index].rawItem, isChecked)
    }

    private class ChoiceItemCallback<E> : DiffUtil.ItemCallback<MultiChoiceItem<E>>() {
        override fun areItemsTheSame(oldItem: MultiChoiceItem<E>, newItem: MultiChoiceItem<E>): Boolean =
            oldItem.id == newItem.id && oldItem.rawItem == newItem.rawItem

        override fun areContentsTheSame(oldItem: MultiChoiceItem<E>, newItem: MultiChoiceItem<E>): Boolean =
            oldItem.isSelected == newItem.isSelected &&
                    oldItem.isDisabled == newItem.isDisabled &&
                    oldItem.name.toString() == newItem.name.toString()
    }

    inner class SearchableRecyclerViewAdapter(
        itemNames: List<CharSequence>,
        items: List<T>,
        @LayoutRes private val mLayoutId: Int,
    ) : ListAdapter<MultiChoiceItem<T>, SearchableRecyclerViewAdapter.ViewHolder>(ChoiceItemCallback()) {

        val mMasterList: MutableList<MultiChoiceItem<T>> = ArrayList()
        val mNotFoundItems: MutableList<T> = ArrayList()
        var mCurrentVisibleList: MutableList<MultiChoiceItem<T>> = ArrayList()
        private var mCurrentQuery: CharSequence = ""

        init {
            val size = minOf(itemNames.size, items.size)
            for (i in 0 until size) {
                mMasterList.add(MultiChoiceItem(i, itemNames[i], items[i]))
            }
            dispatchFilteredList()
        }

        fun setFilteredItems(constraint: CharSequence) {
            mCurrentQuery = constraint
            dispatchFilteredList()
            checkSelections()
        }

        private fun dispatchFilteredList() {
            if (mCurrentQuery.isEmpty()) {
                mCurrentVisibleList = ArrayList(mMasterList)
            } else {
                val locale = Locale.getDefault()
                val query = mCurrentQuery.toString().lowercase(locale)
                mCurrentVisibleList = mMasterList.filter { item ->
                    item.name.toString().lowercase(locale).contains(query) ||
                            item.rawItem.toString().lowercase(Locale.ROOT).contains(query)
                }.toMutableList()
            }
            submitList(mCurrentVisibleList)
        }

        fun getSelectedItems(): ArrayList<T> {
            val selections = ArrayList(mNotFoundItems)
            for (item in mMasterList) {
                if (item.isSelected) {
                    selections.add(item.rawItem)
                }
            }
            return selections
        }

        fun addSelectedItems(selectedItems: List<T>?) {
            if (selectedItems == null) return
            var listUpdated = false
            for (item in selectedItems) {
                var found = false
                for (i in mMasterList.indices) {
                    val wrapper = mMasterList[i]
                    if (wrapper.rawItem == item) {
                        if (!wrapper.isSelected) {
                            val newItem = MultiChoiceItem(wrapper)
                            newItem.isSelected = true
                            mMasterList[i] = newItem
                            listUpdated = true
                        }
                        found = true
                        break
                    }
                }
                if (!found && !mNotFoundItems.contains(item)) {
                    mNotFoundItems.add(item)
                }
            }
            if (listUpdated) {
                dispatchFilteredList()
            }
        }

        fun addSelectedIndexes(selectedIndexes: IntArray?) {
            if (selectedIndexes == null) return
            for (index in selectedIndexes) {
                if (index >= 0 && index < mMasterList.size) {
                    mMasterList[index].isSelected = true
                }
            }
            dispatchFilteredList()
        }

        fun removeSelectedIndexes(selectedIndexes: IntArray?) {
            if (selectedIndexes == null) return
            for (index in selectedIndexes) {
                if (index >= 0 && index < mMasterList.size) {
                    mMasterList[index].isSelected = false
                }
            }
            dispatchFilteredList()
        }

        fun addDisabledItems(disabledItems: List<T>?) {
            if (disabledItems == null) return
            for (item in disabledItems) {
                for (wrapper in mMasterList) {
                    if (wrapper.rawItem == item) {
                        wrapper.isDisabled = true
                        break
                    }
                }
            }
            dispatchFilteredList()
        }

        fun selectAll() {
            var listUpdated = false
            for (item in mCurrentVisibleList) {
                if (!item.isSelected) {
                    val newItem = MultiChoiceItem(item)
                    newItem.isSelected = true
                    mMasterList[newItem.id] = newItem
                    triggerMultiChoiceClickListener(newItem.id, true)
                    listUpdated = true
                }
            }
            if (listUpdated) {
                dispatchFilteredList()
                checkSelections()
            }
        }

        fun deselectAll() {
            var listUpdated = false
            for (item in mCurrentVisibleList) {
                if (item.isSelected) {
                    val newItem = MultiChoiceItem(item)
                    newItem.isSelected = false
                    mMasterList[newItem.id] = newItem
                    triggerMultiChoiceClickListener(newItem.id, false)
                    listUpdated = true
                }
            }
            if (listUpdated) {
                dispatchFilteredList()
                checkSelections()
            }
        }

        fun areAllSelected(): Boolean {
            if (mCurrentVisibleList.isEmpty()) return false
            return mCurrentVisibleList.all { it.isSelected }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(mLayoutId, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.item.text = item.name
            holder.item.setTextIsSelectable(mIsTextSelectable)
            holder.item.isEnabled = !item.isDisabled
            holder.item.isChecked = item.isSelected
            holder.item.setOnClickListener {
                val newItem = MultiChoiceItem(item)
                newItem.isSelected = !item.isSelected
                mMasterList[newItem.id] = newItem
                dispatchFilteredList()
                checkSelections()
                triggerMultiChoiceClickListener(newItem.id, newItem.isSelected)
            }
        }

        inner class ViewHolder @SuppressLint("RestrictedApi") constructor(itemView: View) :
            RecyclerView.ViewHolder(itemView) {
            val item: CheckedTextView = itemView.findViewById(android.R.id.text1)

            init {
                val textAppearanceBodyLarge = MaterialAttributes.resolveInteger(
                    item.context,
                    com.google.android.material.R.attr.textAppearanceBodyLarge,
                    0
                )
                TextViewCompat.setTextAppearance(item, textAppearanceBodyLarge)
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                item.setTextColor(
                    MaterialColors.getColor(
                        item.context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        -1
                    )
                )
            }
        }
    }
}
