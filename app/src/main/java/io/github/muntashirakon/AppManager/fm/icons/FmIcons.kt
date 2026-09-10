// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons

// Mostly taken from: https://github.com/zhanghai/MaterialFiles/blob/43a22b31d59a59e44a05a972269a3bd9cd0c9b8b/app/src/main/java/me/zhanghai/android/files/file/MimeTypeIcon.kt
// Others are freelanced.

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.DrawableRes
import androidx.core.util.Pair
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.ApkFile
import io.github.muntashirakon.AppManager.apk.UriApkSource
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.io.Path
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipFile

internal object FmIcons {
    private val DRAWABLE_APK = R.drawable.ic_android
    private val DRAWABLE_ARCHIVE = R.drawable.ic_archive
    private val DRAWABLE_AUDIO = R.drawable.ic_audio_file
    private val DRAWABLE_CALENDAR = R.drawable.ic_calendar_month
    private val DRAWABLE_CERTIFICATE = R.drawable.ic_shield_key
    private val DRAWABLE_CODE = R.drawable.ic_code
    private val DRAWABLE_CONTACT = R.drawable.ic_contact_page
    private val DRAWABLE_DATABASE = R.drawable.ic_database
    private val DRAWABLE_DIRECTORY = R.drawable.ic_folder
    private val DRAWABLE_DOCUMENT = R.drawable.ic_file
    private val DRAWABLE_EBOOK = R.drawable.ic_book
    private val DRAWABLE_EMAIL = R.drawable.ic_email
    private val DRAWABLE_FONT = R.drawable.ic_font_download
    private val DRAWABLE_GENERIC = R.drawable.ic_file
    private val DRAWABLE_IMAGE = R.drawable.ic_image
    private val DRAWABLE_PDF = R.drawable.ic_pdf_file
    private val DRAWABLE_PRESENTATION = R.drawable.ic_presentation
    private val DRAWABLE_SPREADSHEET = R.drawable.ic_table
    private val DRAWABLE_TEXT = R.drawable.ic_file_document
    private val DRAWABLE_VIDEO = R.drawable.ic_video_file
    private val DRAWABLE_WORD = DRAWABLE_DOCUMENT
    private val DRAWABLE_EXCEL = DRAWABLE_SPREADSHEET
    private val DRAWABLE_POWERPOINT = DRAWABLE_PRESENTATION

