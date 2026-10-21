package com.ipodnano.player.presentation

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.ipodnano.player.R
import com.ipodnano.player.data.repository.MediaStoreMusicRepository
import com.ipodnano.player.domain.model.Track
import com.ipodnano.player.presentation.view.IpodScreenView

class MainActivity : Activity(), IpodScreenView.TouchInteractionListener {

    private lateinit var ipodScreen: IpodScreenView
    private lateinit var musicRepo: MediaStoreMusicRepository
    private var allTracks: List<Track> = emptyList()
    private var currentPlaylist: List<Track> = emptyList()
    private var currentTrackIndex = 0

    private val mainMenuItems = listOf(
        "Music",
        "Artists",
        "Albums",
        "Settings",
        "Now Playing"
    )

    private val settingsItems = listOf(
        "Audio Quality: Lossless",
        "SoundCheck: On",
        "EQ Preset: Rock / Bass",
        "Shuffle: Off",
        "About: Nano Player v2.0"
    )

    private enum class MenuLevel { ROOT, SUB_MENU, TRACKS_LIST, SETTINGS }
    private var menuLevel = MenuLevel.ROOT
    private var currentMenuTitle = "Nano Player"
    private var currentDisplayItems = mainMenuItems
    private var selectedIndex = 0

    private var isPlaying = false
    private var repeatModeState = "Off"
    private var mediaPlayer: MediaPlayer? = null
    private var toneGen: ToneGenerator? = null
    private var audioManager: AudioManager? = null
    private var batteryPercent = 49

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && mediaPlayer != null) {
                try {
                    updateCurrentScreenView()
                    handler.postDelayed(this, 500)
                } catch (_: Exception) {}
            }
        }
    }

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

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        checkAndRequestPermissions()

        musicRepo = MediaStoreMusicRepository(this)
        allTracks = musicRepo.getAllTracks()
        currentPlaylist = allTracks

        updateBatteryStatus()
        renderCurrentState()
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            musicRepo.reload()
            allTracks = musicRepo.getAllTracks()
            currentPlaylist = allTracks
            renderCurrentState()
        }
    }

    private fun updateBatteryStatus() {
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) {
            batteryPercent = 49
        }
    }

    private fun renderCurrentState() {
        ipodScreen = findViewById(R.id.ipod_screen)
        ipodScreen.touchListener = this

        if (menuLevel == MenuLevel.ROOT || menuLevel == MenuLevel.SUB_MENU || menuLevel == MenuLevel.TRACKS_LIST || menuLevel == MenuLevel.SETTINGS) {
            ipodScreen.showMenu(
                title = currentMenuTitle,
                items = currentDisplayItems,
                selected = selectedIndex,
                battery = batteryPercent,
                backEnabled = menuLevel != MenuLevel.ROOT,
                darkMode = false
            )
        } else {
            updateCurrentScreenView()
        }
    }

    private fun updateCurrentScreenView() {
        ipodScreen = findViewById(R.id.ipod_screen)
        ipodScreen.touchListener = this

        val track = if (currentPlaylist.isNotEmpty()) currentPlaylist[currentTrackIndex.coerceIn(0, currentPlaylist.size - 1)] else null
        val title = track?.title ?: "Feel Good, Inc."
        val artist = track?.artist ?: "Gorillaz"
        val album = track?.album ?: "Demon Days"
        val artUri = track?.albumArtUri
        val dur = track?.durationMs ?: 180000L
        val pos = mediaPlayer?.let { try { it.currentPosition.toLong() } catch (_: Exception) { 0L } } ?: 32000L

        val posText = formatTime(pos)
        val durText = "-" + formatTime(dur - pos)
        val progress = if (dur > 0) pos.toFloat() / dur.toFloat() else 0.32f
        val indexText = "${currentTrackIndex + 1} of ${currentPlaylist.size.coerceAtLeast(1)}"
        val vol = audioManager?.let { am ->
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) (cur * 100) / max else 72
        } ?: 72

        ipodScreen.showNowPlaying(
            title = title,
            artist = artist,
            album = album,
            artUri = artUri,
            playing = isPlaying,
            progress = progress,
            posText = posText,
            durText = durText,
            indexText = indexText,
            volume = vol,
            battery = batteryPercent,
            darkMode = false,
            repeat = repeatModeState
        )
    }

    private fun formatTime(ms: Long): String {
        val secs = (ms / 1000) % 60
        val mins = (ms / (1000 * 60)) % 60
        return String.format("%d:%02d", mins, secs)
    }

    override fun onItemClick(index: Int) {
        selectedIndex = index
        playClickSound()

        when (menuLevel) {
            MenuLevel.ROOT -> {
                when (index) {
                    0 -> {
                        // Music
                        menuLevel = MenuLevel.TRACKS_LIST
                        currentMenuTitle = "Music"
                        currentPlaylist = allTracks
                        currentDisplayItems = allTracks.map { "${it.title} - ${it.artist}" }
                        selectedIndex = 0
                        renderCurrentState()
                    }
                    1 -> {
                        // Artists
                        menuLevel = MenuLevel.SUB_MENU
                        currentMenuTitle = "Artists"
                        val artists = musicRepo.getArtists()
                        currentDisplayItems = if (artists.isNotEmpty()) artists else listOf("No artists found")
                        selectedIndex = 0
                        renderCurrentState()
                    }
                    2 -> {
                        // Albums
                        menuLevel = MenuLevel.SUB_MENU
                        currentMenuTitle = "Albums"
                        val albums = musicRepo.getAlbums()
                        currentDisplayItems = if (albums.isNotEmpty()) albums else listOf("No albums found")
                        selectedIndex = 0
                        renderCurrentState()
                    }
                    3 -> {
                        // Settings
                        menuLevel = MenuLevel.SETTINGS
                        currentMenuTitle = "Settings"
                        currentDisplayItems = settingsItems
                        selectedIndex = 0
                        renderCurrentState()
                    }
                    4 -> {
                        // Now Playing
                        if (currentPlaylist.isNotEmpty()) {
                            showNowPlaying()
                        } else {
                            Toast.makeText(this, "No tracks available", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> Toast.makeText(this, mainMenuItems[index], Toast.LENGTH_SHORT).show()
                }
            }
            MenuLevel.SETTINGS -> {
                Toast.makeText(this, settingsItems[index], Toast.LENGTH_SHORT).show()
            }
            MenuLevel.SUB_MENU -> {
                if (currentMenuTitle == "Artists") {
                    val artists = musicRepo.getArtists()
                    if (index in artists.indices) {
                        val artist = artists[index]
                        menuLevel = MenuLevel.TRACKS_LIST
                        currentMenuTitle = artist
                        currentPlaylist = musicRepo.getTracksByArtist(artist)
                        currentDisplayItems = currentPlaylist.map { "${it.title} - ${it.artist}" }
                        selectedIndex = 0
                        renderCurrentState()
                    }
                } else if (currentMenuTitle == "Albums") {
                    val albums = musicRepo.getAlbums()
                    if (index in albums.indices) {
                        val album = albums[index]
                        menuLevel = MenuLevel.TRACKS_LIST
                        currentMenuTitle = album
                        currentPlaylist = musicRepo.getTracksByAlbum(album)
                        currentDisplayItems = currentPlaylist.map { "${it.title} - ${it.artist}" }
                        selectedIndex = 0
                        renderCurrentState()
                    }
                }
            }
            MenuLevel.TRACKS_LIST -> {
                if (index in currentPlaylist.indices) {
                    currentTrackIndex = index
                    playCurrentTrack()
                }
            }
        }
    }

    override fun onBackClick() {
        playClickSound()
        when (menuLevel) {
            MenuLevel.TRACKS_LIST, MenuLevel.SUB_MENU, MenuLevel.SETTINGS -> {
                menuLevel = MenuLevel.ROOT
                currentMenuTitle = "Nano Player"
                currentDisplayItems = mainMenuItems
                selectedIndex = 0
                renderCurrentState()
            }
            MenuLevel.ROOT -> {}
        }
    }

    override fun onPlayPauseClick() {
        togglePlayPause()
    }

    override fun onNextClick() {
        playNextTrack()
    }

    override fun onPreviousClick() {
        playPrevTrack()
    }

    override fun onRepeatClick() {
        playClickSound()
        repeatModeState = when (repeatModeState) {
            "Off" -> "All"
            "All" -> "One"
            "One" -> "Shuffle"
            else -> "Off"
        }
        updateCurrentScreenView()
    }

    override fun onScrub(fraction: Float) {
        if (mediaPlayer != null && mediaPlayer!!.duration > 0) {
            val target = (fraction * mediaPlayer!!.duration).toInt()
            mediaPlayer!!.seekTo(target)
            updateCurrentScreenView()
        }
    }

    override fun onVolumeChange(percent: Int) {
        audioManager?.let { am ->
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (percent / 100f * max).toInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            updateCurrentScreenView()
        }
    }

    private fun showNowPlaying() {
        if (currentPlaylist.isEmpty()) {
            Toast.makeText(this, "No tracks available", Toast.LENGTH_SHORT).show()
            return
        }
        updateCurrentScreenView()
    }

    private fun playCurrentTrack() {
        if (currentPlaylist.isEmpty()) return
        val track = currentPlaylist[currentTrackIndex]
        try {
            mediaPlayer?.release()
            mediaPlayer = if (track.sourceUri.isNotBlank()) {
                MediaPlayer().apply {
                    setDataSource(applicationContext, android.net.Uri.parse(track.sourceUri))
                    prepare()
                    start()
                }
            } else {
                null
            }
            isPlaying = true
            handler.removeCallbacks(progressRunnable)
            handler.post(progressRunnable)
        } catch (_: Exception) {
            isPlaying = true
        }
        updateCurrentScreenView()
    }

    private fun togglePlayPause() {
        playClickSound()
        try {
            if (mediaPlayer != null) {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                    handler.removeCallbacks(progressRunnable)
                } else {
                    mediaPlayer?.start()
                    isPlaying = true
                    handler.post(progressRunnable)
                }
            } else if (currentPlaylist.isNotEmpty()) {
                playCurrentTrack()
            }
        } catch (_: Exception) {}
        updateCurrentScreenView()
    }

    private fun playNextTrack() {
        playClickSound()
        if (currentPlaylist.isEmpty()) return
        currentTrackIndex = if (repeatModeState == "Shuffle") {
            (0 until currentPlaylist.size).random()
        } else {
            (currentTrackIndex + 1) % currentPlaylist.size
        }
        playCurrentTrack()
    }

    private fun playPrevTrack() {
        playClickSound()
        if (currentPlaylist.isEmpty()) return
        currentTrackIndex = if (currentTrackIndex - 1 < 0) currentPlaylist.size - 1 else currentTrackIndex - 1
        playCurrentTrack()
    }

    private fun playClickSound() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 15)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
        try {
            mediaPlayer?.release()
            toneGen?.release()
        } catch (_: Exception) {}
    }
}
