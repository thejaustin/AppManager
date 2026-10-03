// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class ArchivedAppsActivity : BaseActivity() {

    private val viewModel: ArchivedAppsViewModel by viewModels()
    private var adapter: ArchivedAppsAdapter? = null
    private var emptyViewContainer: View? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_archived_apps)

        val toolbar: MaterialToolbar? = findViewById(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setTitle(R.string.archived_apps)
            }
        }

        val recyclerView: RecyclerView = findViewById(R.id.archived_apps_list)
        emptyViewContainer = findViewById(R.id.empty_view_container)
        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.archivedApps.collect { archivedApps ->
                    if (adapter == null) {
                        adapter = ArchivedAppsAdapter(
                            archivedApps,
                            restoreListener = { onRestoreClicked(it) },
                            deleteListener = { onDeleteClicked(it) },
                        )
                        recyclerView.adapter = adapter
                    } else {
                        adapter!!.updateData(archivedApps)
                    }
                    emptyViewContainer?.visibility = if (archivedApps.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (archivedApps.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onRestoreClicked(archivedApp: ArchivedApp) {
        val apkPath = archivedApp.apkPath
        if (apkPath != null && File(apkPath).exists()) {
            startActivity(PackageInstallerActivity.getLaunchableInstance(this, Uri.fromFile(File(apkPath))))
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${archivedApp.packageName}")))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${archivedApp.packageName}")))
        }
    }

    private fun onDeleteClicked(archivedApp: ArchivedApp) {
        viewModel.delete(archivedApp)
    }
}
