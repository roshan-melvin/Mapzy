package com.swapmap.zwap.demo.navigation

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.db.DriverTask

/**
 * Bottom-bar task indicator widget.
 *
 * No tasks → shows a 24dp note icon (same visual weight as hamburger/search).
 * Tasks exist → shows a pill with:
 *   · top row : category emoji + task name  (marquee if ≥ 6 chars)
 *   · bottom row: distance text
 */
class TaskIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val staticIcon: ImageView
    private val dynamicWidget: LinearLayout
    private val tvName: TextView
    private val tvDist: TextView
    private var currentTask: DriverTask? = null

    init {
        // ── Static note icon (default / no-tasks state) ───────────────────────
        staticIcon = ImageView(context).apply {
            val size = dp(24)
            layoutParams = LayoutParams(size, size).also { it.gravity = Gravity.CENTER }
            setImageResource(R.drawable.ic_note_document)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(staticIcon)

        // ── Dynamic pill (tasks-exist state) ─────────────────────────────────
        // Task name — uses Android native marquee (no ValueAnimator needed)
        tvName = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setTextColor(Color.WHITE)
            textSize = 10f
            maxLines = 1
            gravity = Gravity.CENTER_HORIZONTAL
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1          // repeat forever
            isSelected = true               // activates marquee without focus
            isSingleLine = true
        }

        // Distance
        tvDist = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 9f
            maxLines = 1
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Pill container (vertical, centred)
        dynamicWidget = LinearLayout(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            visibility = GONE
        }
        dynamicWidget.addView(tvName)
        dynamicWidget.addView(tvDist)
        addView(dynamicWidget)
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun updateTask(task: DriverTask?, distanceKm: Double?) {
        if (task == null || task.isCompleted) { showStatic(); return }

        currentTask = task
        val cat   = TaskCategory.fromName(task.category)
        val emoji = cat?.emoji ?: "\uD83D\uDCCB"
        val color = cat?.color ?: Color.parseColor("#26A69A")

        tvName.text = "$emoji ${task.text}"
        tvDist.text = if (distanceKm != null) "%.1f km".format(distanceKm) else ""

        val bg = GradientDrawable()
        bg.cornerRadius = 100f
        bg.setColor(color)
        bg.setStroke(2, Color.BLACK)
        dynamicWidget.background = bg

        showDynamic()
    }

    fun hasTask(): Boolean = currentTask != null

    // ─── Internal ────────────────────────────────────────────────────────────

    private fun showStatic() {
        currentTask = null
        dynamicWidget.visibility = GONE
        staticIcon.visibility = VISIBLE
        // Apply dark charcoal pill background to match the other icon buttons
        val bg = GradientDrawable()
        bg.cornerRadius = dp(100).toFloat()
        bg.setColor(Color.parseColor("#000000"))
        bg.setStroke(dp(1), Color.parseColor("#303030"))
        background = bg
        // Shrink to icon width (same weight as search button)
        (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.weight = 1f
            layoutParams = lp
        }
    }

    private fun showDynamic() {
        staticIcon.visibility = GONE
        dynamicWidget.visibility = VISIBLE
        // Remove container background — dynamicWidget carries its own colored pill
        background = null
        // Expand to pill width (same weight as hazard pill)
        (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.weight = 2.5f
            layoutParams = lp
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tvName.isSelected = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tvName.isSelected = true
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
}
