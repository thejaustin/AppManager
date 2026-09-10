// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.UserHandleHidden
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.Nullable
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils
import io.github.muntashirakon.widget.TextInputTextView
import java.util.concurrent.Future

class TrackerWindow @SuppressLint("ClickableViewAccessibility") constructor(context: Context) : View.OnTouchListener {
    val mWindowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mWindowLayoutParams: WindowManager.LayoutParams
    private val mView: View
    private val mIconView: ShapeableImageView
    private val mContentView: MaterialCardView
    private val mPackageNameView: TextInputTextView
    private val mActivityNameView: TextInputTextView
    private val mClassNameView: TextInputTextView
    private val mClassHierarchyView: TextInputTextView
    private val mPlayPauseButton: MaterialButton
    private val mWindowSize = Point(0, 0)
    private val mWindowPosition = Point(0, 0)
    private val mPressPosition = Point(0, 0)
    private val mMaxWidth: Int
    private var mPaused = false
    private var mIconified = false
    private var mViewAttached = false
    @Nullable
    private var mClassHierarchyResult: Future<*>? = null

    init {
        val themedContext = AppearanceUtils.getThemedContext(context, true)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        @Suppress("DEPRECATION")
        val display = mWindowManager.defaultDisplay
        @Suppress("DEPRECATION")
        val displayWidth = display.width
        @Suppress("DEPRECATION")
        display.getRealSize(mWindowSize)
        mMaxWidth = (displayWidth / 2) + 300
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        mWindowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, flags, PixelFormat.TRANSLUCENT
        )
        mWindowLayoutParams.gravity = Gravity.CENTER
        mWindowLayoutParams.width = mMaxWidth
        mWindowLayoutParams.windowAnimations = android.R.style.Animation_Toast

        mView = View.inflate(themedContext, R.layout.window_activity_tracker, null)
        mIconView = mView.findViewById(R.id.icon)
        mContentView = mView.findViewById(R.id.content)
        mPackageNameView = mView.findViewById(R.id.package_name)
        mActivityNameView = mView.findViewById(R.id.activity_name)
        mClassNameView = mView.findViewById(R.id.class_name)
        mClassHierarchyView = mView.findViewById(R.id.class_hierarchy)
        mPlayPauseButton = mView.findViewById(R.id.action_play_pause)

