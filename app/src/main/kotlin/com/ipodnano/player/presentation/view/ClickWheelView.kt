package com.ipodnano.player.presentation.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

class ClickWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface ClickWheelListener {
        fun onWheelRotate(deltaSteps: Int)
        fun onMenuClick()
        fun onSelectClick()
        fun onPlayPauseClick()
        fun onNextClick()
        fun onPreviousClick()
    }

    var listener: ClickWheelListener? = null

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6E6E8")
        style = Paint.Style.FILL
    }

    private val centerButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F7")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D0D0D4")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#808084")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var innerRadius = 0f

    private var lastTouchAngle = 0.0
    private var accumulatedAngle = 0.0
    private var totalMovementAngle = 0.0
    private var isTouchingWheel = false
    private var isTouchingCenter = false
    private var touchDownTime = 0L

    private val angleStepThreshold = Math.toRadians(14.0)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val size = min(w, h)
        outerRadius = (size / 2f) * 0.94f
        innerRadius = outerRadius * 0.38f
        textPaint.textSize = outerRadius * 0.09f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw outer click wheel disc
        canvas.drawCircle(centerX, centerY, outerRadius, wheelPaint)
        canvas.drawCircle(centerX, centerY, outerRadius, borderPaint)

        // Draw center button
        canvas.drawCircle(centerX, centerY, innerRadius, centerButtonPaint)
        canvas.drawCircle(centerX, centerY, innerRadius, borderPaint)

        // Draw wheel button labels
        val textDist = (outerRadius + innerRadius) / 2f
        val fontMetrics = textPaint.fontMetrics
        val textOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f

        // Top: MENU
        canvas.drawText("MENU", centerX, centerY - textDist - textOffset, textPaint)

        // Bottom: >||
        canvas.drawText("▶❚❚", centerX, centerY + textDist - textOffset, textPaint)

        // Left: |<<
        canvas.drawText("❚◀◀", centerX - textDist, centerY - textOffset, textPaint)

        // Right: >>|
        canvas.drawText("▶▶❚", centerX + textDist, centerY - textOffset, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt((dx * dx + dy * dy).toDouble())

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = System.currentTimeMillis()
                totalMovementAngle = 0.0
                accumulatedAngle = 0.0

                if (distance <= innerRadius) {
                    isTouchingCenter = true
                    isTouchingWheel = false
                } else if (distance <= outerRadius) {
                    isTouchingWheel = true
                    isTouchingCenter = false
                    lastTouchAngle = atan2(dy.toDouble(), dx.toDouble())
                } else {
                    isTouchingCenter = false
                    isTouchingWheel = false
                    return false
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTouchingWheel) {
                    val currentAngle = atan2(dy.toDouble(), dx.toDouble())
                    var delta = currentAngle - lastTouchAngle

                    // Normalize angle jump around PI / -PI wrap
                    if (delta > Math.PI) delta -= 2 * Math.PI
                    if (delta < -Math.PI) delta += 2 * Math.PI

                    accumulatedAngle += delta
                    totalMovementAngle += abs(delta)
                    lastTouchAngle = currentAngle

                    if (abs(accumulatedAngle) >= angleStepThreshold) {
                        val steps = (accumulatedAngle / angleStepThreshold).toInt()
                        accumulatedAngle -= steps * angleStepThreshold
                        listener?.onWheelRotate(steps)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - touchDownTime
                val isTap = elapsed < 400L && totalMovementAngle < Math.toRadians(12.0)

                if (isTouchingCenter && isTap && distance <= innerRadius) {
                    listener?.onSelectClick()
                } else if (isTouchingWheel && isTap && distance <= outerRadius && distance >= innerRadius) {
                    val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    when {
                        deg in -135.0..-45.0 -> listener?.onMenuClick()
                        deg in 45.0..135.0 -> listener?.onPlayPauseClick()
                        deg in -45.0..45.0 -> listener?.onNextClick()
                        else -> listener?.onPreviousClick()
                    }
                }

                isTouchingWheel = false
                isTouchingCenter = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isTouchingWheel = false
                isTouchingCenter = false
                return false
            }
        }
        return super.onTouchEvent(event)
    }
}
