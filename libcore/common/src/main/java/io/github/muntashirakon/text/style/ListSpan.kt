// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.text.style

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan
import java.util.Locale

class ListSpan : LeadingMarginSpan {
    private val mLeadingGapWidth: Int
    private val mTrailingGapWidth: Int
    private val mText: String

    constructor(leadingGap: Int, trailingGap: Int, index: Int, locale: Locale) {
        mLeadingGapWidth = leadingGap
        mTrailingGapWidth = trailingGap
        mText = String.format(locale, "%d.", index)
    }

    constructor(leadingGap: Int, trailingGap: Int, index: Int) {
        mLeadingGapWidth = leadingGap
        mTrailingGapWidth = trailingGap
        mText = String.format(Locale.getDefault(), "%d.", index)
    }

    constructor(leadingGap: Int, trailingGap: Int, ch: Char) {
        mLeadingGapWidth = leadingGap
        mTrailingGapWidth = trailingGap
        mText = ch.toString()
    }

    override fun getLeadingMargin(first: Boolean): Int = mLeadingGapWidth + mTrailingGapWidth

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, l: Layout
    ) {
        if (first) {
            val lastStyle = p.style
            p.style = Paint.Style.FILL
            val width = p.measureText(mText)
            c.drawText(mText, (x * dir).toFloat(), bottom - p.descent(), p)
            c.drawText("", ((mLeadingGapWidth + x - width / 2) * dir), bottom - p.descent(), p)
            p.style = lastStyle
        }
    }
}
