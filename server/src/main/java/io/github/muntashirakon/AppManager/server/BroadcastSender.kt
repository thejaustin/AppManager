// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

// Copyright 2017 Zheng Li

package io.github.muntashirakon.AppManager.server

import android.content.Intent
import io.github.muntashirakon.AppManager.server.common.FLog

internal object BroadcastSender {
    fun sendBroadcast(intent: Intent) {
        try {
            val app = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) ?: run {
                FLog.log("BroadcastSender: NullPointerException $intent")
                return
            }
            (app as android.app.Application).sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            FLog.log(e)
        }
    }
}
