// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history

import org.json.JSONException
import org.json.JSONObject

fun interface IJsonSerializer {
    @Throws(JSONException::class)
    fun serializeToJson(): JSONObject
}
