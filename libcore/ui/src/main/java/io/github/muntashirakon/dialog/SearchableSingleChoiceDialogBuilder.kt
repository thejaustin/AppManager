// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.FrameLayout
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.resources.MaterialAttributes
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.RecyclerView
import io.github.muntashirakon.widget.SearchView
import java.util.Locale

class SearchableSingleChoiceDialogBuilder<T> {
    private val mBuilder: MaterialAlertDialogBuilder
    private val mSearchView: SearchView
    private val mRecyclerView: RecyclerView
    private val mViewContainer: FrameLayout
    private val mAdapter: SearchableRecyclerViewAdapter
    private var mDialog: AlertDialog? = null
    private var mOnSingleChoiceClickListener: OnSingleChoiceClickListener<T>? = null
    private var mIsTextSelectable = false

    fun interface OnClickListener<T> {
        fun onClick(dialog: DialogInterface, which: Int, selectedItem: T?)
    }

    fun interface OnSingleChoiceClickListener<T> {
        fun onClick(dialog: DialogInterface, which: Int, item: T, isChecked: Boolean)
    }

    internal class SingleChoiceItem<E>(
        val id: Int,
        val name: CharSequence,
        val rawItem: E,
        var isSelected: Boolean = false,
        var isDisabled: Boolean = false,
    ) {
        constructor(other: SingleChoiceItem<E>) : this(
            id = other.id,
            name = other.name,
            rawItem = other.rawItem,
            isSelected = other.isSelected,
            isDisabled = other.isDisabled,
        )
    }

    constructor(context: Context, items: List<T>, @ArrayRes itemNames: Int) :
            this(context, items, context.resources.getTextArray(itemNames).toList())

    constructor(context: Context, items: List<T>, itemNames: Array<CharSequence>) :
            this(context, items, itemNames.toList())

    constructor(context: Context, items: Array<T>, itemNames: Array<CharSequence>) :
            this(context, items.toList(), itemNames.toList())

    constructor(context: Context, items: List<T>, itemNames: List<CharSequence>) {
        val view = View.inflate(context, R.layout.dialog_searchable_single_choice, null)
        mRecyclerView = view.findViewById(android.R.id.list)
        mRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        mViewContainer = view.findViewById(R.id.container)
        mSearchView = view.findViewById(R.id.action_search)
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
        mBuilder = MaterialAlertDialogBuilder(context).setView(view)
        @SuppressLint("RestrictedApi", "PrivateResource")
        val layoutId = MaterialAttributes.resolveInteger(
            context,
            androidx.appcompat.R.attr.singleChoiceItemLayout,
            com.google.android.material.R.layout.mtrl_alert_select_dialog_singlechoice
        )
        mAdapter = SearchableRecyclerViewAdapter(itemNames, items, layoutId)
        mRecyclerView.adapter = mAdapter
    }

    fun setOnDismissListener(dismissListener: DialogInterface.OnDismissListener?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setOnDismissListener(dismissListener)
        return this
    }

    fun setOnSingleChoiceClickListener(
        onSingleChoiceClickListener: OnSingleChoiceClickListener<T>?,
    ): SearchableSingleChoiceDialogBuilder<T> {
        mOnSingleChoiceClickListener = onSingleChoiceClickListener
        return this
    }

    fun addDisabledItems(disabledItems: List<T>?): SearchableSingleChoiceDialogBuilder<T> {
        mAdapter.addDisabledItems(disabledItems)
        return this
    }

    fun setSelection(selectedItem: T?): SearchableSingleChoiceDialogBuilder<T> {
        mAdapter.setSelection(selectedItem)
        return this
    }

    fun setSelectionIndex(selectedIndex: Int): SearchableSingleChoiceDialogBuilder<T> {
        mAdapter.setSelectedIndex(selectedIndex)
        return this
    }

    fun removeSelection(): SearchableSingleChoiceDialogBuilder<T> {
        mAdapter.setSelectedIndex(-1)
        return this
    }

    fun setView(view: View?): SearchableSingleChoiceDialogBuilder<T> {
        mViewContainer.removeAllViews()
        if (view != null) {
            mViewContainer.addView(view)
        }
        return this
    }

