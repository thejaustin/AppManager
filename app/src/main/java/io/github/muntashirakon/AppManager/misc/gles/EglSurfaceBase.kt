// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc.gles

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Copyright 2014 Google Inc.
open class EglSurfaceBase(protected var mEglCore: EglCore) {
    private var mEGLSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var mWidth = -1
    private var mHeight = -1

    fun createWindowSurface(surface: Any) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) throw IllegalStateException("surface already created")
        mEGLSurface = mEglCore.createWindowSurface(surface)
    }

    fun createOffscreenSurface(width: Int, height: Int) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) throw IllegalStateException("surface already created")
        mEGLSurface = mEglCore.createOffscreenSurface(width, height)
        mWidth = width
        mHeight = height
    }

    fun getWidth(): Int = if (mWidth < 0) mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH) else mWidth

    fun getHeight(): Int = if (mHeight < 0) mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT) else mHeight

    fun releaseEglSurface() {
        mEglCore.releaseSurface(mEGLSurface)
        mEGLSurface = EGL14.EGL_NO_SURFACE
        mHeight = -1
        mWidth = -1
    }

    fun makeCurrent() = mEglCore.makeCurrent(mEGLSurface)

    fun makeCurrentReadFrom(readSurface: EglSurfaceBase) = mEglCore.makeCurrent(mEGLSurface, readSurface.mEGLSurface)

    fun swapBuffers(): Boolean {
        val result = mEglCore.swapBuffers(mEGLSurface)
        if (!result) Log.d(TAG, "WARNING: swapBuffers() failed")
        return result
    }

    fun setPresentationTime(nsecs: Long) = mEglCore.setPresentationTime(mEGLSurface, nsecs)

    @Throws(IOException::class)
    fun saveFrame(file: File) {
        if (!mEglCore.isCurrent(mEGLSurface)) throw RuntimeException("Expected EGL context/surface is not current")
        val width = getWidth()
        val height = getHeight()
        val buf = ByteBuffer.allocateDirect(width * height * 4).apply { order(ByteOrder.LITTLE_ENDIAN) }
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        GlUtil.checkGlError("glReadPixels")
        buf.rewind()
        BufferedOutputStream(FileOutputStream(file)).use { bos ->
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos)
            bmp.recycle()
        }
        Log.d(TAG, "Saved ${width}x${height} frame as '$file'")
    }

    companion object {
        protected const val TAG = "EglSurfaceBase"
    }
}
