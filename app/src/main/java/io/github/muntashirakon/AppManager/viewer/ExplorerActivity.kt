// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer

import android.content.Intent
import android.os.Bundle
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.fm.FmActivity
import io.github.muntashirakon.AppManager.intercept.IntentCompat

class ExplorerActivity : BaseActivity() {
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        val uri = IntentCompat.getDataUri(intent)
        if (uri == null) {
            finish()
            return
        }
        val options = FmActivity.Options(uri, true, true, true)
        val intent = Intent(this, FmActivity::class.java).apply {
            putExtra(FmActivity.EXTRA_OPTIONS, options)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        startActivity(intent)
        finish()
    }

    override fun getTransparentBackground(): Boolean = true
}
