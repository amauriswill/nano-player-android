package com.ipodnano.player.presentation.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class IpodScreenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface TouchInteractionListener {
        fun onItemClick(index: Int)
        fun onBackClick()
        fun onPlayPauseClick()
        fun onNextClick()
        fun onPreviousClick()
        fun onScrub(fraction: Float)
        fun onVolumeChange(percent: Int)
    }

    var touchListener: TouchInteractionListener? = null

    private enum class Mode { MENU, NOW_PLAYING }
    private var currentMode = Mode.MENU

    private var menuTitle: String = "Nano Player"
    private var menuItems: List<String> = emptyList()
    private var selectedIndex: Int = 0
    private var canGoBack: Boolean = false
    private var isDarkMode: Boolean = false

    private var trackTitle: String = "Feel Good, Inc."
    private var artistName: String = "Gorillaz"
    private var albumName: String = "Demon Days"
    private var isPlaying: Boolean = false
    private var progressFraction: Float = 0.72f
    private var positionText: String = "3:41"
    private var durationText: String = "-1:20"
    private var trackIndexText: String = "6 of 15"
    private var volumePercent: Int = 70
    private var batteryPercent: Int = 90

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    // Touch hit bounds
    private val backButtonRect = RectF()
    private val playButtonRect = RectF()
    private val prevButtonRect = RectF()
    private val nextButtonRect = RectF()
    private val scrubberRect = RectF()
    private val volumeRect = RectF()
    private var isDraggingVolume = false
    private var isDraggingScrubber = false

    // Paints: Refined Apple SF Pro Style Typographic Weights
    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val headerPaint = Paint().apply { style = Paint.Style.FILL }
    private val separatorPaint = Paint().apply { strokeWidth = 1.5f; style = Paint.Style.STROKE }

    // Delicate Regular (Body, Artist, Album, Menu labels)
    private val regularTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        isSubpixelText = true
        isDither = true
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    // Medium (Counter, timecodes, clock, menu item labels)
    private val mediumTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        isSubpixelText = true
        isDither = true
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // Semi-Bold (Header title, song title)
    private val semiBoldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        isSubpixelText = true
        isDither = true
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val barBgPaint = Paint().apply { style = Paint.Style.FILL }
    private val barFillPaint = Paint().apply { style = Paint.Style.FILL }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val artBgPaint = Paint().apply { style = Paint.Style.FILL }
    private val artBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    fun showMenu(
        title: String,
        items: List<String>,
        selected: Int,
        battery: Int,
        backEnabled: Boolean,
        darkMode: Boolean
    ) {
        currentMode = Mode.MENU
        menuTitle = title
        menuItems = items
        selectedIndex = selected
        batteryPercent = battery
        canGoBack = backEnabled
        isDarkMode = darkMode
        invalidate()
    }

    fun showNowPlaying(
        title: String,
        artist: String,
        album: String,
        playing: Boolean,
        progress: Float,
        posText: String,
        durText: String,
        indexText: String,
        volume: Int,
        battery: Int,
        darkMode: Boolean
    ) {
        currentMode = Mode.NOW_PLAYING
        trackTitle = title
        artistName = artist
        albumName = album
        isPlaying = playing
        progressFraction = progress
        positionText = posText
        durationText = durText
        trackIndexText = indexText
        volumePercent = volume
        batteryPercent = battery
        canGoBack = true
        isDarkMode = darkMode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val scale = min(w / 320f, h / 480f).coerceAtLeast(0.7f)

        // Delicate Palette
        val bgColor = if (isDarkMode) Color.parseColor("#0F1115") else Color.parseColor("#FFFFFF")
        val headerColor1 = if (isDarkMode) Color.parseColor("#242428") else Color.parseColor("#F4F5F8")
        val headerColor2 = if (isDarkMode) Color.parseColor("#161619") else Color.parseColor("#DCDFE6")
        val sepColor = if (isDarkMode) Color.parseColor("#2A2A30") else Color.parseColor("#B0B4BE")
        val primaryTextColor = if (isDarkMode) Color.parseColor("#F4F4F5") else Color.parseColor("#09090B")
        val subTextColor = if (isDarkMode) Color.parseColor("#A1A1AA") else Color.parseColor("#52525B")
        val lightSubTextColor = if (isDarkMode) Color.parseColor("#71717A") else Color.parseColor("#71717A")
        val barBgColor = if (isDarkMode) Color.parseColor("#27272A") else Color.parseColor("#DCE0E8")
        val buttonBgColor = if (isDarkMode) Color.parseColor("#222226") else Color.parseColor("#E4E7EE")
        val artColor = if (isDarkMode) Color.parseColor("#1E293B") else Color.parseColor("#1E3A5F")

        bgPaint.color = bgColor
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Status Header
        val headerH = max(34f, max(40f * scale, h * 0.08f))
        headerPaint.shader = LinearGradient(0f, 0f, 0f, headerH, headerColor1, headerColor2, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, headerH, headerPaint)
        separatorPaint.color = sepColor
        canvas.drawLine(0f, headerH, w, headerH, separatorPaint)

        // Play indicator on top left (Blue triangle)
        if (currentMode == Mode.NOW_PLAYING && isPlaying) {
            val playPath = android.graphics.Path().apply {
                moveTo(16f * scale, headerH * 0.32f)
                lineTo(26f * scale, headerH * 0.5f)
                lineTo(16f * scale, headerH * 0.68f)
                close()
            }
            val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2563EB")
                style = Paint.Style.FILL
            }
            canvas.drawPath(playPath, playPaint)
        } else if (canGoBack) {
            val backWidth = 70f * scale
            backButtonRect.set(8f * scale, 0f, backWidth, headerH)
            mediumTextPaint.color = Color.parseColor("#2563EB")
            mediumTextPaint.textSize = headerH * 0.38f
            mediumTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("‹ Menu", 12f * scale, headerH * 0.65f, mediumTextPaint)
        }

        // Header Title: Semi-Bold
        semiBoldTextPaint.color = primaryTextColor
        semiBoldTextPaint.textAlign = Paint.Align.CENTER
        semiBoldTextPaint.textSize = headerH * 0.42f
        canvas.drawText(if (currentMode == Mode.MENU) menuTitle else "Now Playing", w / 2f, headerH * 0.65f, semiBoldTextPaint)

        // Live Clock next to Battery: Medium
        val clockStr = timeFormat.format(Date())
        mediumTextPaint.color = primaryTextColor
        mediumTextPaint.textSize = headerH * 0.30f
        mediumTextPaint.textAlign = Paint.Align.RIGHT
        val batteryRight = w - 10f * scale
        val batteryWidth = 22f * scale
        val batteryHeight = 11f * scale
        val clockRight = batteryRight - batteryWidth - 6f * scale

        canvas.drawText(clockStr, clockRight, headerH * 0.64f, mediumTextPaint)
        drawBattery(canvas, batteryRight - batteryWidth / 2f, headerH * 0.5f, batteryWidth, batteryHeight, primaryTextColor)

        val bodyTop = headerH + 1f
        val bodyH = h - bodyTop

        if (currentMode == Mode.MENU) {
            drawMenuList(canvas, 0f, bodyTop, w, bodyH, scale, primaryTextColor, sepColor)
        } else {
            drawCenteredNowPlaying(canvas, 0f, bodyTop, w, bodyH, scale, primaryTextColor, subTextColor, lightSubTextColor, barBgColor, buttonBgColor, artColor)
        }
    }

    private fun drawBattery(canvas: Canvas, cx: Float, cy: Float, bw: Float, bh: Float, color: Int) {
        val rect = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.6f }
        canvas.drawRect(rect, p)
        canvas.drawRect(cx + bw / 2f, cy - bh / 4f, cx + bw / 2f + 2.5f, cy + bh / 4f, p)

        val fillW = (bw - 3f) * (batteryPercent.coerceIn(0, 100) / 100f)
        if (fillW > 0f) {
            val fillPaint = Paint().apply { this.color = Color.parseColor("#22C55E"); style = Paint.Style.FILL }
            canvas.drawRect(rect.left + 1.5f, rect.top + 1.5f, rect.left + 1.5f + fillW, rect.bottom - 1.5f, fillPaint)
        }
    }

    private fun drawMenuList(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        textColor: Int,
        sepColor: Int
    ) {
        if (menuItems.isEmpty()) return
        val itemHeight = 52f * scale
        mediumTextPaint.textSize = 15.5f * scale
        mediumTextPaint.textAlign = Paint.Align.LEFT
        separatorPaint.color = sepColor

        for (i in menuItems.indices) {
            val itemY = top + i * itemHeight
            if (itemY > top + height) break

            mediumTextPaint.color = textColor
            canvas.drawText(menuItems[i], left + 18f * scale, itemY + itemHeight * 0.6f, mediumTextPaint)
            regularTextPaint.color = Color.parseColor("#94A3B8")
            canvas.drawText("›", left + width - 28f * scale, itemY + itemHeight * 0.6f, regularTextPaint)
            canvas.drawLine(left + 12f * scale, itemY + itemHeight, left + width - 12f * scale, itemY + itemHeight, separatorPaint)
        }
    }

    private fun drawCenteredNowPlaying(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        textColor: Int,
        subTextColor: Int,
        lightSubTextColor: Int,
        barBgColor: Int,
        buttonBgColor: Int,
        artColor: Int
    ) {
        val marginX = 22f * scale
        val artSize = min(width * 0.36f, height * 0.28f)

        // Centered vertical rhythm with breathing space
        val contentHeight = 16f * scale + artSize + 14f * scale + 10f * scale + 24f * scale + 14f * scale + 48f * scale + 24f * scale
        val startY = top + max(8f * scale, (height - contentHeight) / 2f)

        // 1. Counter: Delicate Medium ("6 of 15")
        mediumTextPaint.color = lightSubTextColor
        mediumTextPaint.textSize = 11.5f * scale
        mediumTextPaint.textAlign = Paint.Align.LEFT
        val indexY = startY + 12f * scale
        canvas.drawText(trackIndexText, marginX, indexY, mediumTextPaint)

        // 2. Square Album Cover (Left Column)
        val artTop = indexY + 6f * scale
        val artRect = RectF(marginX, artTop, marginX + artSize, artTop + artSize)

        artBgPaint.color = artColor
        canvas.drawRect(artRect, artBgPaint)
        artBorderPaint.color = if (isDarkMode) Color.parseColor("#334155") else Color.parseColor("#94A3B8")
        canvas.drawRect(artRect, artBorderPaint)

        // 4-quadrant Demon Days Cover Mockup
        val halfW = artRect.width() / 2f
        val halfH = artRect.height() / 2f
        val quadrantPaint = Paint().apply { style = Paint.Style.FILL }
        quadrantPaint.color = Color.parseColor("#0F172A")
        canvas.drawRect(artRect.left, artRect.top, artRect.left + halfW, artRect.top + halfH, quadrantPaint)
        quadrantPaint.color = Color.parseColor("#1E293B")
        canvas.drawRect(artRect.left + halfW, artRect.top, artRect.right, artRect.top + halfH, quadrantPaint)
        quadrantPaint.color = Color.parseColor("#334155")
        canvas.drawRect(artRect.left, artRect.top + halfH, artRect.left + halfW, artRect.bottom, quadrantPaint)
        quadrantPaint.color = Color.parseColor("#020617")
        canvas.drawRect(artRect.left + halfW, artRect.top + halfH, artRect.right, artRect.bottom, quadrantPaint)

        // 3. Track Details (Right Column): Delicate Hierarchy
        val metaLeft = artRect.right + 14f * scale
        val metaY = artTop + artSize * 0.32f

        // Song Title: Semi-Bold
        semiBoldTextPaint.color = textColor
        semiBoldTextPaint.textSize = 15.5f * scale
        semiBoldTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(trackTitle, metaLeft, metaY, semiBoldTextPaint)

        // Artist Name: Regular (Delicate, Non-bold)
        regularTextPaint.color = subTextColor
        regularTextPaint.textSize = 13f * scale
        regularTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(artistName, metaLeft, metaY + 22f * scale, regularTextPaint)

        // Album Name: Regular Light Subdued
        regularTextPaint.color = lightSubTextColor
        regularTextPaint.textSize = 11.5f * scale
        canvas.drawText(albumName, metaLeft, metaY + 42f * scale, regularTextPaint)

        // 4. Scrubber Progress Bar
        val scrubY = artRect.bottom + 14f * scale
        scrubberRect.set(marginX, scrubY, width - marginX, scrubY + 9f * scale)
        barBgPaint.color = barBgColor
        canvas.drawRect(scrubberRect, barBgPaint)

        val fillW = scrubberRect.width() * progressFraction.coerceIn(0f, 1f)
        if (fillW > 0f) {
            barFillPaint.shader = LinearGradient(
                scrubberRect.left, scrubberRect.top, scrubberRect.left, scrubberRect.bottom,
                Color.parseColor("#4A8DF8"), Color.parseColor("#1C5EC4"),
                Shader.TileMode.CLAMP
            )
            val fillRect = RectF(scrubberRect.left, scrubberRect.top, scrubberRect.left + fillW, scrubberRect.bottom)
            canvas.drawRect(fillRect, barFillPaint)
        }

        // Time labels: Medium Tabular
        mediumTextPaint.color = subTextColor
        mediumTextPaint.textSize = 10.5f * scale
        mediumTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(positionText, scrubberRect.left, scrubY + 20f * scale, mediumTextPaint)
        mediumTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(durationText, scrubberRect.right, scrubY + 20f * scale, mediumTextPaint)

        // 5. Breathing Room Added Before Transport Controls
        val btnCenterY = scrubY + 58f * scale // Added breathing room
        val btnRadius = 23f * scale
        buttonPaint.color = buttonBgColor

        // Previous
        prevButtonRect.set(width * 0.22f - btnRadius, btnCenterY - btnRadius, width * 0.22f + btnRadius, btnCenterY + btnRadius)
        canvas.drawCircle(prevButtonRect.centerX(), btnCenterY, btnRadius, buttonPaint)
        mediumTextPaint.textSize = 13f * scale
        mediumTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("❚◀◀", prevButtonRect.centerX(), btnCenterY + 4f * scale, mediumTextPaint)

        // Play/Pause
        val playRadius = 27f * scale
        val primaryBtnPaint = Paint(buttonPaint).apply { color = Color.parseColor("#2563EB") }
        playButtonRect.set(width * 0.5f - playRadius, btnCenterY - playRadius, width * 0.5f + playRadius, btnCenterY + playRadius)
        canvas.drawCircle(playButtonRect.centerX(), btnCenterY, playRadius, primaryBtnPaint)
        val playTextPaint = Paint(mediumTextPaint).apply { color = Color.WHITE; textSize = 15.5f * scale }
        canvas.drawText(if (isPlaying) "❚❚" else "▶", playButtonRect.centerX(), btnCenterY + 5f * scale, playTextPaint)

        // Next
        nextButtonRect.set(width * 0.78f - btnRadius, btnCenterY - btnRadius, width * 0.78f + btnRadius, btnCenterY + btnRadius)
        canvas.drawCircle(nextButtonRect.centerX(), btnCenterY, btnRadius, buttonPaint)
        canvas.drawText("▶▶❚", nextButtonRect.centerX(), btnCenterY + 4f * scale, mediumTextPaint)

        // 6. Interactive Touch-Slide Volume Bar with Thumb
        val volY = btnCenterY + 44f * scale
        val volMargin = 38f * scale
        volumeRect.set(volMargin, volY, width - volMargin, volY + 8f * scale)
        canvas.drawRoundRect(volumeRect, 4f * scale, 4f * scale, barBgPaint)

        val volFillW = volumeRect.width() * (volumePercent.coerceIn(0, 100) / 100f)
        if (volFillW > 0f) {
            val volFillPaint = Paint().apply { color = subTextColor; style = Paint.Style.FILL }
            val volFillRect = RectF(volumeRect.left, volumeRect.top, volumeRect.left + volFillW, volumeRect.bottom)
            canvas.drawRoundRect(volFillRect, 4f * scale, 4f * scale, volFillPaint)

            // Draw thumb circle for touch sliding
            val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawCircle(volFillRect.right, volumeRect.centerY(), 6.5f * scale, thumbPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                if (canGoBack && backButtonRect.contains(x, y)) {
                    touchListener?.onBackClick()
                    return true
                }

                if (currentMode == Mode.MENU) {
                    val headerH = max(34f, height * 0.08f)
                    val scale = min(width / 320f, height / 480f).coerceAtLeast(0.7f)
                    val itemH = 52f * scale
                    if (y > headerH) {
                        val index = ((y - headerH) / itemH).toInt()
                        if (index in menuItems.indices) {
                            touchListener?.onItemClick(index)
                            return true
                        }
                    }
                } else {
                    if (playButtonRect.contains(x, y)) {
                        touchListener?.onPlayPauseClick()
                        return true
                    }
                    if (prevButtonRect.contains(x, y)) {
                        touchListener?.onPreviousClick()
                        return true
                    }
                    if (nextButtonRect.contains(x, y)) {
                        touchListener?.onNextClick()
                        return true
                    }
                    // Touch Slide Volume on Down
                    if (volumeRect.contains(x, y) || (y >= volumeRect.top - 15f && y <= volumeRect.bottom + 15f && x >= volumeRect.left && x <= volumeRect.right)) {
                        isDraggingVolume = true
                        val fraction = ((x - volumeRect.left) / volumeRect.width()).coerceIn(0f, 1f)
                        touchListener?.onVolumeChange((fraction * 100).toInt())
                        return true
                    }
                    // Touch Slide Scrubber on Down
                    if (scrubberRect.contains(x, y) || (y >= scrubberRect.top - 12f && y <= scrubberRect.bottom + 12f && x >= scrubberRect.left && x <= scrubberRect.right)) {
                        isDraggingScrubber = true
                        val fraction = ((x - scrubberRect.left) / scrubberRect.width()).coerceIn(0f, 1f)
                        touchListener?.onScrub(fraction)
                        return true
                    }
                }
            }

            // Touch-Slide Dragging (Volume & Scrubber)
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingVolume) {
                    val fraction = ((x - volumeRect.left) / volumeRect.width()).coerceIn(0f, 1f)
                    touchListener?.onVolumeChange((fraction * 100).toInt())
                    return true
                }
                if (isDraggingScrubber) {
                    val fraction = ((x - scrubberRect.left) / scrubberRect.width()).coerceIn(0f, 1f)
                    touchListener?.onScrub(fraction)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingVolume = false
                isDraggingScrubber = false
            }
        }
        return super.onTouchEvent(event)
    }
}
