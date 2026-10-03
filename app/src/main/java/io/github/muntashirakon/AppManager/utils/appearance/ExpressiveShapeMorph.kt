// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.github.muntashirakon.AppManager.R

/**
 * Material 3 Expressive (M3E) Shape Morphing System (2025/2026)
 *
 * Provides fluid, spring-physics-driven shape transitions across Material components:
 * - Fluid corner radius morphing (Pill <-> Rounded Rectangle <-> Squircle)
 * - Asymmetric corner morphing (diagonal/individual corner transitions)
 * - Interactive press and state-change shape morphing with haptics
 * - Standardized M3 Expressive shape tokens
 */
object ExpressiveShapeMorph {

    /**
     * Standard M3 Expressive Corner Size Tokens (in dp)
     */
    object Tokens {
        const val CORNER_NONE = 0f
        const val CORNER_EXTRA_SMALL = 4f
        const val CORNER_SMALL = 8f
        const val CORNER_MEDIUM = 12f
        const val CORNER_LARGE = 16f
        const val CORNER_EXTRA_LARGE = 28f
        const val CORNER_FULL = 500f // Effective pill / circular corner
    }

    /**
     * Converts dp to pixels
     */
    fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    /**
     * Morphs a [MaterialCardView]'s corner radius uniformly using M3 Expressive spring motion.
     */
    fun morphCornerRadius(
        card: MaterialCardView,
        targetRadiusDp: Float,
        duration: Long = ExpressiveMotion.Duration.MODERATE,
        onComplete: (() -> Unit)? = null
    ): ValueAnimator {
        val targetRadiusPx = dpToPx(card.context, targetRadiusDp)
        val startRadiusPx = card.radius

        val animator = ValueAnimator.ofFloat(startRadiusPx, targetRadiusPx).apply {
            this.duration = duration
            this.interpolator = ExpressiveMotion.Easing.SPRING
            addUpdateListener { va ->
                val current = va.animatedValue as Float
                card.radius = current
            }
        }
        if (onComplete != null) {
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
        }
        animator.start()
        return animator
    }

