// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipDrawable
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.UiUtils

class ChangelogRecyclerAdapter : RecyclerView.Adapter<ChangelogRecyclerAdapter.ViewHolder>() {
    private val mAdapterList: MutableList<ChangelogItem> = ArrayList()

    fun setAdapterList(list: List<ChangelogItem>) {
        synchronized(mAdapterList) {
            AdapterUtils.notifyDataSetChanged(this, mAdapterList, list)
        }
    }

    @ChangelogItem.ChangelogType
    override fun getItemViewType(position: Int): Int =
        synchronized(mAdapterList) { mAdapterList[position].type }

    override fun onCreateViewHolder(parent: ViewGroup, @ChangelogItem.ChangelogType viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(
            if (viewType == ChangelogItem.HEADER) R.layout.item_changelog_header
            else R.layout.item_changelog_item,
            parent, false
        )
        return ViewHolder(v, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val changelogItem = synchronized(mAdapterList) { mAdapterList[position] }
        val context = holder.itemView.context
        when (changelogItem.type) {
            ChangelogItem.HEADER -> {
                val header = changelogItem as ChangelogHeader
                holder.label.text = header.releaseType
                holder.title.text = changelogItem.changeText
                holder.subtitle.text = header.releaseDate
            }
            ChangelogItem.TITLE -> {
                TextViewCompat.setTextAppearance(holder.subtitle, getTitleTextAppearance(changelogItem.changeTextType))
                holder.subtitle.text = getChangeText(context, changelogItem)
            }
            else -> {
                TextViewCompat.setTextAppearance(holder.subtitle, getChangeTextAppearance(changelogItem.changeTextType))
                holder.subtitle.text = getChangeText(context, changelogItem)
            }
        }
    }

    override fun getItemCount(): Int = synchronized(mAdapterList) { mAdapterList.size }

    private fun getChangeText(context: android.content.Context, item: ChangelogItem): CharSequence {
        val sb = SpannableStringBuilder()
        if (item.isBulletedList) {
            if (item.isSubtext) sb.append("    ")
            sb.append("• ")
        } else {
            val (tagNameRes, backgroundColorRes, color) = when (item.type) {
                ChangelogItem.FIX -> Triple(R.string.changelog_type_fix, io.github.muntashirakon.ui.R.color.changelog_fix, Color.BLACK)
                ChangelogItem.IMPROVE -> Triple(R.string.changelog_type_improve, io.github.muntashirakon.ui.R.color.changelog_improve, Color.WHITE)
                ChangelogItem.NEW -> Triple(R.string.changelog_type_new, io.github.muntashirakon.ui.R.color.changelog_new, Color.WHITE)
                else -> Triple(0, 0, 0)
            }
            if (tagNameRes != 0) {
                val chip = ChipDrawable.createFromAttributes(
                    context, null,
                    com.google.android.material.R.attr.chipStandaloneStyle,
                    com.google.android.material.R.style.Widget_Material3_Chip_Assist_Elevated
                ).apply {
                    setTextResource(tagNameRes)
                    setTextColor(color)
                    setTextSize(UiUtils.spToPx(context, 10).toFloat())
                    setChipBackgroundColorResource(backgroundColorRes)
                    isCloseIconVisible = false
                    chipStartPadding = 0f
                    chipEndPadding = 0f
                    setBounds(0, 0, intrinsicWidth, UiUtils.dpToPx(context, 20))
                }
                val span = ImageSpan(chip)
                sb.append(" ")
                sb.setSpan(span, sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(" ")
            }
        }
        item.changeTitle?.let { sb.append('[').append(it).append("] ") }
        return sb.append(item.changeText)
    }

    class ViewHolder(itemView: View, @ChangelogItem.ChangelogType viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val label: TextView = itemView.findViewById(R.id.item_label)
        val title: TextView = itemView.findViewById(R.id.item_title)
        val subtitle: TextView = itemView.findViewById(R.id.item_subtitle)

        init {
            subtitle.movementMethod = LinkMovementMethod.getInstance()
            if (viewType == ChangelogItem.HEADER) {
                title.movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    companion object {
        @JvmStatic
        @StyleRes
        fun getChangeTextAppearance(@ChangelogItem.ChangeTextType type: Int): Int = when (type) {
            ChangelogItem.TEXT_LARGE -> com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
            ChangelogItem.TEXT_SMALL -> com.google.android.material.R.style.TextAppearance_Material3_BodySmall
            else -> com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
        }

        @JvmStatic
        @StyleRes
        fun getTitleTextAppearance(@ChangelogItem.ChangeTextType type: Int): Int = when (type) {
            ChangelogItem.TEXT_LARGE -> com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
            ChangelogItem.TEXT_SMALL -> com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
            else -> com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
        }
    }
}