        mPackageNameView.setOnLongClickListener {
            val packageName = mPackageNameView.text
            if (packageName.isNullOrEmpty()) return@setOnLongClickListener false
            copyText("Package name", packageName)
            true
        }
        mActivityNameView.setOnLongClickListener {
            val activityName = mActivityNameView.text
            if (activityName.isNullOrEmpty()) return@setOnLongClickListener false
            copyText("Activity name", activityName)
            true
        }
        mClassNameView.setOnLongClickListener {
            val className = mClassNameView.text
            if (className.isNullOrEmpty()) return@setOnLongClickListener false
            copyText("Class name", className)
            true
        }
        mClassHierarchyView.setOnLongClickListener {
            val hierarchy = mClassHierarchyView.text
            if (hierarchy.isNullOrEmpty()) return@setOnLongClickListener false
            copyText("Class hierarchy", hierarchy)
            true
        }
        mView.findViewById<View>(R.id.info).setOnClickListener {
            val packageName = mPackageNameView.text
            if (packageName.isNullOrEmpty()) return@setOnClickListener
            val appInfoIntent = AppDetailsActivity.getIntent(context, packageName.toString(), UserHandleHidden.myUserId(), true)
            appInfoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(appInfoIntent)
            } catch (th: Throwable) {
                UIUtils.displayLongToast("Error: ${th.message}")
            }
        }
        mView.findViewById<View>(R.id.mini).setOnClickListener { iconify() }
        mPlayPauseButton.setOnClickListener {
            mPaused = !mPaused
            mPlayPauseButton.setIconResource(if (mPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause)
        }
        mView.findViewById<View>(android.R.id.closeButton).setOnClickListener { dismiss() }
        mIconView.visibility = View.GONE
        mIconView.setOnClickListener { expand() }
        mView.findViewById<View>(R.id.drag).setOnTouchListener(this)
        mIconView.setOnTouchListener(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val action = event.action
        if (action == MotionEvent.ACTION_DOWN) {
            mPressPosition.set(event.rawX.toInt(), event.rawY.toInt())
            mWindowPosition.set(mWindowLayoutParams.x, mWindowLayoutParams.y)
            return true
        } else if (action == MotionEvent.ACTION_MOVE) {
            val delX = event.rawX.toInt() - mPressPosition.x
            val delY = event.rawY.toInt() - mPressPosition.y
            mWindowLayoutParams.x = mWindowPosition.x + delX
            mWindowLayoutParams.y = mWindowPosition.y + delY
            updateLayout()
            return true
        }
        if (v === mIconView && action == MotionEvent.ACTION_UP) {
            val delX = Math.abs(event.rawX.toInt() - mPressPosition.x)
            val delY = Math.abs(event.rawY.toInt() - mPressPosition.y)
            if (delX < 1 && delY < 1) {
                v.performClick()
                return true
            }
        }
        return false
    }

    fun showOrUpdate(event: AccessibilityEvent) {
        if (!mViewAttached) {
            mViewAttached = true
            mWindowManager.addView(mView, mWindowLayoutParams)
        }
        if (!mPaused) {
            val packageName = event.packageName
            if (packageName != null && BuildConfig.APPLICATION_ID.contentEquals(packageName)) {
                val className = event.className
                if (className != null && "android.widget.EditText".contentEquals(className)) {
                    if (event.source == null) return
                }
            }
            mClassHierarchyResult?.cancel(true)
            mPackageNameView.setText(packageName)
            mClassNameView.setText(event.className)
            mClassHierarchyResult = ThreadUtils.postOnBackgroundThread {
                val classHierarchy = getClassHierarchy(event).joinToString("\n")
                val activityName = getActivityName(event)
                ThreadUtils.postOnMainThread {
                    mActivityNameView.setText(activityName)
                    mClassHierarchyView.setText(classHierarchy)
                }
            }
        }
    }

    fun dismiss() {
        AccessibilityMultiplexer.getInstance().enableLeadingActivityTracker(false)
        mViewAttached = false
        mClassHierarchyResult?.cancel(true)
        try {
            mWindowManager.removeView(mView)
        } catch (_: Exception) {
        }
    }

    private fun iconify() {
        mPaused = true
        mIconified = true
        val height = -mWindowSize.y / 2
        if (mWindowLayoutParams.y < height) {
            mWindowPosition.y = height
            mWindowLayoutParams.y = height
        }
        mIconView.visibility = View.VISIBLE
        mContentView.visibility = View.GONE
        updateLayout()
    }

    private fun expand() {
        mContentView.visibility = View.VISIBLE
        mIconView.visibility = View.GONE
        mPaused = false
        mIconified = false
        val width = (-mWindowSize.x + mMaxWidth) / 2
        if (mWindowLayoutParams.x < width) {
            mWindowPosition.x = width
            mWindowLayoutParams.x = width
        }
        updateLayout()
    }

    private fun updateLayout() {
        mWindowLayoutParams.width = if (mIconified) WindowManager.LayoutParams.WRAP_CONTENT else mMaxWidth
        mWindowManager.updateViewLayout(mView, mWindowLayoutParams)
    }

    private fun copyText(label: CharSequence, content: CharSequence) {
        Utils.copyToClipboard(mView.context, label, content)
    }

    fun getActivityName(event: AccessibilityEvent): String? {
        val packageName = event.packageName?.toString() ?: return null
        val usageEvent = UsageEvents.Event()
        val currentTimeMillis = System.currentTimeMillis()
        var timeDiff = 5_000L
        var tries = 0
        do {
            val queryEvents = UsageStatsManagerCompat.queryEvents(
                currentTimeMillis - timeDiff, currentTimeMillis, UserHandleHidden.myUserId()
            ) ?: return null
            var lastTime = 0L
            var activityName: String? = null
            while (queryEvents.hasNextEvent()) {
                queryEvents.getNextEvent(usageEvent)
                if (usageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    && packageName == usageEvent.packageName
                    && lastTime < usageEvent.timeStamp) {
                    lastTime = usageEvent.timeStamp
                    activityName = usageEvent.className
                }
            }
            if (activityName != null) return activityName
            timeDiff *= 60
        } while ((++tries) != 3)
        return null
    }

    companion object {
        private fun getClassHierarchy(event: AccessibilityEvent): List<CharSequence> {
            val classHierarchies = mutableListOf<CharSequence>()
            var nodeInfo: AccessibilityNodeInfo = event.source ?: return classHierarchies
            classHierarchies.add(nodeInfo.className)
            var depth = 0
            while (depth < 20) {
                val tmpNodeInfo = nodeInfo.parent
                if (tmpNodeInfo != null) {
                    nodeInfo.recycle()
                    nodeInfo = tmpNodeInfo
                    classHierarchies.add(nodeInfo.className)
                } else {
                    break
                }
                ++depth
                if (ThreadUtils.isInterrupted()) return emptyList()
            }
            try {
                if (depth == 20) classHierarchies.add("...")
            } finally {
                nodeInfo.recycle()
            }
            classHierarchies.reverse()
            if (ThreadUtils.isInterrupted()) return emptyList()
            val size = classHierarchies.size
            if (size <= 1) return classHierarchies
            classHierarchies[0] = "┬ ${classHierarchies[0]}"
            for (i in 1 until size) {
                val sb = StringBuilder()
                repeat(i - 1) { sb.append(' ') }
                if (i != size - 1) sb.append("└┬ ") else sb.append("└─ ")
                sb.append(classHierarchies[i])
                classHierarchies[i] = sb.toString()
                if (ThreadUtils.isInterrupted()) return emptyList()
            }
            return classHierarchies
        }
    }
}