    fun reloadListUi(): SearchableSingleChoiceDialogBuilder<T> {
        mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount, AdapterUtils.STUB)
        return this
    }

    fun setTextSelectable(textSelectable: Boolean): SearchableSingleChoiceDialogBuilder<T> {
        mIsTextSelectable = textSelectable
        return this
    }

    fun setCancelable(cancelable: Boolean): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setCancelable(cancelable)
        return this
    }

    fun hideSearchBar(hide: Boolean): SearchableSingleChoiceDialogBuilder<T> {
        mSearchView.visibility = if (hide) View.GONE else View.VISIBLE
        return this
    }

    fun setIcon(@DrawableRes iconRes: Int): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setIcon(iconRes)
        return this
    }

    fun setIcon(icon: Drawable?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setIcon(icon)
        return this
    }

    fun setTitle(title: CharSequence?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setTitle(title)
        return this
    }

    fun setTitle(@StringRes title: Int): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setTitle(title)
        return this
    }

    fun setTitle(title: View?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setCustomTitle(title)
        return this
    }

    fun setPositiveButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setPositiveButton(textId) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelection()) }
        return this
    }

    fun setPositiveButton(text: CharSequence, listener: OnClickListener<T>?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setPositiveButton(text) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelection()) }
        return this
    }

    fun setNegativeButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setNegativeButton(textId) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelection()) }
        return this
    }

    fun setNegativeButton(text: CharSequence, listener: OnClickListener<T>?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setNegativeButton(text) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelection()) }
        return this
    }

    fun setNeutralButton(@StringRes textId: Int, listener: OnClickListener<T>?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setNeutralButton(textId) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelection()) }
        return this
    }

    fun setNeutralButton(text: CharSequence, listener: OnClickListener<T>?): SearchableSingleChoiceDialogBuilder<T> {
        mBuilder.setNeutralButton(text) { dialog, which -> listener?.onClick(dialog, which, mAdapter.getSelection()) }
        return this
    }

    fun create(): AlertDialog = mBuilder.create().also { mDialog = it }

    fun show(): AlertDialog = mBuilder.show().also { mDialog = it }

    private fun triggerSingleChoiceClickListener(index: Int, isChecked: Boolean) {
        val dialog = mDialog ?: return
        mOnSingleChoiceClickListener?.onClick(dialog, index, mAdapter.mMasterList[index].rawItem, isChecked)
    }

    private class ChoiceItemCallback<E> : DiffUtil.ItemCallback<SingleChoiceItem<E>>() {
        override fun areItemsTheSame(oldItem: SingleChoiceItem<E>, newItem: SingleChoiceItem<E>): Boolean =
            oldItem.id == newItem.id && oldItem.rawItem == newItem.rawItem

        override fun areContentsTheSame(oldItem: SingleChoiceItem<E>, newItem: SingleChoiceItem<E>): Boolean =
            oldItem.isSelected == newItem.isSelected &&
                    oldItem.isDisabled == newItem.isDisabled &&
                    oldItem.name.toString() == newItem.name.toString()
    }

    inner class SearchableRecyclerViewAdapter(
        itemNames: List<CharSequence>,
        items: List<T>,
        @LayoutRes private val mLayoutId: Int,
    ) : RecyclerView.ListAdapter<SingleChoiceItem<T>, SearchableRecyclerViewAdapter.ViewHolder>(ChoiceItemCallback()) {

        val mMasterList: MutableList<SingleChoiceItem<T>> = ArrayList()
        private var mCurrentQuery: String? = null
        private var mSelectedRawItem: T? = null

        init {
            val size = minOf(itemNames.size, items.size)
            for (i in 0 until size) {
                mMasterList.add(SingleChoiceItem(i, itemNames[i], items[i]))
            }
            dispatchFilteredList()
        }

        fun setFilteredItems(constraint: String) {
            mCurrentQuery = if (TextUtils.isEmpty(constraint)) null else constraint.lowercase(Locale.ROOT)
            dispatchFilteredList()
        }

        private fun dispatchFilteredList() {
            val query = mCurrentQuery
            if (query == null) {
                submitList(ArrayList(mMasterList))
                return
            }
            val locale = Locale.getDefault()
            val filteredList = mMasterList.filter { item ->
                item.name.toString().lowercase(locale).contains(query) ||
                        item.rawItem.toString().lowercase(Locale.ROOT).contains(query)
            }
            submitList(filteredList)
        }

        fun getSelection(): T? = mSelectedRawItem

        fun setSelection(selectedItem: T?) {
            if (mSelectedRawItem == selectedItem) return
            mSelectedRawItem = selectedItem
            for (i in mMasterList.indices) {
                val item = mMasterList[i]
                val targetSelectionState = item.rawItem == selectedItem
                if (item.isSelected != targetSelectionState) {
                    val newItem = SingleChoiceItem(item)
                    newItem.isSelected = targetSelectionState
                    mMasterList[i] = newItem
                    triggerSingleChoiceClickListener(newItem.id, targetSelectionState)
                }
            }
            dispatchFilteredList()
        }

        fun setSelectedIndex(selectedIndex: Int) {
            val targetItem = if (selectedIndex >= 0 && selectedIndex < mMasterList.size) mMasterList[selectedIndex] else null
            setSelection(targetItem?.rawItem)
            if (selectedIndex >= 0) {
                mRecyclerView.setSelection(selectedIndex)
            }
        }

        fun addDisabledItems(disabledItems: List<T>?) {
            if (disabledItems == null) return
            var listUpdated = false
            for (item in disabledItems) {
                for (i in mMasterList.indices) {
                    val wrapper = mMasterList[i]
                    if (wrapper.rawItem == item && !wrapper.isDisabled) {
                        val newItem = SingleChoiceItem(wrapper)
                        newItem.isDisabled = true
                        mMasterList[i] = newItem
                        listUpdated = true
                        break
                    }
                }
            }
            if (listUpdated) {
                dispatchFilteredList()
            }
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
                if (item.isSelected) {
                    // Already selected, do nothing
                    return@setOnClickListener
                }
                setSelection(item.rawItem)
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
