// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.util.Size
import com.j256.simplemagic.ContentType
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ThumbnailUtilsCompat
import io.github.muntashirakon.AppManager.fm.ContentType2
import io.github.muntashirakon.AppManager.fm.FmItem
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.svg.SVGParser
import io.github.muntashirakon.util.UiUtils
import java.io.IOException
import java.util.Locale

class FmIconFetcher(private val mFmItem: FmItem) : ImageLoader.ImageFetcherInterface {
    override fun fetchImage(tag: String): ImageLoader.ImageFetcherResult {
        var contentInfo = mFmItem.getContentInfo()
        if (contentInfo == null) {
            contentInfo = mFmItem.path.getPathContentInfo()
            mFmItem.setContentInfo(contentInfo)
        }
        val mimeType = contentInfo.mimeType
        val drawableRes = FmIcons.getDrawableFromType(mimeType)
        val padding = UiUtils.dpToPx(ContextUtils.getContext(), 4)
        val length = UiUtils.dpToPx(ContextUtils.getContext(), 40)
        val defaultImage = ImageLoader.DefaultImageDrawableRes("drawable_$drawableRes", drawableRes, padding)
        val size = Size(length, length)

        if (OPEN_DOCUMENT_FORMAT_MIME_TYPES.contains(mimeType)) {
            val bitmap = FmIcons.getOpenDocumentThumbnail(mFmItem.path)
            if (bitmap != null) {
                return ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage)
            }
        }

        if (FmIcons.isApk(drawableRes)) {
            val bitmap = when {
                ContentType.APK.mimeType == mimeType -> FmIcons.generateApkIcon(mFmItem.path)
                ContentType2.APKM.mimeType == mimeType -> FmIcons.getApkmIcon(mFmItem.path)
                else -> FmIcons.getApksIcon(mFmItem.path)
            }
            if (bitmap != null) {
                return ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage)
            }
        } else if (FmIcons.isArchive(drawableRes)) {
            if (ContentType.JAVA_ARCHIVE.mimeType == mimeType) {
                val bitmap = FmIcons.generateJ2meIcon(mFmItem.path)
                if (bitmap != null) {
                    return ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage)
                }
            }
        } else if (FmIcons.isAudio(drawableRes)) {
            try {
                val bitmap = ThumbnailUtilsCompat.createAudioThumbnail(ContextUtils.getContext(), FmProvider.getContentUri(mFmItem.path), size, null)
                return ImageLoader.ImageFetcherResult(tag, bitmap, false, true, defaultImage)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else if (FmIcons.isVideo(drawableRes)) {
            try {
                val bitmap = ThumbnailUtilsCompat.createVideoThumbnail(ContextUtils.getContext(), FmProvider.getContentUri(mFmItem.path), size, null)
                return ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else if (FmIcons.isImage(drawableRes)) {
            if (ContentType.SVG.mimeType == mimeType) {
                try {
                    mFmItem.path.openInputStream().use { is_ ->
                        val svg = SVGParser.getSVGFromInputStream(is_)
                        val bitmap = svg.getBitmap()
                        return ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage)
                    }
                } catch (th: Throwable) {
                    th.printStackTrace()
                }
            } else {
                val bytes = mFmItem.path.getContentAsBinary()
                if (bytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        return ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage)
                    }
                }
            }
        } else if (FmIcons.isEbook(drawableRes)) {
            if (ContentType.EPUB.mimeType == mimeType) {
                val bitmap = FmIcons.generateEpubCover(mFmItem.path)
                if (bitmap != null) {
                    return ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage)
                }
            }
        } else if (FmIcons.isFont(drawableRes)) {
            val bitmap = FmIcons.generateFontBitmap(mFmItem.path)
            if (bitmap != null) {
                return ImageLoader.ImageFetcherResult(tag, bitmap, false, true, defaultImage)
            }
        } else if (FmIcons.isPdf(drawableRes)) {
            val bitmap = FmIcons.generatePdfBitmap(ContextUtils.getContext(), FmProvider.getContentUri(mFmItem.path))
            if (bitmap != null) {
                return ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage)
            }
        } else if (FmIcons.isGeneric(drawableRes)) {
            val extension = mFmItem.path.extension
            if (extension != null) {
                val len = minOf(extension.length, 4)
                val shortExt = extension.substring(0, len).uppercase(Locale.ROOT)
                val extTag = "fm_ext_$shortExt"
                return ImageLoader.ImageFetcherResult(tag, null, ImageLoader.DefaultImageString(extTag, shortExt))
            }
            if (mFmItem.path.canExecute()) {
                val execDrawableRes = R.drawable.ic_frost_termux
                return ImageLoader.ImageFetcherResult(tag, null,
                    ImageLoader.DefaultImageDrawableRes("drawable_$execDrawableRes", execDrawableRes, padding))
            }
        }
        return ImageLoader.ImageFetcherResult(tag, null, defaultImage)
    }

    private fun getThumbnail(bitmap: Bitmap, size: Size, recycle: Boolean): Bitmap =
        ThumbnailUtils.extractThumbnail(bitmap, size.width, size.height,
            if (recycle) ThumbnailUtils.OPTIONS_RECYCLE_INPUT else 0)

    companion object {
        private val OPEN_DOCUMENT_FORMAT_MIME_TYPES: Set<String> = setOf(
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.oasis.opendocument.graphics",
            "application/vnd.oasis.opendocument.chart",
            "application/vnd.oasis.opendocument.formula",
            "application/vnd.oasis.opendocument.image",
            "application/vnd.oasis.opendocument.text-master",
            "application/vnd.sun.xml.base",
            "application/vnd.oasis.opendocument.base",
            "application/vnd.oasis.opendocument.database",
            // Templates
            "application/vnd.oasis.opendocument.text-template",
            "application/vnd.oasis.opendocument.spreadsheet-template",
            "application/vnd.oasis.opendocument.presentation-template",
            "application/vnd.oasis.opendocument.graphics-template",
            "application/vnd.oasis.opendocument.chart-template",
            "application/vnd.oasis.opendocument.formula-template",
            "application/vnd.oasis.opendocument.text-web",
        )
    }
}
