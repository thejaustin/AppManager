// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

class CompositePermissionController(providers: List<IPermissionController>) : IPermissionController {
    private val mProviders: List<IPermissionController> = providers.toList()

    override fun getId(): String = "composite"

    override fun supports(context: PermissionContext): Boolean {
        return mProviders.any { it.supports(context) }
    }

    override fun getState(context: PermissionContext): PermissionControllerState {
        var state = PermissionState.GRANTED
        var modifiable = false
        var action: PermissionUserAction? = null
        var found = false
        for (provider in mProviders) {
            if (!provider.supports(context)) continue
            found = true
            val providerState = provider.getState(context)
            modifiable = modifiable or providerState.modifiable
            if (action == null) {
                action = providerState.userAction
            }
            when {
                providerState.state == PermissionState.DENIED -> state = PermissionState.DENIED
                providerState.state == PermissionState.UNKNOWN && state == PermissionState.GRANTED -> state = PermissionState.UNKNOWN
                providerState.state == PermissionState.UNSUPPORTED -> state = PermissionState.UNKNOWN
            }
        }
        return PermissionControllerState(getId(), if (found) state else PermissionState.UNSUPPORTED, modifiable, action)
    }

    override fun setGranted(context: PermissionContext, granted: Boolean): PermissionChangeResult {
        var found = false
        for (provider in mProviders) {
            if (!provider.supports(context)) continue
            found = true
            val result = provider.setGranted(context, granted)
            if (!result.isSuccessful()) return result
        }
        return if (found) {
            PermissionChangeResult.success()
        } else {
            PermissionChangeResult.unsupported("No permission provider supports ${context.permission.getName()}")
        }
    }
}
