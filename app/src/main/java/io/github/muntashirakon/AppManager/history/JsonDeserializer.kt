// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history

import org.json.JSONException
import org.json.JSONObject

object JsonDeserializer {
    fun interface Creator<T> {
        @Throws(JSONException::class)
        fun deserialize(jsonObject: JSONObject): T
    }
}
