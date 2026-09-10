// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc.gles

// Copyright 2014 Google Inc.
class OffscreenSurface(eglCore: EglCore, width: Int, height: Int) : EglSurfaceBase(eglCore) {
    init {
        createOffscreenSurface(width, height)
    }

    fun release() = releaseEglSurface()
}
