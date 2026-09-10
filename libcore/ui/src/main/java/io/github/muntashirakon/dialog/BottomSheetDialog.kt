// SPDX-License-Identifier: Apache-2.0

// This class is copied from material components to enable setting a custom BottomSheetBehaviour
// See BottomSheetBehaviour class for details.

package io.github.muntashirakon.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.color.MaterialColors.isColorLight
import com.google.android.material.internal.EdgeToEdgeUtils
import com.google.android.material.shape.MaterialShapeDrawable
import io.github.muntashirakon.ui.R

/**
 * Base class for [android.app.Dialog]s styled as a bottom sheet.
 *
 * <p>Edge to edge window flags are automatically applied if the [
 * android.R.attr.navigationBarColor] is transparent or translucent and `enableEdgeToEdge` is
 * true. These can be set in the theme that is passed to the constructor, or will be taken from the
 * theme of the context (ie. your application or activity theme).
 *
 * <p>In edge to edge mode, padding will be added automatically to the top when sliding under the
 * status bar. Padding can be applied automatically to the left, right, or bottom if any of
 * `paddingBottomSystemWindowInsets`, `paddingLeftSystemWindowInsets`, or
 * `paddingRightSystemWindowInsets` are set to true in the style.
 */
// Copyright 2015 The Android Open Source Project
open class BottomSheetDialog : AppCompatDialog {
    private var behavior: BottomSheetBehavior<FrameLayout>? = null

    private var container: FrameLayout? = null
    private var coordinator: CoordinatorLayout? = null
    private var bottomSheet: FrameLayout? = null

    var dismissWithAnimation = false

    var cancelable = true
        private set
    private var canceledOnTouchOutside = true
    private var canceledOnTouchOutsideSet = false
    private var edgeToEdgeCallback: EdgeToEdgeCallback? = null
    private var edgeToEdgeEnabled = false

