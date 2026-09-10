// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.muntashirakon.compat.xml.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object EpubCoverGenerator {
    fun generateFromFile(file: File): Bitmap? {
        try {
            ZipFile(file).use { zipFile ->
                val opfFile = getOpfFileLocation(zipFile, zipFile.getEntry("META-INF/container.xml"))
                    ?: return null
                val coverId = getCoverId(zipFile, zipFile.getEntry(opfFile)) ?: return null
                val coverImage = getCover(zipFile, zipFile.getEntry(opfFile), coverId) ?: return null
                val parent = File(opfFile).parent
                val coverImageLocation = if (parent != null) "$parent${File.separator}$coverImage" else coverImage
                val coverEntry = zipFile.getEntry(coverImageLocation)
                if (coverEntry != null) {
                    return BitmapFactory.decodeStream(zipFile.getInputStream(coverEntry))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getOpfFileLocation(zipFile: ZipFile, zipEntry: ZipEntry?): String? {
        zipEntry ?: return null
        try {
            val parser = Xml.newFastPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8.name())
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "container")
            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    return parser.getAttributeValue(null, "full-path")
                }
                event = parser.next()
            }
        } catch (_: IOException) {
        } catch (_: XmlPullParserException) {
        }
        return null
    }

    private fun getCoverId(zipFile: ZipFile, zipEntry: ZipEntry?): String? {
        zipEntry ?: return null
        try {
            val parser = Xml.newFastPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8.name())
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "package")
            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG
                    && parser.name == "meta"
                    && parser.getAttributeValue(null, "name") == "cover") {
                    return parser.getAttributeValue(null, "content")
                }
                event = parser.next()
            }
        } catch (_: IOException) {
        } catch (_: XmlPullParserException) {
        }
        return null
    }

    private fun getCover(zipFile: ZipFile, zipEntry: ZipEntry?, coverId: String): String? {
        zipEntry ?: return null
        try {
            val parser = Xml.newFastPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8.name())
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "package")
            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    val id = parser.getAttributeValue(null, "id")
                    if (tagName == "item" && id == null) {
                        event = parser.next()
                        continue
                    }
                    val properties = parser.getAttributeValue(null, "properties")
                    if (coverId == id) {
                        return parser.getAttributeValue(null, "href")
                    } else if (properties == "cover-image") {
                        return parser.getAttributeValue(null, "href")
                    }
                }
                event = parser.next()
            }
        } catch (_: IOException) {
        } catch (_: XmlPullParserException) {
        }
        return null
    }
}