    /**
     * Morphs a [MaterialCardView] to an asymmetric shape with individual corner radii.
     */
    fun morphCorners(
        card: MaterialCardView,
        topLeftDp: Float,
        topRightDp: Float,
        bottomRightDp: Float,
        bottomLeftDp: Float,
        duration: Long = ExpressiveMotion.Duration.MODERATE
    ): ValueAnimator {
        val ctx = card.context
        val startModel = card.shapeAppearanceModel
        val startTL = startModel.topLeftCornerSize.getCornerSize(android.graphics.RectF(0f, 0f, card.width.toFloat(), card.height.toFloat()))
        val startTR = startModel.topRightCornerSize.getCornerSize(android.graphics.RectF(0f, 0f, card.width.toFloat(), card.height.toFloat()))
        val startBR = startModel.bottomRightCornerSize.getCornerSize(android.graphics.RectF(0f, 0f, card.width.toFloat(), card.height.toFloat()))
        val startBL = startModel.bottomLeftCornerSize.getCornerSize(android.graphics.RectF(0f, 0f, card.width.toFloat(), card.height.toFloat()))

        val targetTL = dpToPx(ctx, topLeftDp)
        val targetTR = dpToPx(ctx, topRightDp)
        val targetBR = dpToPx(ctx, bottomRightDp)
        val targetBL = dpToPx(ctx, bottomLeftDp)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = ExpressiveMotion.Easing.EMPHASIZED
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val currentTL = startTL + (targetTL - startTL) * fraction
                val currentTR = startTR + (targetTR - startTR) * fraction
                val currentBR = startBR + (targetBR - startBR) * fraction
                val currentBL = startBL + (targetBL - startBL) * fraction

                card.shapeAppearanceModel = startModel.toBuilder()
                    .setTopLeftCorner(CornerFamily.ROUNDED, currentTL)
                    .setTopRightCorner(CornerFamily.ROUNDED, currentTR)
                    .setBottomRightCorner(CornerFamily.ROUNDED, currentBR)
                    .setBottomLeftCorner(CornerFamily.ROUNDED, currentBL)
                    .build()
            }
        }
        animator.start()
        return animator
    }

    /**
     * Morphs a [Chip]'s corner radius uniformly using M3 Expressive spring motion.
     */
    fun morphCornerRadius(
        chip: Chip,
        targetRadiusDp: Float,
        duration: Long = ExpressiveMotion.Duration.STANDARD,
        onComplete: (() -> Unit)? = null
    ): ValueAnimator {
        val targetRadiusPx = dpToPx(chip.context, targetRadiusDp)
        val startRadiusPx = chip.shapeAppearanceModel.topLeftCornerSize.getCornerSize(
            android.graphics.RectF(0f, 0f, chip.width.toFloat(), chip.height.toFloat())
        )

        val animator = ValueAnimator.ofFloat(startRadiusPx, targetRadiusPx).apply {
            this.duration = duration
            this.interpolator = ExpressiveMotion.Easing.SPRING
            addUpdateListener { va ->
                val current = va.animatedValue as Float
                chip.shapeAppearanceModel = chip.shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(current)
                    .build()
            }
        }
        if (onComplete != null) {
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
        }
        animator.start()
        return animator
    }

    /**
     * Morphs a [MaterialButton]'s corner radius uniformly using M3 Expressive spring motion.
     */
    fun morphCornerRadius(
        button: MaterialButton,
        targetRadiusDp: Float,
        duration: Long = ExpressiveMotion.Duration.STANDARD
    ): ValueAnimator {
        val targetRadiusPx = dpToPx(button.context, targetRadiusDp).toInt()
        val startRadiusPx = button.cornerRadius

        val animator = ValueAnimator.ofInt(startRadiusPx, targetRadiusPx).apply {
            this.duration = duration
            this.interpolator = ExpressiveMotion.Easing.SPRING
            addUpdateListener { va ->
                button.cornerRadius = va.animatedValue as Int
            }
        }
        animator.start()
        return animator
    }

    /**
     * Generic morph between two [ShapeAppearanceModel]s on a view having a [MaterialShapeDrawable] background.
     */
    fun morphShape(
        view: View,
        targetShape: ShapeAppearanceModel,
        duration: Long = ExpressiveMotion.Duration.MODERATE
    ): ValueAnimator? {
        val background = view.background as? MaterialShapeDrawable ?: return null
        val startShape = background.shapeAppearanceModel
        val rect = android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())

        val startTL = startShape.topLeftCornerSize.getCornerSize(rect)
        val startTR = startShape.topRightCornerSize.getCornerSize(rect)
        val startBR = startShape.bottomRightCornerSize.getCornerSize(rect)
        val startBL = startShape.bottomLeftCornerSize.getCornerSize(rect)

        val targetTL = targetShape.topLeftCornerSize.getCornerSize(rect)
        val targetTR = targetShape.topRightCornerSize.getCornerSize(rect)
        val targetBR = targetShape.bottomRightCornerSize.getCornerSize(rect)
        val targetBL = targetShape.bottomLeftCornerSize.getCornerSize(rect)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = ExpressiveMotion.Easing.EMPHASIZED
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                background.shapeAppearanceModel = startShape.toBuilder()
                    .setTopLeftCornerSize(startTL + (targetTL - startTL) * fraction)
                    .setTopRightCornerSize(startTR + (targetTR - startTR) * fraction)
                    .setBottomRightCornerSize(startBR + (targetBR - startBR) * fraction)
                    .setBottomLeftCornerSize(startBL + (targetBL - startBL) * fraction)
                    .build()
            }
        }
        animator.start()
        return animator
    }

    /**
     * Binds interactive press-and-release shape morphing to a [MaterialCardView].
     * Morphs corners and triggers tactile feedback on touch.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun bindInteractiveCardMorph(
        card: MaterialCardView,
        defaultRadiusDp: Float = Tokens.CORNER_LARGE,
        pressedRadiusDp: Float = Tokens.CORNER_EXTRA_LARGE
    ) {
        card.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    morphCornerRadius(card, pressedRadiusDp, ExpressiveMotion.Duration.QUICK)
                    ExpressiveMotion.animateCardPress(card, true)
                    ExpressiveHaptics.perform(card, ExpressiveHaptics.HAPTIC_LIGHT)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    morphCornerRadius(card, defaultRadiusDp, ExpressiveMotion.Duration.MODERATE)
                    ExpressiveMotion.animateCardPress(card, false)
                }
            }
            false
        }
    }

    /**
     * Binds automatic shape morphing to a [Chip] based on its checked state.
     * When checked: morphs to pill (24dp) with spring bounce & haptic.
     * When unchecked: morphs to standard rounded rectangle (8dp).
     */
    fun bindChipSelectionMorph(
        chip: Chip,
        uncheckedRadiusDp: Float = Tokens.CORNER_SMALL,
        checkedRadiusDp: Float = 20f
    ) {
        // Set initial shape based on checked state
        val initialRadius = if (chip.isChecked) checkedRadiusDp else uncheckedRadiusDp
        chip.shapeAppearanceModel = chip.shapeAppearanceModel.toBuilder()
            .setAllCornerSizes(dpToPx(chip.context, initialRadius))
            .build()

        chip.addOnCheckedChangeListener { _, isChecked ->
            val targetRadius = if (isChecked) checkedRadiusDp else uncheckedRadiusDp
            morphCornerRadius(chip, targetRadius, ExpressiveMotion.Duration.STANDARD)
            ExpressiveMotion.addSpringBounce(chip)
            ExpressiveHaptics.perform(chip, ExpressiveHaptics.HAPTIC_LIGHT)
        }
    }
}
