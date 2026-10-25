package com.ipodnano.player.presentation.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class IpodScreenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }


    interface TouchInteractionListener {
        fun onItemClick(index: Int)
        fun onBackClick()
        fun onPlayPauseClick()
        fun onNextClick()
        fun onPreviousClick()
        fun onScrub(fraction: Float)
        fun onVolumeChange(percent: Int)
        fun onRepeatClick() {}
    }

    var touchListener: TouchInteractionListener? = null

    private enum class Mode { MENU, NOW_PLAYING }
    private var currentMode = Mode.MENU

    private var menuTitle: String = "Nano Player"
    private var menuItems: List<String> = emptyList()
    private var selectedIndex: Int = 0
    private var canGoBack: Boolean = false
    private var isDarkMode: Boolean = false
    private var textScaleFactor: Float = 1.0f
    private var backButtonTitle: String = "Library"
    private var trackTitle: String = "Feel Good, Inc."
    private var artistName: String = "Gorillaz"
    private var albumName: String = "Demon Days"
    private var albumArtUri: String? = null
    private var isPlaying: Boolean = false
    private var progressFraction: Float = 0.32f
    private var positionText: String = "0:32"
    private var durationText: String = "-3:11"
    private var trackIndexText: String = "1 of 1"
    private var volumePercent: Int = 72
    private var batteryPercent: Int = 49
    private var repeatMode: String = "Off"

    private var menuScrollY: Float = 0f
    private var maxMenuScroll: Float = 0f
    private var touchStartY: Float = 0f
    private var isScrolling: Float = 0f

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val backButtonRect = RectF()
    private val playButtonRect = RectF()
    private val prevButtonRect = RectF()
    private val nextButtonRect = RectF()
    private val scrubberRect = RectF()
    private val volumeRect = RectF()
    private val repeatButtonRect = RectF()
    private var isDraggingVolume = false
    private var isDraggingScrubber = false

    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val headerPaint = Paint().apply { style = Paint.Style.FILL }
    private val separatorPaint = Paint().apply { strokeWidth = 1.2f; style = Paint.Style.STROKE }

    private val regularTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        isSubpixelText = true
        isDither = true
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val mediumTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        isSubpixelText = true
        isDither = true
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

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
        strokeWidth = 1.2f
    }

    private val bitmapCache = mutableMapOf<String, Bitmap?>()

    private fun loadArtworkBitmap(uriStr: String?): Bitmap? {
        if (uriStr.isNullOrBlank()) return null
        if (bitmapCache.containsKey(uriStr)) return bitmapCache[uriStr]
        val bmp = try {
            val uri = Uri.parse(uriStr)
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val fd = pfd.fileDescriptor
                val b = BitmapFactory.decodeFileDescriptor(fd)
                pfd.close()
                b
            } else {
                null
            }
        } catch (_: Exception) {
            try {
                if (uriStr.startsWith("file://")) {
                    BitmapFactory.decodeFile(uriStr.removePrefix("file://"))
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
        bitmapCache[uriStr] = bmp
        return bmp
    }

    private fun ellipsizeText(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }

    fun showMenu(
        title: String,
        items: List<String>,
        selected: Int,
        battery: Int,
        backEnabled: Boolean,
        darkMode: Boolean,
        textScale: Float = 1.0f,
        backButtonTitle: String = "Library"
    ) {
        currentMode = Mode.MENU
        menuTitle = title
        menuItems = items
        selectedIndex = selected
        batteryPercent = battery
        canGoBack = backEnabled
        isDarkMode = darkMode
        textScaleFactor = textScale
        this.backButtonTitle = backButtonTitle
        menuScrollY = 0f
        invalidate()
    }

    fun showNowPlaying(
        title: String,
        artist: String,
        album: String,
        artUri: String?,
        playing: Boolean,
        progress: Float,
        posText: String,
        durText: String,
        indexText: String,
        volume: Int,
        battery: Int,
        darkMode: Boolean,
        repeat: String = "Off",
        textScale: Float = 1.0f
    ) {
        currentMode = Mode.NOW_PLAYING
        trackTitle = title
        artistName = artist
        albumName = album
        albumArtUri = artUri
        isPlaying = playing
        progressFraction = progress
        positionText = posText
        durationText = durText
        trackIndexText = indexText
        volumePercent = volume
        batteryPercent = battery
        canGoBack = true
        isDarkMode = darkMode
        repeatMode = repeat
        textScaleFactor = textScale
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val scale = min(w / 320f, h / 480f).coerceAtLeast(0.7f)

        val bgColor = Color.parseColor("#FFFFFF")
        val headerColor1 = Color.parseColor("#F8F9FA")
        val headerColor2 = Color.parseColor("#E5E7EB")
        val sepColor = Color.parseColor("#D1D5DB")
        val textColor = Color.parseColor("#111827")
        val subTextColor = Color.parseColor("#4B5563")
        val lightSubTextColor = Color.parseColor("#6B7280")
        val barBgColor = Color.parseColor("#E2E8F0")
        val buttonBgColor = Color.parseColor("#F1F5F9")
        val artColor = Color.parseColor("#1E3A5F")

        bgPaint.color = bgColor
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val headerH = max(34f, max(42f * scale, h * 0.075f))
        headerPaint.shader = LinearGradient(0f, 0f, 0f, headerH, headerColor1, headerColor2, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, headerH, headerPaint)
        separatorPaint.color = sepColor
        canvas.drawLine(0f, headerH, w, headerH, separatorPaint)

        if (canGoBack) {
            val backWidth = 130f * scale
            backButtonRect.set(0f, 0f, backWidth, headerH * 1.5f)
            mediumTextPaint.color = Color.parseColor("#007AFF")
            mediumTextPaint.textSize = headerH * 0.38f
            mediumTextPaint.textAlign = Paint.Align.LEFT
            val fmBack = mediumTextPaint.fontMetrics
            val backY = headerH / 2f - (fmBack.ascent + fmBack.descent) / 2f
            canvas.drawText("‹ $backButtonTitle", 12f * scale, backY, mediumTextPaint)
        }
        semiBoldTextPaint.color = textColor
        semiBoldTextPaint.textAlign = Paint.Align.CENTER
        semiBoldTextPaint.textSize = headerH * 0.32f
        val fmTitle = semiBoldTextPaint.fontMetrics
        val titleY = headerH / 2f - (fmTitle.ascent + fmTitle.descent) / 2f
        canvas.drawText(if (currentMode == Mode.MENU) menuTitle else "Now Playing", w / 2f, titleY, semiBoldTextPaint)

        val clockStr = timeFormat.format(Date())
        mediumTextPaint.color = textColor
        mediumTextPaint.textSize = headerH * 0.30f
        mediumTextPaint.textAlign = Paint.Align.RIGHT
        val fmClock = mediumTextPaint.fontMetrics
        val clockY = headerH / 2f - (fmClock.ascent + fmClock.descent) / 2f
        val batteryRight = w - 10f * scale
        val batteryWidth = 22f * scale
        val batteryHeight = 11f * scale
        val clockRight = batteryRight - batteryWidth - 6f * scale

        canvas.drawText(clockStr, clockRight, clockY, mediumTextPaint)
        drawBattery(canvas, batteryRight - batteryWidth / 2f, headerH * 0.5f, batteryWidth, batteryHeight, textColor)

        val bodyTop = headerH + 1f
        val bodyH = h - bodyTop

        if (currentMode == Mode.MENU) {
            if (menuTitle == "About") {
                drawAboutBlock(canvas, 0f, bodyTop, w, bodyH, scale, textColor)
            } else {
                drawMenuList(canvas, 0f, bodyTop, w, bodyH, scale, textColor, sepColor)
            }
        } else {
            drawCenteredNowPlaying(canvas, 0f, bodyTop, w, bodyH, scale, textColor, subTextColor, lightSubTextColor, barBgColor, buttonBgColor, artColor)
        }
    }

    private fun drawBattery(canvas: Canvas, cx: Float, cy: Float, bw: Float, bh: Float, color: Int) {
        val rect = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.4f }
        canvas.drawRect(rect, p)
        canvas.drawRect(cx + bw / 2f, cy - bh / 4f, cx + bw / 2f + 2f, cy + bh / 4f, p)

        val fillW = (bw - 3f) * (batteryPercent.coerceIn(0, 100) / 100f)
        if (fillW > 0f) {
            val fillPaint = Paint().apply { this.color = Color.parseColor("#16A34A"); style = Paint.Style.FILL }
            canvas.drawRect(rect.left + 1.5f, rect.top + 1.5f, rect.left + 1.5f + fillW, rect.bottom - 1.5f, fillPaint)
        }
    }

    private fun drawAboutBlock(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        textColor: Int
    ) {
        val marginX = 28f * scale
        val startY = top + 36f * scale

        semiBoldTextPaint.color = textColor
        semiBoldTextPaint.textSize = (18f * scale) / 1.618f
        semiBoldTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Nano Player", marginX, startY, semiBoldTextPaint)

        mediumTextPaint.color = Color.parseColor("#007AFF")
        mediumTextPaint.textSize = (11.5f * scale) / 1.618f
        mediumTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("v2.0 • Music-Exclusive Edition", marginX, startY + 20f * scale, mediumTextPaint)
        val dividerY = startY + 34f * scale
        separatorPaint.color = Color.parseColor("#E5E7EB")
        separatorPaint.strokeWidth = 1f
        canvas.drawLine(marginX, dividerY, width - marginX, dividerY, separatorPaint)

        regularTextPaint.color = Color.parseColor("#1F2937")
        regularTextPaint.textSize = (11.5f * scale) / 1.618f
        regularTextPaint.textAlign = Paint.Align.LEFT
        val paragraphs = listOf(
            "Developed with precision by Amauris Willmore.",
            "",
            "A high-fidelity digital audio player built with native Kotlin and Clean Architecture. Features local MediaStore indexing, custom Canvas Aqua UI rendering, and a robust DAP audio pipeline engineered for gapless playback, audio focus, and zero layout shift."
        )

        var blockY = dividerY + 24f * scale
        val normalLineHeight = 17f * scale
        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                blockY += normalLineHeight * 0.8f
                continue
            }
            val words = paragraph.split(" ")
            var currentLine = ""
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (regularTextPaint.measureText(testLine) > (width - marginX * 2f)) {
                    canvas.drawText(currentLine, marginX, blockY, regularTextPaint)
                    blockY += normalLineHeight
                    currentLine = word
                } else {
                    currentLine = testLine
                }
            }
            if (currentLine.isNotEmpty()) {
                canvas.drawText(currentLine, marginX, blockY, regularTextPaint)
                blockY += normalLineHeight
            }
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
        val itemHeight = 48f * scale
        val totalContentHeight = menuItems.size * itemHeight
        maxMenuScroll = max(0f, totalContentHeight - height)
        menuScrollY = menuScrollY.coerceIn(0f, maxMenuScroll)
        mediumTextPaint.textSize = 19f * scale * textScaleFactor
        mediumTextPaint.textAlign = Paint.Align.LEFT
        separatorPaint.color = sepColor

        canvas.save()
        canvas.clipRect(left, top, left + width, top + height)

        for (i in menuItems.indices) {
            val itemY = top + (i * itemHeight) - menuScrollY
            if (itemY + itemHeight < top || itemY > top + height) continue

            if (i == selectedIndex) {
                val highlightPaint = Paint().apply { color = Color.parseColor("#3B82F6"); style = Paint.Style.FILL }
                canvas.drawRect(left, itemY, left + width, itemY + itemHeight, highlightPaint)
                mediumTextPaint.color = Color.WHITE
            } else {
                mediumTextPaint.color = textColor
            }

            canvas.drawText(menuItems[i], left + 20f * scale, itemY + itemHeight * 0.62f, mediumTextPaint)
            regularTextPaint.color = if (i == selectedIndex) Color.WHITE else Color.parseColor("#94A3B8")
            canvas.drawText("›", left + width - 28f * scale, itemY + itemHeight * 0.62f, regularTextPaint)
            canvas.drawLine(left + 16f * scale, itemY + itemHeight, left + width - 16f * scale, itemY + itemHeight, separatorPaint)
        }
        canvas.restore()
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
        val marginX = 24f * scale
        val artSize = min(width * 0.40f, height * 0.35f).coerceIn(110f * scale, 150f * scale)

        // Perfectly centered vertical rhythm
        val totalContentHeight = artSize + 16f * scale + 60f * scale + 24f * scale + 56f * scale + 48f * scale
        val startY = top + max(12f * scale, (height - totalContentHeight) / 2f)

        mediumTextPaint.color = lightSubTextColor
        mediumTextPaint.textSize = 11f * scale
        mediumTextPaint.textAlign = Paint.Align.LEFT
        val indexY = startY
        canvas.drawText(trackIndexText, marginX, indexY, mediumTextPaint)

        val artTop = indexY + 6f * scale
        val artRect = RectF(marginX, artTop, marginX + artSize, artTop + artSize)

        val artworkBmp = loadArtworkBitmap(albumArtUri)
        if (artworkBmp != null) {
            canvas.drawBitmap(artworkBmp, null, artRect, Paint(Paint.ANTI_ALIAS_FLAG))
            artBorderPaint.color = Color.parseColor("#374151")
            canvas.drawRect(artRect, artBorderPaint)
        } else {
            // Dummy Artwork: Yellow Musical Note on Pure Black Background (#FACC15 on #000000)
            artBgPaint.color = Color.parseColor("#000000")
            canvas.drawRect(artRect, artBgPaint)

            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FACC15") // JavaScript yellow HEX
                textSize = artRect.width() * 0.45f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val xPos = artRect.centerX()
            val yPos = artRect.centerY() - ((notePaint.descent() + notePaint.ascent()) / 2f)
            canvas.drawText("♫", xPos, yPos, notePaint)

            artBorderPaint.color = Color.parseColor("#374151")
            canvas.drawRect(artRect, artBorderPaint)
        }

        val metaLeft = artRect.right + 16f * scale
        val metaY = artTop + artSize * 0.28f

        semiBoldTextPaint.color = textColor
        semiBoldTextPaint.textSize = 18f * scale * textScaleFactor
        semiBoldTextPaint.textAlign = Paint.Align.LEFT
        val maxMetaWidth = width - marginX * 2f - artSize - 16f * scale
        canvas.drawText(ellipsizeText(semiBoldTextPaint, trackTitle, maxMetaWidth), metaLeft, metaY, semiBoldTextPaint)

        regularTextPaint.color = subTextColor
        regularTextPaint.textSize = 14f * scale * textScaleFactor
        regularTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(ellipsizeText(regularTextPaint, artistName, maxMetaWidth), metaLeft, metaY + 24f * scale, regularTextPaint)

        regularTextPaint.color = lightSubTextColor
        regularTextPaint.textSize = 13f * scale * textScaleFactor
        canvas.drawText(ellipsizeText(regularTextPaint, albumName, maxMetaWidth), metaLeft, metaY + 44f * scale, regularTextPaint)

        // Scrubber Bar
        val scrubY = artRect.bottom + 18f * scale
        scrubberRect.set(marginX, scrubY, width - marginX, scrubY + 9f * scale)
        barBgPaint.color = barBgColor
        canvas.drawRoundRect(scrubberRect, 4.5f * scale, 4.5f * scale, barBgPaint)

        val fillW = scrubberRect.width() * progressFraction.coerceIn(0f, 1f)
        if (fillW > 0f) {
            barFillPaint.shader = LinearGradient(
                scrubberRect.left, scrubberRect.top, scrubberRect.left, scrubberRect.bottom,
                Color.parseColor("#3B82F6"), Color.parseColor("#1D4ED8"),
                Shader.TileMode.CLAMP
            )
            val fillRect = RectF(scrubberRect.left, scrubberRect.top, scrubberRect.left + fillW, scrubberRect.bottom)
            canvas.drawRoundRect(fillRect, 4.5f * scale, 4.5f * scale, barFillPaint)

            val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawCircle(fillRect.right, scrubberRect.centerY(), 6f * scale, thumbPaint)
        }

        mediumTextPaint.color = subTextColor
        mediumTextPaint.textSize = (11f * scale) / 1.618f
        mediumTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(positionText, scrubberRect.left, scrubY + 22f * scale, mediumTextPaint)
        mediumTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(durationText, scrubberRect.right, scrubY + 22f * scale, mediumTextPaint)

        // Transport Controls
        val btnCenterY = scrubY + 54f * scale
        val btnRadius = 22f * scale
        buttonPaint.color = buttonBgColor

        prevButtonRect.set(width * 0.22f - btnRadius, btnCenterY - btnRadius, width * 0.22f + btnRadius, btnCenterY + btnRadius)
        canvas.drawCircle(prevButtonRect.centerX(), btnCenterY, btnRadius, buttonPaint)
        drawRemixIconGlyph(canvas, prevButtonRect.centerX(), btnCenterY, "prev", textColor, scale)

        val playRadius = 30f * scale
        val primaryBtnPaint = Paint(buttonPaint).apply { color = Color.parseColor("#2563EB") }
        playButtonRect.set(width * 0.5f - playRadius, btnCenterY - playRadius, width * 0.5f + playRadius, btnCenterY + playRadius)
        canvas.drawCircle(playButtonRect.centerX(), btnCenterY, playRadius, primaryBtnPaint)
        drawRemixIconGlyph(canvas, playButtonRect.centerX(), btnCenterY, if (isPlaying) "pause" else "play", Color.WHITE, scale * 1.1f)

        nextButtonRect.set(width * 0.78f - btnRadius, btnCenterY - btnRadius, width * 0.78f + btnRadius, btnCenterY + btnRadius)
        canvas.drawCircle(nextButtonRect.centerX(), btnCenterY, btnRadius, buttonPaint)
        drawRemixIconGlyph(canvas, nextButtonRect.centerX(), btnCenterY, "next", textColor, scale)

        // Volume & Repeat Controls
        val volY = btnCenterY + 46f * scale
        val repeatWidth = 46f * scale
        val repeatHeight = 24f * scale
        repeatButtonRect.set(width - marginX - repeatWidth, volY - repeatHeight / 2f, width - marginX, volY + repeatHeight / 2f)

        volumeRect.set(marginX, volY - 3.5f * scale, repeatButtonRect.left - 16f * scale, volY + 3.5f * scale)
        barBgPaint.color = barBgColor
        canvas.drawRoundRect(volumeRect, 3.5f * scale, 3.5f * scale, barBgPaint)

        val volFillW = volumeRect.width() * (volumePercent.coerceIn(0, 100) / 100f)
        if (volFillW > 0f) {
            val volFillPaint = Paint().apply { color = Color.parseColor("#3B82F6"); style = Paint.Style.FILL }
            val volFillRect = RectF(volumeRect.left, volumeRect.top, volumeRect.left + volFillW, volumeRect.bottom)
            canvas.drawRoundRect(volFillRect, 3.5f * scale, 3.5f * scale, volFillPaint)
            val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawCircle(volFillRect.right, volumeRect.centerY(), 5.5f * scale, thumbPaint)
        }

        buttonPaint.color = buttonBgColor
        canvas.drawRoundRect(repeatButtonRect, repeatHeight / 2f, repeatHeight / 2f, buttonPaint)
        val iconType = when (repeatMode) {
            "All" -> "repeat_all"
            "One" -> "repeat_one"
            "Shuffle" -> "shuffle"
            else -> "repeat_off"
        }
        drawRemixIconGlyph(canvas, repeatButtonRect.centerX(), repeatButtonRect.centerY(), iconType, textColor, scale * 0.9f)
    }

    private fun drawRemixIconGlyph(canvas: Canvas, cx: Float, cy: Float, type: String, color: Int, scale: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val path = Path()
        when (type) {
            "play" -> {
                path.moveTo(cx - 4f * scale, cy - 7f * scale)
                path.lineTo(cx + 7f * scale, cy)
                path.lineTo(cx - 4f * scale, cy + 7f * scale)
                path.close()
                canvas.drawPath(path, p)
            }
            "pause" -> {
                canvas.drawRect(cx - 5f * scale, cy - 6f * scale, cx - 1.5f * scale, cy + 6f * scale, p)
                canvas.drawRect(cx + 1.5f * scale, cy - 6f * scale, cx + 5f * scale, cy + 6f * scale, p)
            }
            "prev" -> {
                canvas.drawRect(cx - 6f * scale, cy - 6f * scale, cx - 3.5f * scale, cy + 6f * scale, p)
                path.moveTo(cx + 5f * scale, cy - 6f * scale)
                path.lineTo(cx - 2.5f * scale, cy)
                path.lineTo(cx + 5f * scale, cy + 6f * scale)
                path.close()
                canvas.drawPath(path, p)
            }
            "next" -> {
                canvas.drawRect(cx + 3.5f * scale, cy - 6f * scale, cx + 6f * scale, cy + 6f * scale, p)
                path.moveTo(cx - 5f * scale, cy - 6f * scale)
                path.lineTo(cx + 2.5f * scale, cy)
                path.lineTo(cx - 5f * scale, cy + 6f * scale)
                path.close()
                canvas.drawPath(path, p)
            }
            "repeat_all" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.5f * scale
                canvas.drawCircle(cx, cy, 5f * scale, p)
            }
            "repeat_one" -> {
                p.style = Paint.Style.FILL
                mediumTextPaint.textSize = 10f * scale
                mediumTextPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("1", cx, cy + 3.5f * scale, mediumTextPaint)
            }
            "shuffle" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.5f * scale
                canvas.drawLine(cx - 5f * scale, cy - 4f * scale, cx + 5f * scale, cy + 4f * scale, p)
                canvas.drawLine(cx - 5f * scale, cy + 4f * scale, cx + 5f * scale, cy - 4f * scale, p)
            }
            "repeat_off" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.5f * scale
                canvas.drawCircle(cx, cy, 5f * scale, p)
                canvas.drawLine(cx - 6f * scale, cy - 6f * scale, cx + 6f * scale, cy + 6f * scale, p)
            }
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                touchStartY = y
                isScrolling = 0f

                if (canGoBack && backButtonRect.contains(x, y)) {
                    touchListener?.onBackClick()
                    return true
                }

                if (currentMode == Mode.MENU) {
                    return true
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
                    if (repeatButtonRect.contains(x, y)) {
                        touchListener?.onRepeatClick()
                        return true
                    }
                    if (volumeRect.contains(x, y) || (y >= volumeRect.top - 15f && y <= volumeRect.bottom + 15f && x >= volumeRect.left && x <= volumeRect.right)) {
                        isDraggingVolume = true
                        val fraction = ((x - volumeRect.left) / volumeRect.width()).coerceIn(0f, 1f)
                        touchListener?.onVolumeChange((fraction * 100).toInt())
                        return true
                    }
                    if (scrubberRect.contains(x, y) || (y >= scrubberRect.top - 12f && y <= scrubberRect.bottom + 12f && x >= scrubberRect.left && x <= scrubberRect.right)) {
                        isDraggingScrubber = true
                        val fraction = ((x - scrubberRect.left) / scrubberRect.width()).coerceIn(0f, 1f)
                        touchListener?.onScrub(fraction)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentMode == Mode.MENU) {
                    val deltaY = touchStartY - y
                    if (abs(deltaY) > 4f) {
                        isScrolling += abs(deltaY)
                    }
                    menuScrollY = (menuScrollY + deltaY).coerceIn(0f, maxMenuScroll)
                    touchStartY = y
                    invalidate()
                    return true
                }
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
                if (currentMode == Mode.MENU && isScrolling < 10f) {
                    val headerH = max(34f, height * 0.075f)
                    val scale = min(width / 320f, height / 480f).coerceAtLeast(0.7f)
                    val itemH = 48f * scale
                    if (y > headerH) {
                        val adjustedY = y - headerH + menuScrollY
                        val index = (adjustedY / itemH).toInt()
                        if (index in menuItems.indices) {
                            selectedIndex = index
                            touchListener?.onItemClick(index)
                            invalidate()
                            return true
                        }
                    }
                }
                isDraggingVolume = false
                isDraggingScrubber = false
                isScrolling = 0f
            }
        }
        return super.onTouchEvent(event)
    }
}
