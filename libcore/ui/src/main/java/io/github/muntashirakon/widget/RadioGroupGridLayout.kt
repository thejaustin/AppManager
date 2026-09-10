// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CompoundButton
import android.widget.GridLayout
import androidx.appcompat.widget.AppCompatRadioButton
import java.util.concurrent.atomic.AtomicInteger

class RadioGroupGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GridLayout(context, attrs) {

    private var mCheckedId = -1
    private val mChildOnCheckedChangeListener: CompoundButton.OnCheckedChangeListener
    private var mProtectFromCheckedChange = false
    private var mOnCheckedChangeListener: OnCheckedChangeListener? = null
    private val mPassThroughListener: PassThroughHierarchyChangeListener

    init {
        mChildOnCheckedChangeListener = CheckedStateTracker()
        mPassThroughListener = PassThroughHierarchyChangeListener()
        super.setOnHierarchyChangeListener(mPassThroughListener)
    }

    override fun setOnHierarchyChangeListener(listener: OnHierarchyChangeListener?) {
        mPassThroughListener.mOnHierarchyChangeListener = listener
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (mCheckedId != -1) {
            mProtectFromCheckedChange = true
            setCheckedStateForView(mCheckedId, true)
            mProtectFromCheckedChange = false
            setCheckedId(mCheckedId)
        }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child is AppCompatRadioButton && child.isChecked) {
            mProtectFromCheckedChange = true
            if (mCheckedId != -1) {
                setCheckedStateForView(mCheckedId, false)
            }
            mProtectFromCheckedChange = false
            setCheckedId(child.id)
        }
        super.addView(child, index, params)
    }

    fun check(id: Int) {
        if (id != -1 && id == mCheckedId) return
        if (mCheckedId != -1) setCheckedStateForView(mCheckedId, false)
        if (id != -1) setCheckedStateForView(id, true)
        setCheckedId(id)
    }

    private fun setCheckedId(id: Int) {
        mCheckedId = id
        mOnCheckedChangeListener?.onCheckedChanged(this, mCheckedId)
    }

    private fun setCheckedStateForView(viewId: Int, checked: Boolean) {
        val checkedView = findViewById<View>(viewId)
        if (checkedView is AppCompatRadioButton) {
            checkedView.isChecked = checked
        }
    }

    fun getCheckedCheckableImageButtonId(): Int = mCheckedId

    fun clearCheck() = check(-1)

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        mOnCheckedChangeListener = listener
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.className = RadioGroupGridLayout::class.java.name
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = RadioGroupGridLayout::class.java.name
    }

    interface OnCheckedChangeListener {
        fun onCheckedChanged(group: RadioGroupGridLayout, checkedId: Int)
    }

    private inner class CheckedStateTracker : CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
            if (mProtectFromCheckedChange) return
            mProtectFromCheckedChange = true
            if (mCheckedId != -1) setCheckedStateForView(mCheckedId, false)
            mProtectFromCheckedChange = false
            setCheckedId(buttonView.id)
        }
    }

    private inner class PassThroughHierarchyChangeListener : ViewGroup.OnHierarchyChangeListener {
        var mOnHierarchyChangeListener: ViewGroup.OnHierarchyChangeListener? = null

        override fun onChildViewAdded(parent: View, child: View) {
            if (parent === this@RadioGroupGridLayout && child is AppCompatRadioButton) {
                var id = child.id
                if (id == View.NO_ID) {
                    id = generateViewId()
                    child.id = id
                }
                child.setOnCheckedChangeListener(mChildOnCheckedChangeListener)
            }
            mOnHierarchyChangeListener?.onChildViewAdded(parent, child)
        }

        override fun onChildViewRemoved(parent: View, child: View) {
            if (parent === this@RadioGroupGridLayout && child is AppCompatRadioButton) {
                child.setOnCheckedChangeListener(null)
            }
            mOnHierarchyChangeListener?.onChildViewRemoved(parent, child)
        }
    }

    companion object {
        private val sNextGeneratedId = AtomicInteger(1)

        @JvmStatic
        fun generateViewId(): Int {
            while (true) {
                val result = sNextGeneratedId.get()
                var newValue = result + 1
                if (newValue > 0x00FFFFFF) newValue = 1
                if (sNextGeneratedId.compareAndSet(result, newValue)) {
                    return result
                }
            }
        }
    }
}
