// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio

import android.graphics.Bitmap
import android.net.Uri

class AudioMetadata {
    lateinit var uri: Uri
    var cover: Bitmap? = null
    lateinit var title: String
    lateinit var artist: String
    lateinit var album: String
    lateinit var albumArtist: String
}
