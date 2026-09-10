// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import androidx.appcompat.widget.TintTypedArray
import androidx.core.widget.TextViewCompat
import androidx.transition.TransitionManager
import androidx.transition.Visibility
import com.google.android.material.color.MaterialColors
import com.google.android.material.internal.ThemeEnforcement
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap
import com.google.android.material.transition.MaterialFadeThrough
import io.github.muntashirakon.ui.R

class MaterialAlertView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialAlertViewStyle
) : TextInputLayout(wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr) {

    @IntDef(ALERT_TYPE_INFO, ALERT_TYPE_WARN, ALERT_TYPE_CUSTOM)
    @Retention(AnnotationRetention.SOURCE)
    annotation class AlertType

    @AlertType
    var alertType: Int = ALERT_TYPE_INFO
        set(value) {
            field = value
            applyAlertType()
        }

    private val mTextInputTextView: TextInputTextView

    init {
        val ctx = getContext()
        mTextInputTextView = TextInputTextView(ctx)
        mTextInputTextView.movementMethod = LinkMovementMethod.getInstance()
        addView(mTextInputTextView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        @SuppressLint("RestrictedApi")
        val a: TintTypedArray = ThemeEnforcement.obtainTintedStyledAttributes(
            ctx, attrs, R.styleable.MaterialAlertView, defStyleAttr, DEF_STYLE_RES
        )
        alertType = a.getInt(R.styleable.MaterialAlertView_alertType, ALERT_TYPE_INFO)
        val textAppearance = a.getResourceId(R.styleable.MaterialAlertView_android_textAppearance, 0)
        val textColor = a.getColorStateList(R.styleable.MaterialAlertView_android_textColor)
        val text = a.getText(R.styleable.MaterialAlertView_android_text)
        a.recycle()

        TextViewCompat.setTextAppearance(mTextInputTextView, textAppearance)
        if (textColor != null) {
            mTextInputTextView.setTextColor(textColor)
        }
        mTextInputTextView.text = text
        mTextInputTextView.overScrollMode = OVER_SCROLL_NEVER

        applyAlertType()
    }

    fun setText(text: CharSequence?) {
        mTextInputTextView.text = text
    }

    fun setText(text: CharSequence?, type: TextView.BufferType) {
        mTextInputTextView.setText(text, type)
    }

    fun setText(@StringRes resid: Int) {
        mTextInputTextView.setText(resid)
    }

    fun setText(@StringRes resid: Int, type: TextView.BufferType) {
        mTextInputTextView.setText(resid, type)
    }

    fun setText(text: CharArray, start: Int, len: Int) {
        mTextInputTextView.setText(text, start, len)
    }

    fun setTextIsSelectable(selectable: Boolean) {
        mTextInputTextView.setTextIsSelectable(selectable)
    }

    fun setMovementMethod(movementMethod: MovementMethod) {
        mTextInputTextView.movementMethod = movementMethod
    }

    fun show() {
        val fadeThrough = MaterialFadeThrough()
        fadeThrough.addTarget(this)
        fadeThrough.duration = 500
        fadeThrough.mode = Visibility.MODE_IN
        val parent = parent
        if (parent is ViewGroup) {
            TransitionManager.beginDelayedTransition(parent, fadeThrough)
        } else {
            TransitionManager.beginDelayedTransition(this, fadeThrough)
        }
        visibility = View.VISIBLE
    }

    fun hide() {
        val fadeThrough = MaterialFadeThrough()
        fadeThrough.addTarget(this)
        fadeThrough.setSecondaryAnimatorProvider(null)
        fadeThrough.duration = 1000
        fadeThrough.mode = Visibility.MODE_OUT
        val parent = parent
        if (parent is ViewGroup) {
            TransitionManager.beginDelayedTransition(parent, fadeThrough)
        } else {
            TransitionManager.beginDelayedTransition(this, fadeThrough)
        }
        visibility = View.GONE
    }

    private fun applyAlertType() {
        when (alertType) {
            ALERT_TYPE_CUSTOM -> Unit
            ALERT_TYPE_WARN -> {
                setStartIconDrawable(R.drawable.ic_caution)
                val foreground = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnErrorContainer))
                val background = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer))
                setBoxBackgroundColorStateList(background)
                setStartIconTintList(foreground)
                mTextInputTextView.setTextColor(foreground)
            }
            else -> {
                setStartIconDrawable(R.drawable.ic_information)
                val foreground = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer))
                val background = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer))
                setBoxBackgroundColorStateList(background)
                setStartIconTintList(foreground)
                mTextInputTextView.setTextColor(foreground)
            }
        }
    }

    companion object {
        const val ALERT_TYPE_CUSTOM = -1
        const val ALERT_TYPE_INFO = 0
        const val ALERT_TYPE_WARN = 1

        private val DEF_STYLE_RES = R.style.Widget_AppTheme_MaterialAlertView
    }
}
