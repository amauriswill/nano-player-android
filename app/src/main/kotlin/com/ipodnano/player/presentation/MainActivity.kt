package com.ipodnano.player.presentation

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.ipodnano.player.R
import kotlin.math.atan2

data class SongItem(val title: String, val artist: String, val assetPath: String)

class MainActivity : AppCompatActivity() {

    private lateinit var lvMenu: ListView
    private lateinit var ivCover: ImageView
    private lateinit var tvNowPlayingTitle: TextView
    private lateinit var tvNowPlayingArtist: TextView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvBattery: TextView

    private var selectedIndex = 0
    private var isInSongsSubmenu = false
    private var isPlaying = false
    private var lastWheelAngle = 0.0
    private var toneGen: ToneGenerator? = null
    private var mediaPlayer: MediaPlayer? = null

    private val songsList = listOf(
        SongItem("Feel Good Inc.", "Gorillaz", "covers/demondays.jpg"),
        SongItem("1901", "Phoenix", "covers/phoenix.jpg"),
        SongItem("Viva La Vida", "Coldplay", "covers/coldplay.jpg"),
        SongItem("Harder, Better, Faster", "Daft Punk", "covers/daftpunk.jpg"),
        SongItem("Mr. Brightside", "The Killers", "covers/thekillers.jpg")
    )

    private val mainMenuItems = listOf(
        "Música",
        "Listas de reproducción",
        "Artistas",
        "Álbumes",
        "Configuración",
        "Ahora suena"
    )

    private var currentDisplayList: List<String> = mainMenuItems
    private lateinit var menuAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 30)
        } catch (_: Exception) {}

        lvMenu = findViewById(R.id.lv_menu)
        ivCover = findViewById(R.id.iv_cover)
        tvNowPlayingTitle = findViewById(R.id.tv_now_playing_title)
        tvNowPlayingArtist = findViewById(R.id.tv_now_playing_artist)
        tvHeaderTitle = findViewById(R.id.tv_header_title)
        tvBattery = findViewById(R.id.tv_battery)

        updateBatteryStatus()
        setupMenuList()
        setupClickWheel()
    }

    private fun updateBatteryStatus() {
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            tvBattery.text = "$batLevel% 🔋"
        } catch (_: Exception) {
            tvBattery.text = "100% 🔋"
        }
    }

    private fun setupMenuList() {
        menuAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, currentDisplayList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.textSize = 14f
                view.setPadding(20, 14, 20, 14)

                if (position == selectedIndex) {
                    view.setBackgroundColor(Color.parseColor("#3478F6"))
                    view.setTextColor(Color.WHITE)
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                    view.setTextColor(Color.BLACK)
                }
                return view
            }
        }
        lvMenu.adapter = menuAdapter

        lvMenu.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            menuAdapter.notifyDataSetChanged()
            handleCenterButtonPress()
        }
    }

    private fun setupClickWheel() {
        val wheel = findViewById<RelativeLayout>(R.id.ipod_wheel)
        val centerBtn = findViewById<View>(R.id.ipod_center_btn)
        val btnMenu = findViewById<View>(R.id.btn_wheel_menu)
        val btnNext = findViewById<View>(R.id.btn_wheel_next)
        val btnPrev = findViewById<View>(R.id.btn_wheel_prev)
        val btnPlay = findViewById<View>(R.id.btn_wheel_play)

        wheel.setOnTouchListener { v, event ->
            val centerX = v.width / 2f
            val centerY = v.height / 2f
            val x = event.x - centerX
            val y = event.y - centerY

            val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastWheelAngle = angle
                }
                MotionEvent.ACTION_MOVE -> {
                    var delta = angle - lastWheelAngle
                    if (delta > 180) delta -= 360
                    if (delta < -180) delta += 360

                    if (delta > 25) {
                        moveSelection(1)
                        lastWheelAngle = angle
                    } else if (delta < -25) {
                        moveSelection(-1)
                        lastWheelAngle = angle
                    }
                }
            }
            true
        }

        centerBtn.setOnClickListener { handleCenterButtonPress() }

        btnMenu.setOnClickListener {
            if (isInSongsSubmenu) {
                isInSongsSubmenu = false
                tvHeaderTitle.text = "Now Playing"
                currentDisplayList = mainMenuItems
                selectedIndex = 0
                setupMenuList()
                playClickSound()
            }
        }

        btnNext.setOnClickListener { moveSelection(1) }
        btnPrev.setOnClickListener { moveSelection(-1) }

        btnPlay.setOnClickListener {
            togglePlayPause()
        }
    }

    private fun moveSelection(direction: Int) {
        val newIndex = (selectedIndex + direction).coerceIn(0, currentDisplayList.size - 1)
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            menuAdapter.notifyDataSetChanged()
            lvMenu.smoothScrollToPosition(selectedIndex)
            playClickSound()
        }
    }

    private fun handleCenterButtonPress() {
        playClickSound()
        if (!isInSongsSubmenu) {
            when (selectedIndex) {
                0 -> {
                    // Música / Canciones
                    isInSongsSubmenu = true
                    tvHeaderTitle.text = "Música"
                    currentDisplayList = songsList.map { it.title }
                    selectedIndex = 0
                    setupMenuList()
                }
                else -> {
                    Toast.init(this)
                    Toast.makeText(this, "Opción: ${mainMenuItems[selectedIndex]}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val song = songsList[selectedIndex]
            playSong(song)
        }
    }

    private fun playSong(song: SongItem) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                // Play synth fallback or resource
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                // Use synthetic tone or asset if available
                prepare()
                start()
            }
            isPlaying = true
            tvNowPlayingTitle.text = "▶ ${song.title}"
            tvNowPlayingArtist.text = song.artist
        } catch (_: Exception) {
            isPlaying = !isPlaying
            val stateIcon = if (isPlaying) "▶ " else "⏸ "
            tvNowPlayingTitle.text = "$stateIcon${song.title}"
            tvNowPlayingArtist.text = song.artist
        }
        playClickSound()
    }

    private fun togglePlayPause() {
        isPlaying = !isPlaying
        val stateIcon = if (isPlaying) "▶ " else "⏸ "
        val currentSong = songsList[selectedIndex.coerceIn(0, songsList.size - 1)]
        tvNowPlayingTitle.text = "$stateIcon${currentSong.title}"
        playClickSound()
    }

    private fun playClickSound() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 15)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer?.release()
            toneGen?.release()
        } catch (_: Exception) {}
    }
}
