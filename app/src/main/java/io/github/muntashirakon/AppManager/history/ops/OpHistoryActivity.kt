// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.entity.OpHistory
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException

class OpHistoryActivity : BaseActivity() {
    private lateinit var mViewModel: OpHistoryViewModel
    private lateinit var mAdapter: OpHistoryAdapter
    private lateinit var mProgressIndicator: LinearProgressIndicator

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_op_history)
        setSupportActionBar(findViewById(R.id.toolbar))
        mViewModel = ViewModelProvider(this)[OpHistoryViewModel::class.java]
        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator.setVisibilityAfterHide(View.GONE)
        val listView: RecyclerView = findViewById(android.R.id.list)
        listView.layoutManager = UIUtils.getGridLayoutAt450Dp(this)
        listView.setEmptyView(findViewById(android.R.id.empty))
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView)
        mAdapter = OpHistoryAdapter(this)
        listView.adapter = mAdapter
        val fab: FloatingActionButton = findViewById(R.id.floatingActionButton)
        UiUtils.applyWindowInsetsAsMargin(fab)
        fab.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.are_you_sure)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    mProgressIndicator.show()
                    mViewModel.clearHistory()
                }
                .show()
        }
        mViewModel.opHistoriesLiveData.observe(this) { opHistories ->
            mProgressIndicator.hide()
            mAdapter.setDefaultList(opHistories)
        }
        mViewModel.clearHistoryLiveData.observe(this) { cleared ->
            UIUtils.displayShortToast(if (cleared) R.string.done else R.string.failed)
        }
        mViewModel.serviceLauncherIntentLiveData.observe(this) { intent ->
            if (intent != null) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                UIUtils.displayShortToast(R.string.failed)
            }
        }
        OpHistoryManager.getHistoryAddedLiveData().observe(this) {
            mProgressIndicator.show()
            mViewModel.loadOpHistories()
        }
        mProgressIndicator.show()
        mViewModel.loadOpHistories()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        else return super.onOptionsItemSelected(item)
        return true
    }

    class OpHistoryAdapter(private val mActivity: OpHistoryActivity) :
        RecyclerView.Adapter<OpHistoryAdapter.ViewHolder>() {

        private val mAdapterList: MutableList<OpHistoryItem> = ArrayList()
        private val mColorSuccess = ColorCodes.getSuccessColor(mActivity)
        private val mColorFailure = ColorCodes.getFailureColor(mActivity)

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView as MaterialCardView
            val type: TextView = itemView.findViewById(R.id.type)
            val title: TextView = itemView.findViewById(android.R.id.title)
            val execTime: TextView = itemView.findViewById(android.R.id.summary)
            val execBtn: Button = itemView.findViewById(R.id.item_action)
        }

        fun setDefaultList(list: List<OpHistoryItem>) {
            synchronized(mAdapterList) {
                AdapterUtils.notifyDataSetChanged(this, mAdapterList, list)
            }
        }

        override fun getItemCount(): Int = synchronized(mAdapterList) { mAdapterList.size }

        override fun getItemId(position: Int): Long =
            synchronized(mAdapterList) { mAdapterList[position].hashCode().toLong() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_op_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val history = synchronized(mAdapterList) { mAdapterList[position] }
            holder.card.strokeColor = if (history.getStatus()) mColorSuccess else mColorFailure
            holder.type.text = history.getLocalizedType(mActivity)
            holder.title.text = history.getLabel(mActivity)
            holder.execTime.text = DateUtils.formatLongDateTime(mActivity, history.getTimestamp())
            holder.card.setOnClickListener {
                // TODO: 1/26/25 Display history info
            }
            holder.card.setOnLongClickListener {
                // TODO: 1/26/25 Possible long click options
                //  1. Apply, 2. Delete, 3. Add as profile, 4. Export, 5. Create shortcut
                true
            }
            holder.execBtn.setOnClickListener {
                MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.title_confirm_execution)
                    .setMessage(R.string.are_you_sure)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        mActivity.mViewModel.getServiceLauncherIntent(history)
                    }
                    .show()
            }
        }
    }

    class OpHistoryViewModel(application: Application) : AndroidViewModel(application) {
        private val _opHistoriesLiveData = MutableLiveData<List<OpHistoryItem>>()
        val opHistoriesLiveData: LiveData<List<OpHistoryItem>> get() = _opHistoriesLiveData

        private val _clearHistoryLiveData = MutableLiveData<Boolean>()
        val clearHistoryLiveData: LiveData<Boolean> get() = _clearHistoryLiveData

        private val _serviceLauncherIntentLiveData = MutableLiveData<Intent?>()
        val serviceLauncherIntentLiveData: LiveData<Intent?> get() = _serviceLauncherIntentLiveData

        private var mLoadJob: Job? = null

        fun loadOpHistories() {
            mLoadJob?.cancel()
            mLoadJob = viewModelScope.launch(Dispatchers.IO) {
                val opHistories: List<OpHistory> = OpHistoryManager.getAllHistoryItems()
                    .sortedByDescending { it.execTime }
                val items = opHistories.mapNotNull { history ->
                    try {
                        OpHistoryItem(history)
                    } catch (e: JSONException) {
                        Log.w(TAG, e.message, e)
                        null
                    }
                }
                _opHistoriesLiveData.postValue(items)
            }
        }

        fun clearHistory() {
            viewModelScope.launch(Dispatchers.IO) {
                OpHistoryManager.clearAllHistory()
                _clearHistoryLiveData.postValue(true)
                _opHistoriesLiveData.postValue(emptyList())
            }
        }

        fun getServiceLauncherIntent(opHistoryItem: OpHistoryItem) {
            viewModelScope.launch(Dispatchers.IO) {
                val intent = try {
                    OpHistoryManager.getExecutableIntent(getApplication(), opHistoryItem)
                } catch (e: JSONException) {
                    Log.w(TAG, e.message, e)
                    null
                }
                _serviceLauncherIntentLiveData.postValue(intent)
            }
        }

        companion object {
            private const val TAG = "OpHistoryViewModel"
        }
    }
}
