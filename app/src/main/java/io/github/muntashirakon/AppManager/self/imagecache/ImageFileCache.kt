// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.imagecache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.io.IoUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

internal class ImageFileCache {
    private val mCacheDir: File = File(FileUtils.getCachePath(), "images").also {
        if (!it.exists() && !it.mkdirs()) {
            throw IllegalStateException("Could not create cache. Is this OS broken?")
        }
    }

    @Throws(IOException::class)
    fun putImage(name: String, inputStream: InputStream) {
        FileOutputStream(getImageFile(name)).use { os -> IoUtils.copy(inputStream, os) }
    }

    @Throws(IOException::class)
    fun putImage(name: String, drawable: Drawable) {
        putImage(name, UIUtils.getBitmapFromDrawable(drawable))
    }

    @Throws(IOException::class)
    fun putImage(name: String, bitmap: Bitmap) {
        FileOutputStream(getImageFile(name)).use { os ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            os.flush()
        }
    }

    fun getImage(name: String): Bitmap? {
        val iconFile = getImageFile(name)
        if (iconFile.lastModified() >= LAST_MODIFIED_DATE) {
            return try {
                FileInputStream(iconFile).use { BitmapFactory.decodeStream(it) }
            } catch (e: IOException) {
                null
            }
        }
        return null
    }

    fun getImageDrawable(name: String): Drawable? {
        val iconFile = getImageFile(name)
        if (iconFile.lastModified() >= LAST_MODIFIED_DATE) {
            return try {
                FileInputStream(iconFile).use { Drawable.createFromStream(it, name) }
            } catch (e: IOException) {
                null
            }
        }
        return null
    }

    fun clear() {
        val files = mCacheDir.listFiles() ?: return
        var count = 0
        for (f in files) {
            if (f.lastModified() < LAST_MODIFIED_DATE && f.delete()) ++count
        }
        Log.d("Cache", "Deleted %d images.", count)
    }

    private fun getImageFile(name: String): File = File(mCacheDir, "$name.png")

    companion object {
        private val LAST_MODIFIED_DATE = System.currentTimeMillis() - 604_800_000
    }
}
