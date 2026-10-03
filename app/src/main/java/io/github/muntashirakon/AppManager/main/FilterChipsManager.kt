// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.content.Context
import android.view.View
import android.view.ViewStub
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.appearance.ExpressiveShapeMorph

/**
 * Manages quick filter chips for the main app list.
 * Provides one-tap filtering for common app categories.
 */
class FilterChipsManager(
    private val mContext: Context,
    private val mFilterChipsStub: ViewStub?,
    private val mOnFilterChangeListener: OnFilterChangeListener
) {
    private var mFilterChipGroup: ChipGroup? = null
    private var mFilterChipsScroll: HorizontalScrollView? = null
    private var mActiveFilters: MutableSet<Int> = mutableSetOf()
    
    interface OnFilterChangeListener {
        fun onFilterChanged(filters: Set<Int>)
    }
    
    /**
     * Initialize and display filter chips
     */
    fun initialize() {
        if (mFilterChipsStub == null) return
        
        mFilterChipsScroll = mFilterChipsStub.inflate() as? HorizontalScrollView
        mFilterChipGroup = mFilterChipsScroll?.findViewById(R.id.filter_chip_group)
        
        setupFilterChips()
    }
    
    /**
     * Setup all filter chips
     */
    private fun setupFilterChips() {
        mFilterChipGroup?.apply {
            removeAllViews()
            
            // Add predefined quick filters
            addFilterChip(FILTER_USER_APPS, R.string.filter_user_apps)
            addFilterChip(FILTER_SYSTEM_APPS, R.string.filter_system_apps)
            addFilterChip(FILTER_FROZEN_APPS, R.string.filter_frozen_apps)
            addFilterChip(FILTER_UNFROZEN_APPS, R.string.filter_unfrozen_apps)
            addFilterChip(FILTER_DISABLED_APPS, R.string.filter_disabled_apps)
            addFilterChip(FILTER_RUNNING_APPS, R.string.filter_running_apps)
            addFilterChip(FILTER_APPS_WITH_BACKUPS, R.string.filter_apps_with_backups)
            addFilterChip(FILTER_APPS_WITHOUT_BACKUPS, R.string.filter_apps_without_backups)
            addFilterChip(FILTER_APPS_WITH_RULES, R.string.filter_apps_with_rules)
            addFilterChip(FILTER_STOPPED_APPS, R.string.filter_force_stopped_apps)
            addFilterChip(FILTER_APPS_WITH_KEYSTORE, R.string.filter_apps_with_keystore)
            addFilterChip(FILTER_APPS_WITH_SPLITS, R.string.filter_apps_with_splits)
            addFilterChip(FILTER_APPS_WITH_SAF, R.string.filter_apps_with_saf)
        }
    }
    
    /**
     * Add a single filter chip to the group
     */
    private fun addFilterChip(filterFlag: Int, stringResId: Int) {
        val chip = Chip(mContext).apply {
            text = mContext.getString(stringResId)
            tag = filterFlag
            isCheckable = true
            isCheckedIconVisible = false
            setChipBackgroundColorResource(R.color.filter_chip_background)
            setTextColor(ContextCompat.getColor(mContext, R.color.filter_chip_text))

            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) mActiveFilters.add(filterFlag) else mActiveFilters.remove(filterFlag)
                mOnFilterChangeListener.onFilterChanged(mActiveFilters.toSet())
            }
            ExpressiveShapeMorph.bindChipSelectionMorph(this, uncheckedRadiusDp = 8f, checkedRadiusDp = 20f)
        }
        mFilterChipGroup?.addView(chip)
    }
    
    /**
     * Clear all active filters
     */
    fun clearFilters() {
        mFilterChipGroup?.apply {
            for (i in 0 until childCount) {
                val chip = getChildAt(i) as? Chip
                chip?.isChecked = false
            }
        }
        mActiveFilters.clear()
        mOnFilterChangeListener.onFilterChanged(emptySet())
    }
    
    /**
     * Get currently active filters
     */
    fun getActiveFilters(): Set<Int> = mActiveFilters.toSet()
    
    /**
     * Set active filters programmatically (from a set of flag values).
     */
    fun setActiveFilters(filters: Set<Int>) {
        mActiveFilters.clear()
        mActiveFilters.addAll(filters)
        syncChipCheckedState(filters)
    }

    /**
     * Restore chip checked state from a saved filter bitmask (e.g. from Prefs).
     */
    fun restoreFromBitmask(bitmask: Int) {
        val active = CHIP_FLAGS.filter { bitmask and it != 0 }.toSet()
        mActiveFilters.clear()
        mActiveFilters.addAll(active)
        syncChipCheckedState(active)
    }

    private fun syncChipCheckedState(filters: Set<Int>) {
        mFilterChipGroup?.apply {
            for (i in 0 until childCount) {
                val chip = getChildAt(i) as? Chip ?: continue
                val flag = chip.tag as? Int ?: continue
                // Suppress the listener while programmatically toggling so we don't re-fire
                chip.setOnCheckedChangeListener(null)
                chip.isChecked = filters.contains(flag)
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) mActiveFilters.add(flag) else mActiveFilters.remove(flag)
                    mOnFilterChangeListener.onFilterChanged(mActiveFilters.toSet())
                }
            }
        }
    }
    
    /**
     * Hide filter chips
     */
    fun hide() {
        mFilterChipsScroll?.visibility = View.GONE
    }
    
    /**
     * Show filter chips
     */
    fun show() {
        mFilterChipsScroll?.visibility = View.VISIBLE
    }
    
    companion object {
        // Filter flags (matching MainListOptions)
        const val FILTER_USER_APPS = MainListOptions.FILTER_USER_APPS
        const val FILTER_SYSTEM_APPS = MainListOptions.FILTER_SYSTEM_APPS
        const val FILTER_FROZEN_APPS = MainListOptions.FILTER_FROZEN_APPS
        const val FILTER_UNFROZEN_APPS = MainListOptions.FILTER_UNFROZEN_APPS
        const val FILTER_RUNNING_APPS = MainListOptions.FILTER_RUNNING_APPS
        const val FILTER_APPS_WITH_BACKUPS = MainListOptions.FILTER_APPS_WITH_BACKUPS
        const val FILTER_APPS_WITHOUT_BACKUPS = MainListOptions.FILTER_APPS_WITHOUT_BACKUPS
        const val FILTER_APPS_WITH_RULES = MainListOptions.FILTER_APPS_WITH_RULES
        const val FILTER_STOPPED_APPS = MainListOptions.FILTER_STOPPED_APPS
        const val FILTER_DISABLED_APPS = MainListOptions.FILTER_DISABLED_APPS
        const val FILTER_APPS_WITH_KEYSTORE = MainListOptions.FILTER_APPS_WITH_KEYSTORE
        const val FILTER_APPS_WITH_SPLITS = MainListOptions.FILTER_APPS_WITH_SPLITS
        const val FILTER_APPS_WITH_SAF = MainListOptions.FILTER_APPS_WITH_SAF

        // All flags that have a chip — order matches setupFilterChips()
        val CHIP_FLAGS = listOf(
            FILTER_USER_APPS, FILTER_SYSTEM_APPS, FILTER_FROZEN_APPS, FILTER_UNFROZEN_APPS,
            FILTER_DISABLED_APPS, FILTER_RUNNING_APPS, FILTER_APPS_WITH_BACKUPS,
            FILTER_APPS_WITHOUT_BACKUPS, FILTER_APPS_WITH_RULES, FILTER_STOPPED_APPS,
            FILTER_APPS_WITH_KEYSTORE, FILTER_APPS_WITH_SPLITS, FILTER_APPS_WITH_SAF
        )
    }
}
