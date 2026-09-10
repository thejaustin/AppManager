// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio

import android.net.Uri
import android.os.Bundle
import io.github.muntashirakon.AppManager.PerProcessActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.intercept.IntentCompat

class AudioPlayerActivity : PerProcessActivity() {
    override fun getTransparentBackground(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)
        val uriList = IntentCompat.getDataUris(intent)
        if (uriList == null) {
            finish()
            return
        }
        val fragment = AudioPlayerDialogFragment.getInstance(uriList.toTypedArray(), true)
        fragment.show(supportFragmentManager, AudioPlayerDialogFragment.TAG)
    }
}
