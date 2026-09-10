// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.net.ConnectivityManagerHidden
import android.os.Build
import android.os.RemoteException
import io.github.muntashirakon.AppManager.compat.ConnectivityManagerCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride

class ConnectivityPermissionOverridePlatform : PermissionOverrideReconciler.Platform {
    @Throws(Exception::class)
    override fun resolveUid(packageName: String, userId: Int): Int {
        val info = PackageManagerCompat.getApplicationInfo(packageName, 0, userId)
        return info.uid
    }

    @Throws(Exception::class)
    override fun isEnforced(uid: Int, override: PermissionOverride): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val firewallChain = chain(override)
        if (!ConnectivityManagerCompat.getFirewallChainEnabled(firewallChain)) return false
        val rule = ConnectivityManagerCompat.getUidFirewallRule(firewallChain, uid)
        val expected = if (override.desiredGranted) {
            ConnectivityManagerHidden.FIREWALL_RULE_DEFAULT
        } else {
            ConnectivityManagerHidden.FIREWALL_RULE_DENY
        }
        return rule == expected
    }

    @Throws(Exception::class)
    override fun apply(uid: Int, override: PermissionOverride) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw RemoteException("Firewall overlays require Android 13+")
        }
        val firewallChain = chain(override)
        ConnectivityManagerCompat.setFirewallChainEnabled(firewallChain, true)
        val rule = if (override.desiredGranted) {
            ConnectivityManagerHidden.FIREWALL_RULE_DEFAULT
        } else {
            ConnectivityManagerHidden.FIREWALL_RULE_DENY
        }
        ConnectivityManagerCompat.setUidFirewallRule(firewallChain, uid, rule)
    }

    private fun chain(override: PermissionOverride): Int = override.controller.toInt()
}
