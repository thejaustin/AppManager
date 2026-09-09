// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.annotation.RawRes
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.Locale

/**
 * Read and parse res/raw/changelog.xml.
 */
// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
open class ChangelogParser(
    protected val mContext: Context,
    @RawRes private val mChangeLogFileResourceId: Int,
    private val mStartVersion: Long = 0,
) {
    constructor(context: Context, @RawRes changeLogFileResourceId: Int) :
        this(context, changeLogFileResourceId, 0)

    @Throws(IOException::class, XmlPullParserException::class)
    fun parse(): Changelog {
        return mContext.resources.openRawResource(mChangeLogFileResourceId).use { is_ ->
            val changelog = Changelog()
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(is_, "UTF-8")
                nextTag()
            }
            readChangelogTag(parser, changelog)
            changelog
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    protected open fun readChangelogTag(parser: XmlPullParser, changeLog: Changelog) {
        if (parser.depth != 1) {
            Log.e(TAG, String.format(Locale.ROOT, "Invalid depth %d, expecting depth 1.", parser.depth))
            return
        }
        parser.require(XmlPullParser.START_TAG, null, TAG_CHANGELOG)
        changeLog.isBulletedList = "true" == parser.getAttributeValue(null, ATTR_BULLET)

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.depth != 2) continue
            if (TAG_RELEASE == parser.name) {
                readReleaseTag(parser, changeLog)
            } else {
                Log.w(TAG, String.format(Locale.ROOT, "Unknown tag (%s) at depth 2." + parser.name))
            }
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readReleaseTag(parser: XmlPullParser, changeLog: Changelog) {
        if (parser.depth != 2) {
            Log.e(TAG, String.format(Locale.ROOT, "Invalid depth %d, expecting depth 2.", parser.depth))
            return
        }
        parser.require(XmlPullParser.START_TAG, null, TAG_RELEASE)

        val versionName = requireNotNull(parser.getAttributeValue(null, ATTR_RELEASE_VERSION))
        val versionCodeStr = requireNotNull(parser.getAttributeValue(null, ATTR_RELEASE_CODE))
        val versionCode = versionCodeStr.toLongOrNull().also {
            if (it == null) Log.w(TAG, "Error while parsing versionCode.")
        } ?: 0L
        val releaseDate = requireNotNull(parser.getAttributeValue(null, ATTR_RELEASE_DATE))
        val releaseType = requireNotNull(parser.getAttributeValue(null, ATTR_RELEASE_TYPE))

        if (versionCode < mStartVersion) {
            while (parser.next() != XmlPullParser.END_TAG) { /* skip */ }
            return
        }

        changeLog.addItem(ChangelogHeader(versionName, versionCode, releaseType, releaseDate))

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.depth != 3) continue
            if (CHANGE_LOG_TAGS.contains(parser.name)) {
                readChangelogItemTags(parser, changeLog)
            } else {
                Log.w(TAG, String.format(Locale.ROOT, "Unknown tag (%s) at depth 3." + parser.name))
            }
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readChangelogItemTags(parser: XmlPullParser, changeLog: Changelog) {
        if (parser.depth != 3) {
            Log.e(TAG, String.format(Locale.ROOT, "Invalid depth %d, expecting depth 3.", parser.depth))
            return
        }
        val tag = parser.name
        val changeTextType = parser.getAttributeValue(null, ATTR_TYPE)
        val title = parser.getAttributeValue(null, ATTR_TITLE)
        val showBullet = parser.getAttributeValue(null, ATTR_BULLET)
        val subtext = parser.getAttributeValue(null, ATTR_SUBTEXT)

        var changeText: String? = null
        if (parser.next() == XmlPullParser.TEXT) {
            changeText = parser.text
            parser.nextTag()
        }

        val type = when (tag) {
            TAG_TITLE -> ChangelogItem.TITLE
            TAG_NEW -> ChangelogItem.NEW
            TAG_IMPROVE -> ChangelogItem.IMPROVE
            TAG_FIX -> ChangelogItem.FIX
            else -> ChangelogItem.NOTE
        }

        val changelogItem = if (changeText == null) ChangelogItem(type) else ChangelogItem(changeText, type)
        changelogItem.changeTitle = title
        changelogItem.isBulletedList = "true" == showBullet || changeLog.isBulletedList
        changelogItem.changeTextType = getChangeTextType(changeTextType)
        changelogItem.isSubtext = "true" == subtext
        changeLog.addItem(changelogItem)
    }

    companion object {
        private const val TAG = "ChangelogParser"

        private const val TAG_CHANGELOG = "changelog"
        private const val TAG_RELEASE = "release"
        private const val TAG_TITLE = "title"
        private const val TAG_NEW = "new"
        private const val TAG_IMPROVE = "improve"
        private const val TAG_FIX = "fix"
        private const val TAG_NOTE = "note"

        private const val ATTR_RELEASE_TYPE = "type"
        private const val ATTR_RELEASE_VERSION = "version"
        private const val ATTR_RELEASE_CODE = "code"
        private const val ATTR_RELEASE_DATE = "date"

        private const val ATTR_TYPE = "type"
        private const val ATTR_TITLE = "title"
        private const val ATTR_BULLET = "bullet"
        private const val ATTR_SUBTEXT = "subtext"

        private val CHANGE_LOG_TAGS = listOf(TAG_TITLE, TAG_NEW, TAG_IMPROVE, TAG_FIX, TAG_NOTE)

        @ChangelogItem.ChangeTextType
        private fun getChangeTextType(rawText: String?): Int = when (rawText) {
            "large" -> ChangelogItem.TEXT_LARGE
            "small" -> ChangelogItem.TEXT_SMALL
            else -> ChangelogItem.TEXT_MEDIUM
        }
    }
}
