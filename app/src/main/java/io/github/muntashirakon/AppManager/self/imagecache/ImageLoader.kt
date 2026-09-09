// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.imagecache

import android.content.pm.PackageItemInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import java.io.Closeable
import java.io.IOException
import java.lang.ref.WeakReference

class ImageLoader private constructor() : Closeable {

    fun interface ImageFetcherInterface {
        fun fetchImage(tag: String): ImageFetcherResult
    }

    interface DefaultImage {
        fun getTag(): String? = null
        fun getImage(): Bitmap
    }

    class DefaultImageDrawableRes(
        private val mTag: String?,
        @DrawableRes private val mDrawableRes: Int,
        @Px private val mPadding: Int = 0,
    ) : DefaultImage {
        constructor(tag: String?, drawableRes: Int) : this(tag, drawableRes, 0)

        override fun getTag(): String? = mTag

        override fun getImage(): Bitmap = UIUtils.getBitmapFromDrawable(
            requireNotNull(ContextCompat.getDrawable(ContextUtils.getContext(), mDrawableRes)),
            mPadding
        )
    }

    class DefaultImageDrawable(
        private val mTag: String?,
        private val mDrawable: Drawable,
    ) : DefaultImage {
        override fun getTag(): String? = mTag
        override fun getImage(): Bitmap = UIUtils.getBitmapFromDrawable(mDrawable)
    }

    class DefaultImageString(
        private val mTag: String?,
        private val mText: String,
    ) : DefaultImage {
        override fun getTag(): String? = mTag
        override fun getImage(): Bitmap = UIUtils.generateBitmapFromText(mText, null)
    }

    class ImageFetcherResult @JvmOverloads constructor(
        val tag: String,
        val bitmap: Bitmap?,
        val cacheInMemory: Boolean = true,
        val persistCache: Boolean = true,
        val defaultImage: DefaultImage,
    ) {
        constructor(tag: String, bitmap: Bitmap?, defaultImage: DefaultImage) :
            this(tag, bitmap, true, true, defaultImage)
    }

    class ImageLoaderQueueItem(
        val tag: String,
        imageFetcherInterface: ImageFetcherInterface,
        imageView: ImageView,
    ) {
        val imageView: WeakReference<ImageView> = WeakReference(imageView)
        internal val mImageFetcherInterface: ImageFetcherInterface = imageFetcherInterface
    }

    // OPTIMIZATION: Increased cache size from 300 to 2000 for users with many apps
    // At ~50KB per icon, 2000 icons = ~100MB memory (acceptable on modern devices)
    private val mMemoryCache = LruCache<String, Bitmap>(2000)
    private val mImageFileCache = ImageFileCache()
    private var mIsClosed = false

    fun getCachedImage(tag: String): Bitmap? {
        return mMemoryCache[tag] ?: mImageFileCache.getImage(tag)
    }

    fun displayImage(tag: String, imageView: ImageView, imageFetcherInterface: ImageFetcherInterface) {
        val image = mMemoryCache[tag]
        if (image != null) {
            imageView.setImageBitmap(image)
        } else {
            queueImage(tag, imageView, imageFetcherInterface)
        }
    }

    fun displayImage(tag: String, info: PackageItemInfo?, imageView: ImageView) {
        val image = mMemoryCache[tag]
        if (image != null) {
            imageView.setImageBitmap(image)
        } else {
            queueImage(tag, info, imageView)
        }
    }

    override fun close() {
        mIsClosed = true
        mMemoryCache.evictAll()
        mImageFileCache.clear()
    }

    protected fun finalize() {
        if (!mIsClosed) close()
    }

    private fun queueImage(tag: String, info: PackageItemInfo?, imageView: ImageView) {
        queueImage(tag, imageView, PackageInfoImageFetcher(info))
    }

    private fun queueImage(tag: String, imageView: ImageView, imageFetcherInterface: ImageFetcherInterface) {
        val queueItem = ImageLoaderQueueItem(tag, imageFetcherInterface, imageView)
        ThreadUtils.postOnBackgroundThread(LoadQueueItem(queueItem))
    }

    private fun imageViewReusedOrClosed(item: ImageLoaderQueueItem): Boolean {
        return mIsClosed || item.imageView.get() == null
    }

    private inner class PackageInfoImageFetcher(private val mInfo: PackageItemInfo?) : ImageFetcherInterface {
        override fun fetchImage(tag: String): ImageFetcherResult {
            val pm = ContextUtils.getContext().packageManager
            val drawable = mInfo?.loadIcon(pm)
            return ImageFetcherResult(
                tag,
                if (drawable != null) UIUtils.getBitmapFromDrawable(drawable) else null,
                mInfo != null && tag == mInfo.packageName,
                true,
                DefaultImageDrawable("android_default_icon", pm.defaultActivityIcon)
            )
        }
    }

    private inner class LoadQueueItem(private val mQueueItem: ImageLoaderQueueItem) : Runnable {
        override fun run() {
            if (imageViewReusedOrClosed(mQueueItem)) return
            var image = mImageFileCache.getImage(mQueueItem.tag)
            if (image != null) {
                mMemoryCache.put(mQueueItem.tag, image)
            } else {
                val result = mQueueItem.mImageFetcherInterface.fetchImage(mQueueItem.tag)
                if (result.bitmap == null) {
                    val defaultImage = result.defaultImage
                    val defaultTag = defaultImage.getTag()
                    if (defaultTag == null) {
                        image = defaultImage.getImage()
                    } else {
                        image = mMemoryCache[defaultTag] ?: mImageFileCache.getImage(defaultTag)
                        if (image == null) {
                            image = defaultImage.getImage()
                            mMemoryCache.put(defaultTag, image)
                            try {
                                mImageFileCache.putImage(defaultTag, image)
                            } catch (_: IOException) {
                            }
                        }
                    }
                } else {
                    image = result.bitmap
                    if (result.cacheInMemory) mMemoryCache.put(mQueueItem.tag, image)
                    if (result.persistCache) {
                        try {
                            mImageFileCache.putImage(result.tag, image)
                        } catch (_: IOException) {
                        }
                    }
                }
            }
            if (imageViewReusedOrClosed(mQueueItem)) return
            ThreadUtils.postOnMainThread(LoadImageInImageView(image!!, mQueueItem))
        }
    }

    private inner class LoadImageInImageView(
        private val mImage: Bitmap,
        private val mQueueItem: ImageLoaderQueueItem,
    ) : Runnable {
        override fun run() {
            if (imageViewReusedOrClosed(mQueueItem)) return
            val iv = mQueueItem.imageView.get() ?: return
            val tag = iv.tag
            if (tag == null || tag == mQueueItem.tag) {
                iv.setImageBitmap(mImage)
            }
        }
    }

    companion object {
        private val sInstance = ImageLoader()

        @JvmStatic
        fun getInstance(): ImageLoader = sInstance

        @JvmStatic
        fun displayImage(info: PackageItemInfo?, imageView: ImageView?) {
            val ivRef = WeakReference(imageView)
            ThreadUtils.postOnBackgroundThread {
                val iv = ivRef.get()
                if (info == null || iv == null) return@postOnBackgroundThread
                val drawable = info.loadIcon(iv.context.packageManager)
                ThreadUtils.postOnMainThread {
                    ivRef.get()?.setImageDrawable(drawable)
                }
            }
        }
    }
}
