/*
 * AccessComplete, an easy to use editor of accessibility related
 * OpenStreetMap data for Android.  This program is a fork of
 * StreetComplete (https://github.com/westnordost/StreetComplete).
 *
 * Copyright (C) 2020 Sven Stoll (AccessComplete author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ch.uzh.ifi.accesscomplete.quests.incline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import ch.uzh.ifi.accesscomplete.R
import ch.uzh.ifi.accesscomplete.ktx.toPx
import ch.uzh.ifi.accesscomplete.util.checkIfTalkBackIsActive
import ch.uzh.ifi.accesscomplete.util.fromDegreesToPercentage
import kotlin.math.abs
import kotlin.math.roundToInt

class InclineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onLockChanged(locked: Boolean)
    }

    private var listeners: MutableList<Listener> = mutableListOf()

    var locked: Boolean = false
    var inclineInDegrees: Double = 0.0
    private var lastRenderedInclineInDegrees: Double = 0.0

    private val backgroundPaint = Paint()
    private val foregroundPaint = Paint()
    private val textPaint = Paint()

    @ColorInt private var lockedColorInt = ContextCompat.getColor(context, R.color.accent)
    @ColorInt private var unlockedColorInt = ContextCompat.getColor(context, R.color.primary)

    init {
        backgroundPaint.color = ContextCompat.getColor(context, R.color.inverted_background)
        foregroundPaint.color = unlockedColorInt

        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 32f.toPx(context)

        setOnClickListener {
            changeLock(!locked)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null) {
            return
        }

        this.lastRenderedInclineInDegrees = this.inclineInDegrees
        drawVisualRepresentationOfDegrees(canvas)
        drawInclineInPercentageLabel(canvas)
        drawLockIcon(canvas)
    }

    private fun drawVisualRepresentationOfDegrees(canvas: Canvas) {
        canvas.drawRect(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            backgroundPaint)
        canvas.save()

        canvas.rotate((-inclineInDegrees).toFloat(), width.toFloat() / 2f, height.toFloat() / 2f)

        canvas.drawRect(
            -width.toFloat(), height.toFloat() / 2f,
            width.toFloat() * 4f, height.toFloat() * 4f,
            foregroundPaint)
        canvas.restore()
    }

    private fun drawInclineInPercentageLabel(canvas: Canvas) {
        val inclineValueFormatted = formatInclineInDegrees()
        val label = when {
                inclineInDegrees > 89.0 -> "∞ %"
                inclineInDegrees < -89.0 -> "-∞ %"
                else -> "$inclineValueFormatted %"
            }

        canvas.drawText(
            label,
            width / 2f,
            ((height / 2) - ((textPaint.descent() + textPaint.ascent()) / 2)),
            textPaint)

        contentDescription = context.getString(R.string.incline_view_content_description, inclineValueFormatted)
    }

    private fun formatInclineInDegrees(): String {
        return "%d".format(inclineInDegrees.fromDegreesToPercentage().roundToInt())
    }

    private fun drawLockIcon(canvas: Canvas) {
        val lockIconResource = if (locked) R.drawable.ic_lock_24 else R.drawable.ic_lock_open_24
        val lockIconDrawable: Drawable? = ContextCompat.getDrawable(context, lockIconResource)
        val iconSizeDp = (32f.toPx(context)).roundToInt()
        val offsetDp = (8f.toPx(context)).roundToInt()
        lockIconDrawable?.setBounds(
            width - (iconSizeDp + offsetDp),
            (height / 2) - (iconSizeDp / 2),
            width - offsetDp,
            (height / 2) + (iconSizeDp / 2))
        lockIconDrawable?.draw(canvas)
    }

    fun changeIncline(inclineInDegrees: Double) {
        val shouldInvalidate = abs(this.lastRenderedInclineInDegrees - inclineInDegrees) >= 0.5
        this.inclineInDegrees = inclineInDegrees

        if (shouldInvalidate) {
            invalidate()
        }
    }

    fun changeLock(locked: Boolean) {
        this.locked = locked
        foregroundPaint.color = if (locked) lockedColorInt else unlockedColorInt
        invalidate()

        listeners.forEach {
            it.onLockChanged(locked)
        }

        sendAccessibilityAnnouncementAfterLockAction()
    }

    private fun sendAccessibilityAnnouncementAfterLockAction() {
        if (!isShown || !checkIfTalkBackIsActive(context)) {
            return
        }

        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val inclineValueFormatted = formatInclineInDegrees()
        val announcement = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)

        val textId =
            if (locked) R.string.incline_view_measurement_locked_announcement
            else R.string.incline_view_measurement_unlocked_announcement

        announcement.text.add(resources.getString(textId, inclineValueFormatted))
        announcement.contentDescription = null
        announcement.className = javaClass.name
        announcement.packageName = context.packageName
        dispatchPopulateAccessibilityEvent(announcement)
        accessibilityManager.sendAccessibilityEvent(announcement)
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
