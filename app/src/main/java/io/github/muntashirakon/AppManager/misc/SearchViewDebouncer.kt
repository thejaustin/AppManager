// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.os.Handler
import android.os.Looper
import androidx.appcompat.widget.SearchView

class SearchViewDebouncer(private val delayMillis: Long) {
    companion object {
        const val DELAY_STANDARD = 200
    }

    fun interface OnDebouncedQueryListener {
        fun onQueryDebounced(query: String)
    }

    interface OnAdvancedDebouncedQueryListener {
        fun onQueryDebounced(query: String, @AdvancedSearchView.SearchType type: Int)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    fun bind(searchView: SearchView, listener: OnDebouncedQueryListener) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                cancelPendingSearch()
                listener.onQueryDebounced(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                cancelPendingSearch()
                searchRunnable = Runnable { listener.onQueryDebounced(newText ?: "") }
                handler.postDelayed(searchRunnable!!, delayMillis)
                return true
            }
        })
    }

    fun bindAdvanced(searchView: AdvancedSearchView, listener: OnAdvancedDebouncedQueryListener) {
        searchView.setOnQueryTextListener(object : AdvancedSearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?, @AdvancedSearchView.SearchType type: Int): Boolean {
                cancelPendingSearch()
                listener.onQueryDebounced(query ?: "", type)
                return true
            }

            override fun onQueryTextChange(newText: String?, @AdvancedSearchView.SearchType type: Int): Boolean {
                cancelPendingSearch()
                searchRunnable = Runnable { listener.onQueryDebounced(newText ?: "", type) }
                handler.postDelayed(searchRunnable!!, delayMillis)
                return true
            }
        })
    }

    fun unbind() {
        cancelPendingSearch()
    }

    private fun cancelPendingSearch() {
        searchRunnable?.let {
            handler.removeCallbacks(it)
            searchRunnable = null
        }
    }
}