    private val sMimeTypeToIconMap: Map<String, Int> = mapOf(
        "application/vnd.android.package-archive" to DRAWABLE_APK,
        "application/vnd.apkm" to DRAWABLE_APK,
        "application/x-apks" to DRAWABLE_APK,
        "application/xapk-package-archive" to DRAWABLE_APK,
        "application/gzip" to DRAWABLE_ARCHIVE,
        "application/java-archive" to DRAWABLE_ARCHIVE,
        "application/mac-binhex40" to DRAWABLE_ARCHIVE,
        "application/rar" to DRAWABLE_ARCHIVE,
        "application/zip" to DRAWABLE_ARCHIVE,
        "application/vnd.debian.binary-package" to DRAWABLE_ARCHIVE,
        "application/vnd.ms-cab-compressed" to DRAWABLE_ARCHIVE,
        "application/vnd.rar" to DRAWABLE_ARCHIVE,
        "application/x-7z-compressed" to DRAWABLE_ARCHIVE,
        "application/x-apple-diskimage" to DRAWABLE_ARCHIVE,
        "application/x-bzip" to DRAWABLE_ARCHIVE,
        "application/x-bzip2" to DRAWABLE_ARCHIVE,
        "application/x-compress" to DRAWABLE_ARCHIVE,
        "application/x-cpio" to DRAWABLE_ARCHIVE,
        "application/x-deb" to DRAWABLE_ARCHIVE,
        "application/x-debian-package" to DRAWABLE_ARCHIVE,
        "application/x-gtar" to DRAWABLE_ARCHIVE,
        "application/x-gtar-compressed" to DRAWABLE_ARCHIVE,
        "application/x-gzip" to DRAWABLE_ARCHIVE,
        "application/x-iso9660-image" to DRAWABLE_ARCHIVE,
        "application/x-java-archive" to DRAWABLE_ARCHIVE,
        "application/x-lha" to DRAWABLE_ARCHIVE,
        "application/x-lzh" to DRAWABLE_ARCHIVE,
        "application/x-lzma" to DRAWABLE_ARCHIVE,
        "application/x-lzx" to DRAWABLE_ARCHIVE,
        "application/x-rar" to DRAWABLE_ARCHIVE,
        "application/x-rar-compressed" to DRAWABLE_ARCHIVE,
        "application/x-stuffit" to DRAWABLE_ARCHIVE,
        "application/x-tar" to DRAWABLE_ARCHIVE,
        "application/x-webarchive" to DRAWABLE_ARCHIVE,
        "application/x-webarchive-xml" to DRAWABLE_ARCHIVE,
        "application/x-xz" to DRAWABLE_ARCHIVE,
        "application/ogg" to DRAWABLE_AUDIO,
        "application/x-flac" to DRAWABLE_AUDIO,
        "text/calendar" to DRAWABLE_CALENDAR,
        "text/x-vcalendar" to DRAWABLE_CALENDAR,
        "application/pem-certificate-chain" to DRAWABLE_CERTIFICATE,
        "application/pgp" to DRAWABLE_CERTIFICATE,
        "application/pgp-encrypted" to DRAWABLE_CERTIFICATE,
        "application/pgp-keys" to DRAWABLE_CERTIFICATE,
        "application/pgp-signature" to DRAWABLE_CERTIFICATE,
        "application/pkcs8" to DRAWABLE_CERTIFICATE,
        "application/x-pkcs12" to DRAWABLE_CERTIFICATE,
        "application/x-pkcs7-certificates" to DRAWABLE_CERTIFICATE,
        "application/x-pkcs7-certreqresp" to DRAWABLE_CERTIFICATE,
        "application/x-pkcs7-crl" to DRAWABLE_CERTIFICATE,
        "application/x-pkcs7-mime" to DRAWABLE_CERTIFICATE,
        "application/x-pkcs7-signature" to DRAWABLE_CERTIFICATE,
        "application/x-x509-ca-cert" to DRAWABLE_CERTIFICATE,
        "application/x-x509-server-cert" to DRAWABLE_CERTIFICATE,
        "application/x-x509-user-cert" to DRAWABLE_CERTIFICATE,
        "application/ecmascript" to DRAWABLE_CODE,
        "application/javascript" to DRAWABLE_CODE,
        "application/json" to DRAWABLE_CODE,
        "application/toml" to DRAWABLE_CODE,
        "application/typescript" to DRAWABLE_CODE,
        "application/xml" to DRAWABLE_CODE,
        "application/x-csh" to DRAWABLE_CODE,
        "application/x-ecmascript" to DRAWABLE_CODE,
        "application/x-javascript" to DRAWABLE_CODE,
        "application/x-latex" to DRAWABLE_CODE,
        "application/x-perl" to DRAWABLE_CODE,
        "application/x-plist" to DRAWABLE_CODE,
        "application/x-python" to DRAWABLE_CODE,
        "application/x-ruby" to DRAWABLE_CODE,
        "application/x-sh" to DRAWABLE_CODE,
        "application/x-shellscript" to DRAWABLE_CODE,
        "application/x-smali" to DRAWABLE_CODE,
        "application/x-texinfo" to DRAWABLE_CODE,
        "application/x-yaml" to DRAWABLE_CODE,
        "text/css" to DRAWABLE_CODE,
        "text/html" to DRAWABLE_CODE,
        "text/ecmascript" to DRAWABLE_CODE,
        "text/javascript" to DRAWABLE_CODE,
        "text/jscript" to DRAWABLE_CODE,
        "text/livescript" to DRAWABLE_CODE,
        "text/xml" to DRAWABLE_CODE,
        "text/x-asm" to DRAWABLE_CODE,
        "text/x-c++hdr" to DRAWABLE_CODE,
        "text/x-c++src" to DRAWABLE_CODE,
        "text/x-chdr" to DRAWABLE_CODE,
        "text/x-csh" to DRAWABLE_CODE,
        "text/x-csharp" to DRAWABLE_CODE,
        "text/x-csrc" to DRAWABLE_CODE,
        "text/x-dsrc" to DRAWABLE_CODE,
        "text/x-ecmascript" to DRAWABLE_CODE,
        "text/x-haskell" to DRAWABLE_CODE,
        "text/x-java" to DRAWABLE_CODE,
        "text/x-java-source" to DRAWABLE_CODE,
        "text/x-javascript" to DRAWABLE_CODE,
        "text/x-kotlin" to DRAWABLE_CODE,
        "text/x-literate-haskell" to DRAWABLE_CODE,
        "text/x-lua" to DRAWABLE_CODE,
        "text/x-pascal" to DRAWABLE_CODE,
        "text/x-perl" to DRAWABLE_CODE,
        "text/x-php" to DRAWABLE_CODE,
        "text/x-python" to DRAWABLE_CODE,
        "text/x-ruby" to DRAWABLE_CODE,
        "text/x-shellscript" to DRAWABLE_CODE,
        "text/x-tcl" to DRAWABLE_CODE,
        "text/x-tex" to DRAWABLE_CODE,
        "text/x-yaml" to DRAWABLE_CODE,
        "text/vcard" to DRAWABLE_CONTACT,
        "text/x-vcard" to DRAWABLE_CONTACT,
        "application/vnd.sqlite3" to DRAWABLE_DATABASE,
        "application/x-sqlite3" to DRAWABLE_DATABASE,
        "inode/directory" to DRAWABLE_DIRECTORY,
        DocumentsContract.Document.MIME_TYPE_DIR to DRAWABLE_DIRECTORY,
        "resource/folder" to DRAWABLE_DIRECTORY,
        "application/rtf" to DRAWABLE_DOCUMENT,
        "application/vnd.oasis.opendocument.text" to DRAWABLE_DOCUMENT,
        "application/vnd.oasis.opendocument.text-master" to DRAWABLE_DOCUMENT,
        "application/vnd.oasis.opendocument.text-template" to DRAWABLE_DOCUMENT,
        "application/vnd.oasis.opendocument.text-web" to DRAWABLE_DOCUMENT,
        "application/vnd.stardivision.writer" to DRAWABLE_DOCUMENT,
        "application/vnd.stardivision.writer-global" to DRAWABLE_DOCUMENT,
        "application/vnd.sun.xml.writer" to DRAWABLE_DOCUMENT,
        "application/vnd.sun.xml.writer.global" to DRAWABLE_DOCUMENT,
        "application/vnd.sun.xml.writer.template" to DRAWABLE_DOCUMENT,
        "application/x-abiword" to DRAWABLE_DOCUMENT,
        "application/x-kword" to DRAWABLE_DOCUMENT,
        "text/rtf" to DRAWABLE_DOCUMENT,
        "application/epub+zip" to DRAWABLE_EBOOK,
        "application/vnd.amazon.ebook" to DRAWABLE_EBOOK,
        "application/x-cbr" to DRAWABLE_EBOOK,
        "application/x-cbz" to DRAWABLE_EBOOK,
        "application/x-ibooks+zip" to DRAWABLE_EBOOK,
        "application/x-mobipocket-ebook" to DRAWABLE_EBOOK,
        "application/vnd.ms-outlook" to DRAWABLE_EMAIL,
        "message/rfc822" to DRAWABLE_EMAIL,
        "application/font-cff" to DRAWABLE_FONT,
        "application/font-off" to DRAWABLE_FONT,
        "application/font-sfnt" to DRAWABLE_FONT,
        "application/font-ttf" to DRAWABLE_FONT,
        "application/font-woff" to DRAWABLE_FONT,
        "application/vnd.ms-fontobject" to DRAWABLE_FONT,
        "application/vnd.ms-opentype" to DRAWABLE_FONT,
        "application/x-font" to DRAWABLE_FONT,
        "application/x-font-otf" to DRAWABLE_FONT,
        "application/x-font-ttf" to DRAWABLE_FONT,
        "application/x-font-woff" to DRAWABLE_FONT,
        "application/vnd.oasis.opendocument.graphics" to DRAWABLE_IMAGE,
        "application/vnd.oasis.opendocument.graphics-template" to DRAWABLE_IMAGE,
        "application/vnd.oasis.opendocument.image" to DRAWABLE_IMAGE,
        "application/vnd.stardivision.draw" to DRAWABLE_IMAGE,
        "application/vnd.sun.xml.draw" to DRAWABLE_IMAGE,
        "application/vnd.sun.xml.draw.template" to DRAWABLE_IMAGE,
        "application/vnd.visio" to DRAWABLE_IMAGE,
        "application/pdf" to DRAWABLE_PDF,
        "application/vnd.oasis.opendocument.presentation" to DRAWABLE_PRESENTATION,
        "application/vnd.oasis.opendocument.presentation-template" to DRAWABLE_PRESENTATION,
        "application/vnd.stardivision.impress" to DRAWABLE_PRESENTATION,
        "application/vnd.sun.xml.impress" to DRAWABLE_PRESENTATION,
        "application/vnd.sun.xml.impress.template" to DRAWABLE_PRESENTATION,
        "application/x-kpresenter" to DRAWABLE_PRESENTATION,
        "application/vnd.oasis.opendocument.spreadsheet" to DRAWABLE_SPREADSHEET,
        "application/vnd.oasis.opendocument.spreadsheet-template" to DRAWABLE_SPREADSHEET,
        "application/vnd.stardivision.calc" to DRAWABLE_SPREADSHEET,
        "application/vnd.sun.xml.calc" to DRAWABLE_SPREADSHEET,
        "application/vnd.sun.xml.calc.template" to DRAWABLE_SPREADSHEET,
        "application/x-kspread" to DRAWABLE_SPREADSHEET,
        "application/x-quicktimeplayer" to DRAWABLE_VIDEO,
        "application/x-shockwave-flash" to DRAWABLE_VIDEO,
        "application/msword" to DRAWABLE_WORD,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to DRAWABLE_WORD,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.template" to DRAWABLE_WORD,
        "application/vnd.ms-excel" to DRAWABLE_EXCEL,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to DRAWABLE_EXCEL,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template" to DRAWABLE_EXCEL,
        "application/vnd.ms-powerpoint" to DRAWABLE_POWERPOINT,
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to DRAWABLE_POWERPOINT,
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow" to DRAWABLE_POWERPOINT,
        "application/vnd.openxmlformats-officedocument.presentationml.template" to DRAWABLE_POWERPOINT,
    )

