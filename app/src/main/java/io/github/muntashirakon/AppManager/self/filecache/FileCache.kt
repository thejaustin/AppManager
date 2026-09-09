// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.filecache

import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class FileCache private constructor(private val mSessionOnly: Boolean) : Closeable {
    private val mCacheDir: File = File(FileUtils.getCachePath(), "files").also {
        if (!it.exists() && !it.mkdirs()) {
            throw IllegalStateException("Could not create cache. Is this OS broken?")
        }
    }
    private val mFileCacheMap: MutableMap<Path, File> = HashMap()
    private val mFileCache: MutableSet<File> = HashSet()
    private val mDirectoryCache: MutableSet<File> = HashSet()

    private var mClosed = false

    constructor() : this(true)

    override fun close() {
        mClosed = true
        if (mSessionOnly) deleteAll()
    }

    protected fun finalize() {
        if (!mClosed) close()
    }

    @Throws(IOException::class)
    fun getCachedFile(source: Path): File {
        if (!source.exists()) {
            throw FileNotFoundException("Path $source does not exist.")
        }
        var tempFile = mFileCacheMap[source]
        if (tempFile == null || !tempFile.exists()) {
            val extension = source.extension
            tempFile = File.createTempFile(source.name + "_", "." + (extension ?: "tmp"), mCacheDir)
            mFileCacheMap[source] = tempFile
        } else if (source.lastModified() > 0 && source.lastModified() < tempFile.lastModified()) {
            return tempFile
        }
        IoUtils.copy(source, Paths.get(tempFile))
        return tempFile
    }

    @Throws(IOException::class)
    fun getCachedFile(inputStream: InputStream, extension: String?): File {
        val tempFile = File.createTempFile("file_", "." + (extension ?: "tmp"), mCacheDir)
        mFileCache.add(tempFile)
        FileOutputStream(tempFile).use { os -> IoUtils.copy(inputStream, os) }
        return tempFile
    }

    @Throws(IOException::class)
    fun getCachedFile(bytes: ByteArray, extension: String?): File {
        val tempFile = File.createTempFile("file_", "." + (extension ?: "tmp"), mCacheDir)
        mFileCache.add(tempFile)
        FileOutputStream(tempFile).use { os -> os.write(bytes) }
        return tempFile
    }

    @Throws(IOException::class)
    fun createCachedFile(extension: String?): File {
        val tempFile = File.createTempFile("file_", "." + (extension ?: "tmp"), mCacheDir)
        mFileCache.add(tempFile)
        return tempFile
    }

    fun createCachedDir(prefix: String?): File {
        var sanitizedPrefix = if (prefix != null) Paths.sanitize(prefix, true) else null
        if (sanitizedPrefix == null) sanitizedPrefix = "folder"
        var dirName = sanitizedPrefix
        var i = 1
        var newDir = File(mCacheDir, dirName)
        while (newDir.exists()) {
            dirName = "${sanitizedPrefix}_$i"
            newDir = File(mCacheDir, dirName)
            ++i
        }
        newDir.mkdirs()
        val firstComponent = dirName.indexOf(File.separator).let { idx ->
            if (idx == -1) dirName else dirName.substring(0, idx)
        }
        mDirectoryCache.add(File(mCacheDir, firstComponent))
        return newDir
    }

    fun delete(path: Path?): Boolean {
        val tempFile = mFileCacheMap.remove(path)
        return tempFile != null && tempFile.delete()
    }

    fun delete(tempFile: File?): Boolean {
        if (mFileCache.remove(tempFile)) {
            return tempFile != null && tempFile.delete()
        }
        return false
    }

    fun deleteAll() {
        for (file in mFileCacheMap.values) FileUtils.deleteSilently(file)
        for (file in mFileCache) FileUtils.deleteSilently(file)
        for (file in mDirectoryCache) Paths.get(file).delete()
    }

    companion object {
        private var sInstance: FileCache? = null

        @JvmStatic
        fun getGlobalFileCache(): FileCache = sInstance ?: FileCache(false).also { sInstance = it }
    }
}
