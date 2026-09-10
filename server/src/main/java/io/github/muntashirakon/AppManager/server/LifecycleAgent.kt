// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

// Copyright 2017 Zheng Li

package io.github.muntashirakon.AppManager.server

import android.content.Intent
import io.github.muntashirakon.AppManager.server.common.ConfigParams
import io.github.muntashirakon.AppManager.server.common.ConfigParams.PARAM_TOKEN
import io.github.muntashirakon.AppManager.server.common.ConfigParams.PARAM_UID
import io.github.muntashirakon.AppManager.server.common.ServerActions
import io.github.muntashirakon.AppManager.server.common.ServerInfo

class LifecycleAgent(val configParams: ConfigParams) {
    companion object {
        @JvmField
        val sServerInfo = ServerInfo()
    }

    fun onStarted() = BroadcastSender.sendBroadcast(makeIntent(ServerActions.ACTION_SERVER_STARTED))
    fun onConnected() = BroadcastSender.sendBroadcast(makeIntent(ServerActions.ACTION_SERVER_CONNECTED))
    fun onDisconnected() = BroadcastSender.sendBroadcast(makeIntent(ServerActions.ACTION_SERVER_DISCONNECTED))
    fun onStopped() = BroadcastSender.sendBroadcast(makeIntent(ServerActions.ACTION_SERVER_STOPPED))

    private fun makeIntent(action: String): Intent =
        Intent(action)
            .setClassName(configParams.appName, "${ServerActions.PACKAGE_NAME}.servermanager.ServerStatusChangeReceiver")
            .putExtra(PARAM_TOKEN, configParams.token)
            .putExtra(PARAM_UID, configParams.uid)
}
