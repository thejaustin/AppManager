// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ArrayRes
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.resources.MaterialAttributes
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.SearchView
import java.util.Locale

class SearchableItemsDialogBuilder<T : CharSequence> {
    private val mBuilder: MaterialAlertDialogBuilder
    private val mSearchView: SearchView
    private val mAdapter: SearchableRecyclerViewAdapter
    private var mDialog: AlertDialog? = null
    private var mOnItemClickListener: OnItemClickListener<T>? = null
    private var mIsTextSelectable = false

    @ColorInt private var mListBackgroundColorEven: Int? = null
    @ColorInt private var mListBackgroundColorOdd: Int? = null

    fun interface OnItemClickListener<T> {
        fun onClick(dialog: DialogInterface, which: Int, item: T)
    }

    internal class SearchableItem<E : CharSequence>(val id: Int, val rawItem: E) {
        var isDisabled = false
    }

    @Suppress("UNCHECKED_CAST")
    constructor(context: Context, @ArrayRes itemNames: Int) :
            this(context, context.resources.getTextArray(itemNames).toList() as List<T>)

    constructor(context: Context, itemNames: Array<T>) : this(context, itemNames.toList())

    constructor(context: Context, itemNames: List<T>) {
        val view = View.inflate(context, R.layout.dialog_searchable_single_choice, null)
        val recyclerView: RecyclerView = view.findViewById(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        mSearchView = view.findViewById(R.id.action_search)
        mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false
            override fun onQueryTextChange(newText: String): Boolean {
                mAdapter.setFilteredItems(newText); return true
            }
        })
        if (itemNames.size < 6) mSearchView.visibility = View.GONE
        mBuilder = MaterialAlertDialogBuilder(context).setView(view)
        @SuppressLint("RestrictedApi", "PrivateResource")
        val layoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.listItemLayout,
            R.layout.m3_alert_select_dialog_item)
        mAdapter = SearchableRecyclerViewAdapter(itemNames, layoutId)
        recyclerView.adapter = mAdapter
    }

    fun setOnItemClickListener(onItemClickListener: OnItemClickListener<T>?): SearchableItemsDialogBuilder<T> {
        mOnItemClickListener = onItemClickListener; return this
    }

    fun addDisabledItems(disabledItems: List<T>?): SearchableItemsDialogBuilder<T> {
        mAdapter.addDisabledItems(disabledItems); return this
    }

    fun setListBackgroundColorEven(@ColorInt color: Int?): SearchableItemsDialogBuilder<T> {
        mListBackgroundColorEven = color; return this
    }

    fun setListBackgroundColorOdd(@ColorInt color: Int?): SearchableItemsDialogBuilder<T> {
        mListBackgroundColorOdd = color; return this
    }

    fun reloadListUi(): SearchableItemsDialogBuilder<T> {
        mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount, AdapterUtils.STUB); return this
    }

    fun setTextSelectable(textSelectable: Boolean): SearchableItemsDialogBuilder<T> {
        mIsTextSelectable = textSelectable; return this
    }

    fun setCancelable(cancelable: Boolean): SearchableItemsDialogBuilder<T> {
        mBuilder.setCancelable(cancelable); return this
    }

    fun hideSearchBar(hide: Boolean): SearchableItemsDialogBuilder<T> {
        mSearchView.visibility = if (hide) View.GONE else View.VISIBLE; return this
    }

    fun setTitle(title: CharSequence?): SearchableItemsDialogBuilder<T> { mBuilder.setTitle(title); return this }
    fun setTitle(@StringRes title: Int): SearchableItemsDialogBuilder<T> { mBuilder.setTitle(title); return this }
    fun setTitle(title: View): SearchableItemsDialogBuilder<T> { mBuilder.setCustomTitle(title); return this }

    fun setPositiveButton(@StringRes textId: Int, listener: DialogInterface.OnClickListener?): SearchableItemsDialogBuilder<T> {
        mBuilder.setPositiveButton(textId, listener); return this
    }

    fun setPositiveButton(text: CharSequence, listener: DialogInterface.OnClickListener?): SearchableItemsDialogBuilder<T> {
        mBuilder.setPositiveButton(text, listener); return this
    }

    fun setNegativeButton(@StringRes textId: Int, listener: DialogInterface.OnClickListener?): SearchableItemsDialogBuilder<T> {
        mBuilder.setNegativeButton(textId, listener); return this
    }

    fun setNegativeButton(text: CharSequence, listener: DialogInterface.OnClickListener?): SearchableItemsDialogBuilder<T> {
        mBuilder.setNegativeButton(text, listener); return this
    }

    fun setNeutralButton(@StringRes textId: Int, listener: DialogInterface.OnClickListener?): SearchableItemsDialogBuilder<T> {
        mBuilder.setNeutralButton(textId, listener); return this
    }

    fun setNeutralButton(text: CharSequence, listener: DialogInterface.OnClickListener?): SearchableItemsDialogBuilder<T> {
        mBuilder.setNeutralButton(text, listener); return this
    }

    fun create(): AlertDialog = mBuilder.create().also { mDialog = it }
    fun show(): AlertDialog = mBuilder.show().also { mDialog = it }

    private fun triggerItemClickListener(index: Int) {
        val dialog = mDialog ?: return
        val listener = mOnItemClickListener ?: return
        listener.onClick(dialog, index, mAdapter.mMasterList[index].rawItem)
    }

    private class ItemCallback<E : CharSequence> : DiffUtil.ItemCallback<SearchableItem<E>>() {
        override fun areItemsTheSame(oldItem: SearchableItem<E>, newItem: SearchableItem<E>) =
            oldItem.id == newItem.id && oldItem.rawItem == newItem.rawItem

        override fun areContentsTheSame(oldItem: SearchableItem<E>, newItem: SearchableItem<E>) =
            oldItem.isDisabled == newItem.isDisabled
    }

    inner class SearchableRecyclerViewAdapter(
        items: List<T>,
        @LayoutRes private val mLayoutId: Int
    ) : ListAdapter<SearchableItem<T>, SearchableRecyclerViewAdapter.ViewHolder>(ItemCallback()) {

        val mMasterList: MutableList<SearchableItem<T>> = items.mapIndexed { i, item ->
            SearchableItem(i, item)
        }.toMutableList()

        private var mCurrentQuery: String? = null

        fun setFilteredItems(constraint: CharSequence) {
            mCurrentQuery = if (TextUtils.isEmpty(constraint)) null else constraint.toString().lowercase(Locale.ROOT)
            dispatchFilteredList()
        }

        private fun dispatchFilteredList() {
            val query = mCurrentQuery
            if (query == null) {
                submitList(mMasterList.toList()); return
            }
            val locale = Locale.getDefault()
            submitList(mMasterList.filter { it.rawItem.toString().lowercase(locale).contains(query) })
        }

        fun addDisabledItems(disabledItems: List<T>?) {
            if (disabledItems == null) return
            for (item in disabledItems) {
                mMasterList.firstOrNull { it.rawItem == item }?.isDisabled = true
            }
            dispatchFilteredList()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(mLayoutId, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.item.text = item.rawItem
            holder.item.setTextIsSelectable(mIsTextSelectable)
            holder.item.isEnabled = !item.isDisabled
            holder.itemView.setOnClickListener { triggerItemClickListener(item.id) }
            setBackgroundColor(holder.itemView, if (position % 2 == 0) mListBackgroundColorOdd else mListBackgroundColorEven)
        }

        private fun setBackgroundColor(view: View, color: Int?) {
            if (view is MaterialCardView) {
                view.setCardBackgroundColor(color ?: 0)
            } else {
                if (color != null) view.setBackgroundColor(color) else view.background = null
            }
        }

        inner class ViewHolder @SuppressLint("RestrictedApi") constructor(itemView: View) :
                RecyclerView.ViewHolder(itemView) {
            val item: TextView = itemView.findViewById(android.R.id.text1)

            init {
                itemView.findViewById<View>(R.id.icon_frame).visibility = View.GONE
                itemView.findViewById<View>(android.R.id.widget_frame).visibility = View.GONE
            }
        }
    }
}