    private val sTypeToIconMap: Map<String, Int> = mapOf(
        "audio" to DRAWABLE_AUDIO,
        "font" to DRAWABLE_FONT,
        "image" to DRAWABLE_IMAGE,
        "text" to DRAWABLE_TEXT,
        "video" to DRAWABLE_VIDEO,
    )

    @DrawableRes
    @JvmStatic
    fun getDrawableFromType(mimeType: String?): Int {
        if (mimeType == null) return DRAWABLE_GENERIC
        sMimeTypeToIconMap[mimeType]?.let { return it }
        val firstPart = mimeType.split("/")[0]
        return sTypeToIconMap[firstPart] ?: DRAWABLE_GENERIC
    }

    @JvmStatic fun isApk(@DrawableRes drawable: Int) = drawable == DRAWABLE_APK
    @JvmStatic fun isArchive(@DrawableRes drawable: Int) = drawable == DRAWABLE_ARCHIVE
    @JvmStatic fun isImage(@DrawableRes drawable: Int) = drawable == DRAWABLE_IMAGE
    @JvmStatic fun isAudio(@DrawableRes drawable: Int) = drawable == DRAWABLE_AUDIO
    @JvmStatic fun isVideo(@DrawableRes drawable: Int) = drawable == DRAWABLE_VIDEO
    @JvmStatic fun isMedia(@DrawableRes drawable: Int) = drawable == DRAWABLE_AUDIO || drawable == DRAWABLE_VIDEO
    @JvmStatic fun isEbook(@DrawableRes drawable: Int) = drawable == DRAWABLE_EBOOK
    @JvmStatic fun isFont(@DrawableRes drawable: Int) = drawable == DRAWABLE_FONT
    @JvmStatic fun isPdf(@DrawableRes drawable: Int) = drawable == DRAWABLE_PDF
    @JvmStatic fun isGeneric(@DrawableRes drawable: Int) = drawable == DRAWABLE_GENERIC

