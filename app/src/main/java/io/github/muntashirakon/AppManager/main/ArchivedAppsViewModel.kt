// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.muntashirakon.AppManager.db.dao.ArchivedAppDao
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchivedAppsViewModel @Inject constructor(
    private val archivedAppDao: ArchivedAppDao
) : ViewModel() {
    val archivedApps: StateFlow<List<ArchivedApp>> = archivedAppDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun delete(archivedApp: ArchivedApp) {
        viewModelScope.launch {
            archivedAppDao.delete(archivedApp)
        }
    }
}
