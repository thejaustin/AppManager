// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import java.util.jar.Manifest
import java.util.zip.ZipFile

internal object J2meIconExtractor {
    fun generateFromFile(file: File): Bitmap? {
        try {
            ZipFile(file).use { zipFile ->
                val iconFile = getIconLocation(zipFile, zipFile.getEntry("META-INF/MANIFEST.MF"))
                    ?: return null
                val iconEntry = zipFile.getEntry(iconFile)
                if (iconEntry != null) {
                    return BitmapFactory.decodeStream(zipFile.getInputStream(iconEntry))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getIconLocation(zipFile: ZipFile, zipEntry: java.util.zip.ZipEntry?): String? {
        zipEntry ?: return null
        try {
            val manifest = Manifest(zipFile.getInputStream(zipEntry))
            val attributes = manifest.mainAttributes
            var icon = attributes.getValue("MIDlet-Icon")
            if (icon.isNullOrBlank()) {
                val midlet = "MIDlet-1"
                icon = attributes.getValue(midlet) ?: return null
                val start = icon.indexOf(',')
                if (start != -1) {
                    val end = icon.indexOf(',', start + 1)
                    if (end != -1) icon = icon.substring(start + 1, end)
                }
            }
            icon = icon.trim()
            if (icon.isEmpty()) return null
            while (icon[0] == '/') icon = icon.substring(1)
            return icon
        } catch (_: IOException) {
        }
        return null
    }
}
