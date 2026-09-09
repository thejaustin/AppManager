// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.Manifest
import android.app.Application
import android.os.UserHandleHidden
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.AppManager.self.SelfPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InstallerPreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val _canInstallPackages = MutableLiveData<Boolean>()
    val canInstallPackages: LiveData<Boolean> get() = _canInstallPackages

    private val _isSystemOrRootOrShell = MutableLiveData<Boolean>()
    val isSystemOrRootOrShell: LiveData<Boolean> get() = _isSystemOrRootOrShell

    private val _canModifyAppComponentStates = MutableLiveData<Boolean>()
    val canModifyAppComponentStates: LiveData<Boolean> get() = _canModifyAppComponentStates

    fun loadCanInstallPackagesPermission() {
        viewModelScope.launch(Dispatchers.IO) {
            _canInstallPackages.postValue(
                SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)
            )
        }
    }

    fun loadSystemOrRootOrShellStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSystemOrRootOrShell.postValue(SelfPermissions.isSystemOrRootOrShell())
        }
    }

    fun loadCanModifyAppComponentStates() {
        viewModelScope.launch(Dispatchers.IO) {
            _canModifyAppComponentStates.postValue(
                SelfPermissions.canModifyAppComponentStates(UserHandleHidden.myUserId(), null, true)
            )
        }
    }
}