    @JvmStatic
    fun generateFontBitmap(path: Path): Bitmap? {
        val extension = path.extension
        val text = if (extension != null)
            extension.substring(0, minOf(extension.length, 4)).uppercase(Locale.ROOT)
        else "FONT"
        val file = getUsableFile(path) ?: return null
        return try {
            val typeface = Typeface.createFromFile(file.first)
            UIUtils.generateBitmapFromText(text, typeface)
        } finally {
            if (file.second) file.first!!.delete()
        }
    }

    @JvmStatic
    fun generatePdfBitmap(context: Context, uri: Uri): Bitmap? {
        val renderer = try {
            PdfRenderer(FileUtils.getFdFromUri(context, uri, "r"))
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        val page = try {
            renderer.openPage(0)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            return null
        }
        val srcWidth = page.width
        val srcHeight = page.height
        if (srcWidth <= 0 || srcHeight <= 0) return null
        val bitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    @JvmStatic
    fun generateEpubCover(path: Path): Bitmap? {
        val file = getUsableFile(path) ?: return null
        return try {
            EpubCoverGenerator.generateFromFile(file.first!!)
        } finally {
            if (file.second) file.first!!.delete()
        }
    }

    @JvmStatic
    fun generateJ2meIcon(path: Path): Bitmap? {
        val file = getUsableFile(path) ?: return null
        return try {
            J2meIconExtractor.generateFromFile(file.first!!)
        } finally {
            if (file.second) file.first!!.delete()
        }
    }

    @JvmStatic
    fun getOpenDocumentThumbnail(path: Path): Bitmap? {
        val file = getUsableFile(path) ?: return null
        return try {
            ZipFile(file.first).use { zipFile ->
                val coverEntry = zipFile.getEntry("Thumbnails/thumbnail.png")
                if (coverEntry != null) BitmapFactory.decodeStream(zipFile.getInputStream(coverEntry)) else null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } finally {
            if (file.second) file.first!!.delete()
        }
    }

    @JvmStatic
    fun generateApkIcon(path: Path): Bitmap? {
        val file = getUsableFile(path) ?: return null
        return try {
            getApkIcon(file.first!!)
        } finally {
            if (file.second) file.first!!.delete()
        }
    }

    @JvmStatic
    fun getApkmIcon(path: Path): Bitmap? {
        val file = getUsableFile(path) ?: return null
        return try {
            ZipFile(file.first).use { zipFile ->
                val iconEntry = zipFile.getEntry("icon.png")
                if (iconEntry != null) BitmapFactory.decodeStream(zipFile.getInputStream(iconEntry)) else null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } finally {
            if (file.second) file.first!!.delete()
        }
    }

    @JvmStatic
    fun getApksIcon(path: Path): Bitmap? {
        val file = getUsableFile(path) ?: return null
        return try {
            ZipFile(file.first).use { zipFile ->
                val iconEntry = zipFile.getEntry("icon.png")
                if (iconEntry != null) return BitmapFactory.decodeStream(zipFile.getInputStream(iconEntry))
                val apkSource = UriApkSource(Uri.fromFile(file.first), path.type)
                apkSource.resolve().use { apkFile ->
                    getApkIcon(apkFile.baseEntry.getFile(false))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: ApkFile.ApkFileException) {
            e.printStackTrace()
            null
        } finally {
            if (file.second) file.first!!.delete()
        }
    }

    private fun getApkIcon(apkFile: File): Bitmap? {
        val pm = ContextUtils.getContext().packageManager
        val f = apkFile.absolutePath
        val packageInfo = pm.getPackageArchiveInfo(f, 0) ?: return null
        val info: ApplicationInfo = packageInfo.applicationInfo
        info.sourceDir = f
        info.publicSourceDir = f
        if (info.icon != 0) {
            return UIUtils.getBitmapFromDrawable(info.loadIcon(pm))
        }
        return null
    }

    private fun getUsableFile(path: Path): Pair<File, Boolean>? {
        var f = path.file
        if (f == null) {
            return try {
                Pair(FileCache.getGlobalFileCache().getCachedFile(path), true)
            } catch (_: IOException) {
                null
            }
        }
        f = File(f.path)
        if (!f.canRead()) {
            return try {
                Pair(FileCache.getGlobalFileCache().getCachedFile(path), true)
            } catch (_: IOException) {
                null
            }
        }
        return Pair(f, false)
    }
}
