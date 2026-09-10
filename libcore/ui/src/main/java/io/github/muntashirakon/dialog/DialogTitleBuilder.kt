// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.ui.R
import io.github.muntashirakon.util.UiUtils

class DialogTitleBuilder(private val mContext: Context) {
    private val mView: View = View.inflate(mContext, R.layout.dialog_title_with_two_icons, null)
    private val mTitle: TextView = mView.findViewById(R.id.title)
    private val mSubtitle: TextView = mView.findViewById<TextView>(R.id.subtitle).also {
        it.visibility = View.GONE
    }
    private val mStartIcon: ImageView = mView.findViewById<ImageView>(R.id.icon).also {
        it.visibility = View.GONE
    }
    private val mEndIcon: MaterialButton = mView.findViewById<MaterialButton>(R.id.action).also {
        it.visibility = View.GONE
    }
    private val mIconTopPadding: Int = UiUtils.dpToPx(mContext, 12)

    fun setTitle(title: CharSequence?): DialogTitleBuilder {
        mTitle.text = title
        return this
    }

    fun setTitle(@StringRes titleRes: Int): DialogTitleBuilder {
        mTitle.setText(titleRes)
        return this
    }

    fun setTitleSelectable(titleSelectable: Boolean): DialogTitleBuilder {
        mTitle.setTextIsSelectable(titleSelectable)
        return this
    }

    fun setSubtitle(subtitle: CharSequence?): DialogTitleBuilder {
        toggleVisibility(mSubtitle, subtitle != null)
        updateIconPadding(subtitle != null)
        mSubtitle.text = subtitle
        return this
    }

    fun setSubtitle(@StringRes subtitleRes: Int): DialogTitleBuilder {
        toggleVisibility(mSubtitle, subtitleRes != 0)
        updateIconPadding(subtitleRes != 0)
        mSubtitle.setText(subtitleRes)
        return this
    }

    fun setSubtitleSelectable(subtitleSelectable: Boolean): DialogTitleBuilder {
        mSubtitle.setTextIsSelectable(subtitleSelectable)
        return this
    }

    fun setStartIcon(startIcon: Drawable?): DialogTitleBuilder {
        toggleVisibility(mStartIcon, startIcon != null)
        mStartIcon.setImageDrawable(startIcon)
        return this
    }

    fun setStartIcon(@DrawableRes startIconRes: Int): DialogTitleBuilder {
        toggleVisibility(mStartIcon, startIconRes != 0)
        mStartIcon.setImageResource(startIconRes)
        return this
    }

    fun setEndIcon(endIcon: Drawable?, listener: View.OnClickListener?): DialogTitleBuilder {
        toggleVisibility(mEndIcon, endIcon != null)
        mEndIcon.icon = endIcon
        mEndIcon.setOnClickListener(listener)
        return this
    }

    fun setEndIcon(@DrawableRes endIconRes: Int, listener: View.OnClickListener?): DialogTitleBuilder {
        toggleVisibility(mEndIcon, endIconRes != 0)
        mEndIcon.setIconResource(endIconRes)
        mEndIcon.setOnClickListener(listener)
        return this
    }

    fun setEndIconContentDescription(endIconContentDescription: CharSequence?): DialogTitleBuilder {
        mEndIcon.contentDescription = endIconContentDescription
        return this
    }

    fun setEndIconContentDescription(@StringRes endIconContentDescriptionRes: Int): DialogTitleBuilder {
        mEndIcon.contentDescription = mContext.getText(endIconContentDescriptionRes)
        return this
    }

    fun build(): View = mView

    private fun toggleVisibility(v: View, show: Boolean) {
        val isShown = v.isShown
        if (show) {
            if (!isShown) v.visibility = View.VISIBLE
        } else if (isShown) {
            v.visibility = View.GONE
        }
    }

    private fun updateIconPadding(set: Boolean) {
        (mStartIcon.parent as FrameLayout).setPadding(0, if (set) mIconTopPadding else 0, 0, 0)
    }
}