    constructor(context: Context) : super(context, 0) {
        edgeToEdgeEnabled = context.theme
            .obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.enableEdgeToEdge))
            .getBoolean(0, false)
    }

    constructor(context: Context, @StyleRes theme: Int) : super(context, getThemeResId(context, theme)) {
        // We hide the title bar for any style configuration. Otherwise, there will be a gap
        // above the bottom sheet when it is expanded.
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        edgeToEdgeEnabled = getContext().theme
            .obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.enableEdgeToEdge))
            .getBoolean(0, false)
    }

    protected constructor(context: Context, cancelable: Boolean, cancelListener: OnCancelListener?) :
            super(context, cancelable, cancelListener) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        this.cancelable = cancelable
        edgeToEdgeEnabled = getContext().theme
            .obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.enableEdgeToEdge))
            .getBoolean(0, false)
    }

    override fun setContentView(@LayoutRes layoutResId: Int) {
        super.setContentView(wrapInBottomSheet(layoutResId, null, null))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val window = window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // The status bar should always be transparent because of the window animation.
                window.statusBarColor = 0
                window.addFlags(LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    // It can be transparent for API 23 and above because we will handle switching the status
                    // bar icons to light or dark as appropriate. For API 21 and API 22 we just set the
                    // translucent status bar.
                    window.addFlags(LayoutParams.FLAG_TRANSLUCENT_STATUS)
                }
            }
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun setContentView(view: View) {
        super.setContentView(wrapInBottomSheet(0, view, null))
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        super.setContentView(wrapInBottomSheet(0, view, params))
    }

    override fun setCancelable(cancelable: Boolean) {
        super.setCancelable(cancelable)
        if (this.cancelable != cancelable) {
            this.cancelable = cancelable
            behavior?.setHideable(cancelable)
        }
    }

    override fun onStart() {
        super.onStart()
        if (behavior != null && behavior!!.state == BottomSheetBehavior.STATE_HIDDEN) {
            behavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val window = window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // If the navigation bar is transparent at all the BottomSheet should be edge to edge.
                val drawEdgeToEdge = edgeToEdgeEnabled && Color.alpha(window.navigationBarColor) < 255
                container?.fitsSystemWindows = !drawEdgeToEdge
                coordinator?.fitsSystemWindows = !drawEdgeToEdge
                WindowCompat.setDecorFitsSystemWindows(window, !drawEdgeToEdge)
            }
            edgeToEdgeCallback?.setWindow(window)
        }
    }

    override fun onDetachedFromWindow() {
        edgeToEdgeCallback?.setWindow(null)
    }

    /**
     * This function can be called from a few different use cases, including Swiping the dialog down
     * or calling `dismiss()` from a `BottomSheetDialogFragment`, tapping outside a dialog, etc...
     *
     * <p>The default animation to dismiss this dialog is a fade-out transition through a
     * windowAnimation. Call [setDismissWithAnimation] if you want to utilize the
     * BottomSheet animation instead.
     *
     * <p>If this function is called from a swipe down interaction, or dismissWithAnimation is false,
     * then keep the default behavior.
     *
     * <p>Else, since this is a terminal event which will finish this dialog, we override the attached
     * [BottomSheetBehavior.BottomSheetCallback] to call this function, after [
     * BottomSheetBehavior.STATE_HIDDEN] is set. This will enforce the swipe down animation before
     * canceling this dialog.
     */
    override fun cancel() {
        val behavior = getBehavior()
        if (!dismissWithAnimation || behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            super.cancel()
        } else {
            behavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    override fun setCanceledOnTouchOutside(cancel: Boolean) {
        super.setCanceledOnTouchOutside(cancel)
        if (cancel && !cancelable) {
            cancelable = true
        }
        canceledOnTouchOutside = cancel
        canceledOnTouchOutsideSet = true
    }

    fun getBehavior(): BottomSheetBehavior<FrameLayout> {
        if (behavior == null) {
            // The content hasn't been set, so the behavior doesn't exist yet. Let's create it.
            ensureContainerAndBehavior()
        }
        return behavior!!
    }

    /**
     * Set to perform the swipe down animation when dismissing instead of the window animation for the
     * dialog.
     *
     * @param dismissWithAnimation True if swipe down animation should be used when dismissing.
     */
    fun setDismissWithAnimation(dismissWithAnimation: Boolean) {
        this.dismissWithAnimation = dismissWithAnimation
    }

    /**
     * Returns if dismissing will perform the swipe down animation on the bottom sheet, rather than
     * the window animation for the dialog.
     */
    fun getDismissWithAnimation(): Boolean = dismissWithAnimation

    /** Returns if edge to edge behavior is enabled for this dialog. */
    fun getEdgeToEdgeEnabled(): Boolean = edgeToEdgeEnabled

    /** Creates the container layout which must exist to find the behavior */
    private fun ensureContainerAndBehavior(): FrameLayout {
        if (container == null) {
            container = View.inflate(context, R.layout.dialog_bottom_sheet, null) as FrameLayout
            coordinator = container!!.findViewById(R.id.coordinator)
            bottomSheet = container!!.findViewById(R.id.design_bottom_sheet)
            behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior!!.addBottomSheetCallback(bottomSheetCallback)
            behavior!!.setHideable(cancelable)
        }
        return container!!
    }

    private fun wrapInBottomSheet(
        layoutResId: Int,
        view: View?,
        params: ViewGroup.LayoutParams?,
    ): View {
        ensureContainerAndBehavior()
        val coordinator = container!!.findViewById<CoordinatorLayout>(R.id.coordinator)
        var wrappedView = view
        if (layoutResId != 0 && wrappedView == null) {
            wrappedView = layoutInflater.inflate(layoutResId, coordinator, false)
        }

        if (edgeToEdgeEnabled) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomSheet!!) { _, insets ->
                if (edgeToEdgeCallback != null) {
                    behavior!!.removeBottomSheetCallback(edgeToEdgeCallback!!)
                }
                edgeToEdgeCallback = EdgeToEdgeCallback(bottomSheet!!, insets)
                edgeToEdgeCallback!!.setWindow(window)
                behavior!!.addBottomSheetCallback(edgeToEdgeCallback!!)
                insets
            }
        }

        bottomSheet!!.removeAllViews()
        if (params == null) {
            bottomSheet!!.addView(wrappedView)
        } else {
            bottomSheet!!.addView(wrappedView, params)
        }
        // We treat the CoordinatorLayout as outside the dialog though it is technically inside
        coordinator.findViewById<View>(R.id.touch_outside).setOnClickListener {
            if (cancelable && isShowing && shouldWindowCloseOnTouchOutside()) {
                cancel()
            }
        }
        // Handle accessibility events
        ViewCompat.setAccessibilityDelegate(
            bottomSheet!!,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    if (cancelable) {
                        info.addAction(AccessibilityNodeInfoCompat.ACTION_DISMISS)
                        info.setDismissable(true)
                    } else {
                        info.setDismissable(false)
                    }
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    if (action == AccessibilityNodeInfoCompat.ACTION_DISMISS && cancelable) {
                        cancel()
                        return true
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
        )
        bottomSheet!!.setOnTouchListener { _, _ ->
            // Consume the event and prevent it from falling through
            true
        }
        return container!!
    }

    internal fun shouldWindowCloseOnTouchOutside(): Boolean {
        if (!canceledOnTouchOutsideSet) {
            val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowCloseOnTouchOutside))
            canceledOnTouchOutside = a.getBoolean(0, true)
            a.recycle()
            canceledOnTouchOutsideSet = true
        }
        return canceledOnTouchOutside
    }

    internal fun removeDefaultCallback() {
        behavior!!.removeBottomSheetCallback(bottomSheetCallback)
    }

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                cancel()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    private class EdgeToEdgeCallback(
        bottomSheet: View,
        private val insetsCompat: WindowInsetsCompat,
    ) : BottomSheetBehavior.BottomSheetCallback() {

        private val lightBottomSheet: Boolean?
        private var window: Window? = null
        private var lightStatusBar = false

        init {
            // Try to find the background color to automatically change the status bar icons so they will
            // still be visible when the bottomsheet slides underneath the status bar.
            val msd = BottomSheetBehavior.from(bottomSheet).getMaterialShapeDrawable()
            val backgroundTint: ColorStateList? = if (msd != null) {
                msd.fillColor
            } else {
                ViewCompat.getBackgroundTintList(bottomSheet)
            }

            lightBottomSheet = when {
                backgroundTint != null -> {
                    // First check for a tint
                    isColorLight(backgroundTint.defaultColor)
                }
                bottomSheet.background is ColorDrawable -> {
                    // Then check for the background color
                    isColorLight((bottomSheet.background as ColorDrawable).color)
                }
                else -> {
                    // Otherwise don't change the status bar color
                    null
                }
            }
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            setPaddingForPosition(bottomSheet)
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            setPaddingForPosition(bottomSheet)
        }

        override fun onLayout(bottomSheet: View) {
            setPaddingForPosition(bottomSheet)
        }

        fun setWindow(window: Window?) {
            if (this.window == window) return
            this.window = window
            if (window != null) {
                val insetsController: WindowInsetsControllerCompat =
                    WindowCompat.getInsetsController(window, window.decorView)
                lightStatusBar = insetsController.isAppearanceLightStatusBars
            }
        }

        @SuppressLint("RestrictedApi")
        private fun setPaddingForPosition(bottomSheet: View) {
            if (bottomSheet.top < insetsCompat.systemWindowInsetTop) {
                // If the bottomsheet is light, we should set light status bar so the icons are visible
                // since the bottomsheet is now under the status bar.
                if (window != null) {
                    EdgeToEdgeUtils.setLightStatusBar(
                        window!!,
                        lightBottomSheet ?: lightStatusBar
                    )
                }
                // Smooth transition into status bar when drawing edge to edge.
                bottomSheet.setPadding(
                    bottomSheet.paddingLeft,
                    insetsCompat.systemWindowInsetTop - bottomSheet.top,
                    bottomSheet.paddingRight,
                    bottomSheet.paddingBottom
                )
            } else if (bottomSheet.top != 0) {
                // Reset the status bar icons to the original color because the bottomsheet is not under the
                // status bar.
                if (window != null) {
                    EdgeToEdgeUtils.setLightStatusBar(window!!, lightStatusBar)
                }
                bottomSheet.setPadding(
                    bottomSheet.paddingLeft,
                    0,
                    bottomSheet.paddingRight,
                    bottomSheet.paddingBottom
                )
            }
        }
    }

    companion object {
        private fun getThemeResId(context: Context, themeId: Int): Int {
            var resolvedThemeId = themeId
            if (resolvedThemeId == 0) {
                // If the provided theme is 0, then retrieve the dialogTheme from our theme
                val outValue = TypedValue()
                if (context.theme.resolveAttribute(
                        com.google.android.material.R.attr.bottomSheetDialogTheme,
                        outValue,
                        true
                    )
                ) {
                    resolvedThemeId = outValue.resourceId
                } else {
                    // bottomSheetDialogTheme is not provided; we default to our light theme
                    resolvedThemeId = com.google.android.material.R.style.Theme_Design_Light_BottomSheetDialog
                }
            }
            return resolvedThemeId
        }

        /**
         * @deprecated use [EdgeToEdgeUtils.setLightStatusBar] instead
         */
        @Deprecated("Use EdgeToEdgeUtils.setLightStatusBar instead")
        @JvmStatic
        fun setLightStatusBar(view: View, isLight: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = view.systemUiVisibility
                flags = if (isLight) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                view.systemUiVisibility = flags
            }
        }
    }
}
