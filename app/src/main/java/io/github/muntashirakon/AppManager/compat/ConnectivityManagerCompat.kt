// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManagerHidden
import android.net.IConnectivityManager
import android.os.Build
import android.os.RemoteException
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import io.github.muntashirakon.AppManager.ipc.ProxyBinder

object ConnectivityManagerCompat {
    @SuppressLint("UniqueConstants")
    @IntDef(
        ConnectivityManagerHidden.FIREWALL_CHAIN_DOZABLE,
        ConnectivityManagerHidden.FIREWALL_CHAIN_STANDBY,
        ConnectivityManagerHidden.FIREWALL_CHAIN_POWERSAVE,
        ConnectivityManagerHidden.FIREWALL_CHAIN_RESTRICTED,
        ConnectivityManagerHidden.FIREWALL_CHAIN_LOW_POWER_STANDBY,
        ConnectivityManagerHidden.FIREWALL_CHAIN_LOCKDOWN_VPN,
        ConnectivityManagerHidden.FIREWALL_CHAIN_BACKGROUND,
        ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_1,
        ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_2,
        ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_3,
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Retention(AnnotationRetention.SOURCE)
    annotation class FirewallChain

    @IntDef(
        ConnectivityManagerHidden.FIREWALL_RULE_DEFAULT,
        ConnectivityManagerHidden.FIREWALL_RULE_ALLOW,
        ConnectivityManagerHidden.FIREWALL_RULE_DENY,
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Retention(AnnotationRetention.SOURCE)
    annotation class FirewallRule

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Throws(RemoteException::class)
    fun setUidFirewallRule(@FirewallChain chain: Int, uid: Int, @FirewallRule rule: Int) {
        getConnectivityManager().setUidFirewallRule(chain, uid, rule)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @FirewallRule
    @Throws(RemoteException::class)
    fun getUidFirewallRule(@FirewallChain chain: Int, uid: Int): Int =
        getConnectivityManager().getUidFirewallRule(chain, uid)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Throws(RemoteException::class)
    fun setFirewallChainEnabled(@FirewallChain chain: Int, enable: Boolean) {
        getConnectivityManager().setFirewallChainEnabled(chain, enable)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Throws(RemoteException::class)
    fun getFirewallChainEnabled(@FirewallChain chain: Int): Boolean =
        getConnectivityManager().getFirewallChainEnabled(chain)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Throws(RemoteException::class)
    fun replaceFirewallChain(@FirewallChain chain: Int, uids: IntArray) {
        getConnectivityManager().replaceFirewallChain(chain, uids)
    }

    private fun getConnectivityManager(): IConnectivityManager =
        IConnectivityManager.Stub.asInterface(ProxyBinder.getService(Context.CONNECTIVITY_SERVICE))
}
