// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc.gles

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

// Copyright 2014 Google Inc.
class EglCore @JvmOverloads constructor(sharedContext: EGLContext? = null, flags: Int = 0) {
    private var mEGLDisplay = EGL14.EGL_NO_DISPLAY
    private var mEGLContext = EGL14.EGL_NO_CONTEXT
    private var mEGLConfig: EGLConfig? = null
    private var mGlVersion = -1

    init {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) throw RuntimeException("EGL already set up")
        val ctx = sharedContext ?: EGL14.EGL_NO_CONTEXT
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("unable to get EGL14 display")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }

        // Try GLES3 first
        val config3 = getConfig(flags, 3)
        if (config3 != null) {
            val attrib3List = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(mEGLDisplay, config3, ctx, attrib3List, 0)
            if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
                mEGLConfig = config3
                mEGLContext = context
                mGlVersion = 3
            }
        }

        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            val config2 = getConfig(flags, 2) ?: throw RuntimeException("Unable to find a suitable EGLConfig")
            val attrib2List = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(mEGLDisplay, config2, ctx, attrib2List, 0)
            checkEglError("eglCreateContext")
            mEGLConfig = config2
            mEGLContext = context
            mGlVersion = 2
        }

        val values = IntArray(1)
        EGL14.eglQueryContext(mEGLDisplay, mEGLContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0)
        Log.d(TAG, "EGLContext created, client version ${values[0]}")
    }

    private fun getConfig(flags: Int, version: Int): EGLConfig? {
        var renderableType = EGL14.EGL_OPENGL_ES2_BIT
        if (version >= 3) renderableType = renderableType or EGLExt.EGL_OPENGL_ES3_BIT_KHR
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            EGL14.EGL_NONE, 0,  // placeholder for recordable
            EGL14.EGL_NONE
        )
        if ((flags and FLAG_RECORDABLE) != 0) {
            attribList[attribList.size - 3] = EGL_RECORDABLE_ANDROID
            attribList[attribList.size - 2] = 1
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
            Log.w(TAG, "unable to find RGB8888 / $version EGLConfig")
            return null
        }
        return configs[0]
    }

    fun release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(mEGLDisplay)
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY
        mEGLContext = EGL14.EGL_NO_CONTEXT
        mEGLConfig = null
    }

    protected fun finalize() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            Log.w(TAG, "WARNING: EglCore was not explicitly released -- state may be leaked")
            release()
        }
    }

    fun releaseSurface(eglSurface: EGLSurface) = EGL14.eglDestroySurface(mEGLDisplay, eglSurface)

    fun createWindowSurface(surface: Any): EGLSurface {
        if (surface !is Surface && surface !is SurfaceTexture) throw RuntimeException("invalid surface: $surface")
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface, surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
        return eglSurface ?: throw RuntimeException("surface was null")
    }

    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, surfaceAttribs, 0)
        checkEglError("eglCreatePbufferSurface")
        return eglSurface ?: throw RuntimeException("surface was null")
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) Log.d(TAG, "NOTE: makeCurrent w/o display")
        if (!EGL14.eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun makeCurrent(drawSurface: EGLSurface, readSurface: EGLSurface) {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) Log.d(TAG, "NOTE: makeCurrent w/o display")
        if (!EGL14.eglMakeCurrent(mEGLDisplay, drawSurface, readSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent(draw,read) failed")
        }
    }

    fun makeNothingCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun swapBuffers(eglSurface: EGLSurface): Boolean = EGL14.eglSwapBuffers(mEGLDisplay, eglSurface)

    fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) =
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, eglSurface, nsecs)

    fun isCurrent(eglSurface: EGLSurface): Boolean =
        mEGLContext == EGL14.eglGetCurrentContext() && eglSurface == EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)

    fun querySurface(eglSurface: EGLSurface, what: Int): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(mEGLDisplay, eglSurface, what, value, 0)
        return value[0]
    }

    fun queryString(what: Int): String = EGL14.eglQueryString(mEGLDisplay, what)

    fun getGlVersion(): Int = mGlVersion

    private fun checkEglError(msg: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException("$msg: EGL error: 0x${Integer.toHexString(error)}")
        }
    }

    companion object {
        private const val TAG = "EglCore"
        const val FLAG_RECORDABLE = 0x01
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        @JvmStatic
        fun logCurrent(msg: String) {
            val display = EGL14.eglGetCurrentDisplay()
            val context = EGL14.eglGetCurrentContext()
            val surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
            Log.i(TAG, "Current EGL ($msg): display=$display, context=$context, surface=$surface")
        }
    }
}
