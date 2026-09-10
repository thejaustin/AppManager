// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc.gles

import android.opengl.GLES20
import android.util.Log

// Copyright 2014 Google Inc.
object GlUtil {
    const val TAG = "GlUtil"

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = "$op: glError 0x${Integer.toHexString(error)}"
            Log.e(TAG, msg)
            throw RuntimeException(msg)
        }
    }
}
