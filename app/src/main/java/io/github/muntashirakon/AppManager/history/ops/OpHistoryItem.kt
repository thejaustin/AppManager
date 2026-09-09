// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops

import android.content.Context
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.db.entity.OpHistory
import io.github.muntashirakon.AppManager.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject

class OpHistoryItem @Throws(JSONException::class) constructor(private val opHistory: OpHistory) {
    val jsonData: JSONObject = JSONObject(opHistory.serializedData)

    @OpHistoryManager.HistoryType
    fun getType(): String = opHistory.type

    fun getLocalizedType(context: Context): String = when (opHistory.type) {
        OpHistoryManager.HISTORY_TYPE_BATCH_OPS -> try {
            context.getString(jsonData.getInt("title_res"))
        } catch (e: Exception) {
            context.getString(R.string.batch_ops)
        }
        OpHistoryManager.HISTORY_TYPE_INSTALLER -> context.getString(R.string.installer)
        OpHistoryManager.HISTORY_TYPE_PROFILE -> context.getString(R.string.profiles)
        else -> throw IllegalStateException("Invalid type: ${opHistory.type}")
    }

    fun getLabel(context: Context): String = when (opHistory.type) {
        OpHistoryManager.HISTORY_TYPE_BATCH_OPS -> try {
            BatchOpsService.getDesiredOpTitle(context, jsonData.getInt("op"))
        } catch (e: JSONException) {
            context.getString(R.string.unknown_op)
        }
        OpHistoryManager.HISTORY_TYPE_INSTALLER ->
            JSONUtils.optString(jsonData, "app_label") ?: context.getString(R.string.state_unknown)
        OpHistoryManager.HISTORY_TYPE_PROFILE ->
            JSONUtils.optString(jsonData, "profile_name") ?: context.getString(R.string.state_unknown)
        else -> throw IllegalStateException("Invalid type: ${opHistory.type}")
    }

    fun getTimestamp(): Long = opHistory.execTime

    fun getStatus(): Boolean = opHistory.status == OpHistoryManager.STATUS_SUCCESS
}
