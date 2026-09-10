// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.UiUtils
import java.util.Objects

/**
 * A [BottomSheetDialogFragment] with a [BottomSheetDragHandleView] on top
 */
// Copyright 2022 Muntashir Al-Islam
// Copyright 2022 Absinthe
abstract class CapsuleBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private lateinit var mBottomSheetContainer: LinearLayoutCompat
    private lateinit var mDragHandle: BottomSheetDragHandleView
    private lateinit var mHeaderContainer: LinearLayoutCompat
    private lateinit var mMainContainer: FrameLayout
    private lateinit var mBodyContainer: LinearLayoutCompat
    private lateinit var mLoadingLayout: RelativeLayout
    private var mHeader: View? = null
    private var mBody: View? = null
    private var mIsCapsuleActivated = false
    private var mIsLoadingFinished = false
    lateinit var behavior: BottomSheetBehavior<FrameLayout>
        private set

    private val mBottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_DRAGGING -> {
                    if (!mIsCapsuleActivated) {
                        mIsCapsuleActivated = true
                        onCapsuleActivated(true)
                    }
                }
                BottomSheetBehavior.STATE_HALF_EXPANDED -> throw UnsupportedOperationException()
                BottomSheetBehavior.STATE_COLLAPSED -> throw UnsupportedOperationException()
                BottomSheetBehavior.STATE_EXPANDED -> {
                    if (mIsCapsuleActivated) {
                        mIsCapsuleActivated = false
                        onCapsuleActivated(false)
                    }
                }
                BottomSheetBehavior.STATE_HIDDEN,
                BottomSheetBehavior.STATE_SETTLING -> Unit
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    val body: View?
        get() = mBody

    @MainThread
    abstract fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View

    @MainThread
    open fun onBodyInitialized(bodyView: View, savedInstanceState: Bundle?) {}

    @MainThread
    open fun displayLoaderByDefault(): Boolean = false

    @MainThread
    fun startLoading() {
        if (!mIsLoadingFinished) return
        mIsLoadingFinished = false
        mLoadingLayout.visibility = View.VISIBLE
        mBodyContainer.visibility = View.GONE
    }

    @MainThread
    fun finishLoading() {
        if (mIsLoadingFinished) return
        mIsLoadingFinished = true
        mBodyContainer.visibility = View.VISIBLE
        if (mBodyContainer.childCount != 1 && body != null) {
            mBodyContainer.addView(body, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        } // else Body has already been set, no need to set it again
        mLoadingLayout.visibility = View.GONE
        behavior.setStateInternal(BottomSheetBehavior.STATE_EXPANDED)
    }

    fun getHeader(): View? = mHeader

    fun setHeader(header: View?) {
        mHeader = header
        mHeaderContainer.removeAllViews()
        if (header != null) {
            // Remove top padding
            // TODO: 12/8/22 Fix this workaround by unsetting the top padding in the DialogTitleBuilder
            if (header.isPaddingRelative) {
                header.setPaddingRelative(header.paddingStart, 0, header.paddingEnd, header.paddingBottom)
            } else {
                header.setPadding(header.paddingStart, 0, header.paddingEnd, header.paddingBottom)
            }
            mHeaderContainer.addView(header, LinearLayoutCompat.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    @CallSuper
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialogInternal(requireContext(), theme)
        behavior = dialog.behavior
        behavior.fitToContents = true
        behavior.skipHalfExpanded = true
        behavior.setSkipCollapsed(true)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mBottomSheetContainer = inflater.inflate(R.layout.dialog_bottom_sheet_capsule, container, false) as LinearLayoutCompat
        mDragHandle = mBottomSheetContainer.findViewById(R.id.capsule)
        mDragHandle.setImageDrawable(TransitionDrawable(arrayOf<Drawable>(
            ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_drag_handle)!!,
            ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_drag_handle_activated)!!
        )))
        mHeaderContainer = mBottomSheetContainer.findViewById(R.id.header)
        mBodyContainer = mBottomSheetContainer.findViewById(R.id.body)
        mLoadingLayout = mBottomSheetContainer.findViewById(R.id.loader)
        mMainContainer = mBodyContainer.parent as FrameLayout
        mBody = initRootView(inflater, mBottomSheetContainer, savedInstanceState)

        if (!displayLoaderByDefault()) {
            finishLoading()
        }
        return mBottomSheetContainer
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        onBodyInitialized(mBody!!, savedInstanceState)
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        behavior.addBottomSheetCallback(mBottomSheetCallback)
    }

    @CallSuper
    override fun onStop() {
        super.onStop()
        behavior.removeBottomSheetCallback(mBottomSheetCallback)
    }

    @CallSuper
    override fun onDestroyView() {
        mHeader = null
        mBody = null
        super.onDestroyView()
    }

    open fun onCapsuleActivated(activated: Boolean) {
        val drawable = mDragHandle.drawable as TransitionDrawable
        if (activated) {
            drawable.startTransition(150)
        } else {
            drawable.reverseTransition(150)
        }
    }

    private class BottomSheetDialogInternal(context: Context, theme: Int) : BottomSheetDialog(context, theme) {
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            val window: Window? = window
            if (window != null) {
                window.attributes.windowAnimations = R.style.AppTheme_BottomSheetAnimation
                WindowCompat.setDecorFitsSystemWindows(window, false)
                UiUtils.setSystemBarStyle(window, true)
                WindowInsetsControllerCompat(window, window.decorView)
                    .isAppearanceLightNavigationBars = !UiUtils.isDarkMode()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes.blurBehindRadius = 64
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }
            }

            Objects.requireNonNull(findViewById<View>(R.id.container)).setFitsSystemWindows(false)
            Objects.requireNonNull(findViewById<View>(R.id.coordinator)).setFitsSystemWindows(false)
        }
    }

    companion object {
        @JvmField
        val TAG: String = CapsuleBottomSheetDialogFragment::class.java.simpleName
    }
}
