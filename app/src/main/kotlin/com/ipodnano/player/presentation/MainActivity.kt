package com.ipodnano.player.presentation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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

    private var shuffleState = false
    private var textScaleIndex = 0
    private val textScaleLabels = listOf("Normal", "Large", "Extra Large", "Compact")
    private val textScaleValues = listOf(1.0f, 1.15f, 1.30f, 0.85f)
    private var textScaleMultiplier = 1.0f

    private val settingsItems: List<String>
        get() = listOf(
            "Audio Quality: Lossless",
            "SoundCheck: On",
            "Shuffle: ${if (shuffleState) "On" else "Off"}",
            "Text Size: ${textScaleLabels[textScaleIndex]}",
            "About"
        )

    private enum class ScreenMode { MENU, NOW_PLAYING }
    private var screenMode = ScreenMode.MENU
    private enum class MenuLevel { ROOT, SUB_MENU, TRACKS_LIST, SETTINGS, ABOUT }
    private data class NavState(
        val level: MenuLevel,
        val title: String,
        val items: List<String>,
        val playlist: List<Track>,
        val selected: Int
    )
    private val navStack = mutableListOf<NavState>()

    private fun navigateTo(newLevel: MenuLevel, newTitle: String, newItems: List<String>, newPlaylist: List<Track>) {
        navStack.add(NavState(menuLevel, currentMenuTitle, currentDisplayItems, currentPlaylist, selectedIndex))
        menuLevel = newLevel
        currentMenuTitle = newTitle
        currentDisplayItems = newItems
        currentPlaylist = newPlaylist
        selectedIndex = 0
        screenMode = ScreenMode.MENU
        renderCurrentState()
    }
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

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (isPlaying) {
                    togglePlayPause()
                }
            }
        }
    }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_main)

        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 30)
        } catch (_: Exception) {}

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        } catch (_: Exception) {}

        checkAndRequestPermissions()
        musicRepo = MediaStoreMusicRepository(this)
        allTracks = musicRepo.getAllTracks()
        currentPlaylist = allTracks

        updateBatteryStatus()
        ipodScreen = findViewById(R.id.ipod_screen)
        ipodScreen.touchListener = this
        renderCurrentState()

        Thread {
            musicRepo.reload()
            val loaded = musicRepo.getAllTracks()
            if (loaded.isNotEmpty()) {
                runOnUiThread {
                    allTracks = loaded
                    if (currentPlaylist.size <= 1) {
                        currentPlaylist = allTracks
                    }
                    renderCurrentState()
                }
            }
        }.start()
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

        if (screenMode == ScreenMode.NOW_PLAYING) {
            updateCurrentScreenView()
            return
        }

        if (menuLevel == MenuLevel.ROOT || menuLevel == MenuLevel.SUB_MENU || menuLevel == MenuLevel.TRACKS_LIST || menuLevel == MenuLevel.SETTINGS || menuLevel == MenuLevel.ABOUT) {
            val backTitle = if (navStack.isNotEmpty()) navStack.last().title else "Library"
            ipodScreen.showMenu(
                title = currentMenuTitle,
                items = currentDisplayItems,
                selected = selectedIndex,
                battery = batteryPercent,
                backEnabled = navStack.isNotEmpty(),
                darkMode = false,
                textScale = textScaleMultiplier,
                backButtonTitle = backTitle
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
            repeat = repeatModeState,
            textScale = textScaleMultiplier
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
                        navigateTo(
                            MenuLevel.TRACKS_LIST,
                            "Music",
                            allTracks.map { "${it.title} - ${it.artist}" },
                            allTracks
                        )
                    }
                    1 -> {
                        // Artists
                        val artists = musicRepo.getArtists()
                        navigateTo(
                            MenuLevel.SUB_MENU,
                            "Artists",
                            if (artists.isNotEmpty()) artists else listOf("No artists found"),
                            currentPlaylist
                        )
                    }
                    2 -> {
                        // Albums
                        val albums = musicRepo.getAlbums()
                        navigateTo(
                            MenuLevel.SUB_MENU,
                            "Albums",
                            if (albums.isNotEmpty()) albums else listOf("No albums found"),
                            currentPlaylist
                        )
                    }
                    3 -> {
                        // Settings
                        navigateTo(
                            MenuLevel.SETTINGS,
                            "Settings",
                            settingsItems,
                            currentPlaylist
                        )
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
                if (index == 2) {
                    shuffleState = !shuffleState
                    currentDisplayItems = settingsItems
                    renderCurrentState()
                } else if (index == 3) {
                    textScaleIndex = (textScaleIndex + 1) % textScaleLabels.size
                    textScaleMultiplier = textScaleValues[textScaleIndex]
                    currentDisplayItems = settingsItems
                    renderCurrentState()
                    Toast.makeText(this, "Text Size: ${textScaleLabels[textScaleIndex]}", Toast.LENGTH_SHORT).show()
                } else if (index == 4) {
                    navigateTo(
                        MenuLevel.ABOUT,
                        "About",
                        emptyList(),
                        currentPlaylist
                    )
                } else {
                    Toast.makeText(this, settingsItems[index], Toast.LENGTH_SHORT).show()
                }
            }
            MenuLevel.SUB_MENU -> {
                if (currentMenuTitle == "Artists") {
                    val artists = musicRepo.getArtists()
                    if (index in artists.indices) {
                        val artist = artists[index]
                        val tracks = musicRepo.getTracksByArtist(artist)
                        navigateTo(
                            MenuLevel.TRACKS_LIST,
                            artist,
                            tracks.map { "${it.title} - ${it.artist}" },
                            tracks
                        )
                    }
                } else if (currentMenuTitle == "Albums") {
                    val albums = musicRepo.getAlbums()
                    if (index in albums.indices) {
                        val album = albums[index]
                        val tracks = musicRepo.getTracksByAlbum(album)
                        navigateTo(
                            MenuLevel.TRACKS_LIST,
                            album,
                            tracks.map { "${it.title} - ${it.artist}" },
                            tracks
                        )
                    }
                }
            }
            MenuLevel.TRACKS_LIST -> {
                if (index in currentPlaylist.indices) {
                    currentTrackIndex = index
                    playCurrentTrack()
                }
            }
            MenuLevel.ABOUT -> {}
        }
    }

    override fun onBackClick() {
        playClickSound()
        if (screenMode == ScreenMode.NOW_PLAYING) {
            screenMode = ScreenMode.MENU
            renderCurrentState()
            return
        }
        if (navStack.isNotEmpty()) {
            val prev = navStack.removeAt(navStack.size - 1)
            menuLevel = prev.level
            currentMenuTitle = prev.title
            currentDisplayItems = prev.items
            currentPlaylist = prev.playlist
            selectedIndex = prev.selected
            renderCurrentState()
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
        screenMode = ScreenMode.NOW_PLAYING
        updateCurrentScreenView()
    }

    private fun requestAudioFocus(): Boolean {
        audioManager?.let { am ->
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .build()
                am.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus({ focusChange ->
                    if (focusChange <= AudioManager.AUDIOFOCUS_LOSS) {
                        if (isPlaying) togglePlayPause()
                    }
                }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return true
    }

    private fun playCurrentTrack() {
        if (currentPlaylist.isEmpty()) return
        screenMode = ScreenMode.NOW_PLAYING
        val track = currentPlaylist[currentTrackIndex]
        try {
            mediaPlayer?.release()
            requestAudioFocus()
            mediaPlayer = if (track.sourceUri.isNotBlank()) {
                MediaPlayer().apply {
                    setDataSource(applicationContext, android.net.Uri.parse(track.sourceUri))
                    setOnCompletionListener { playNextTrack() }
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
        currentTrackIndex = if (shuffleState || repeatModeState == "Shuffle") {
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
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
            toneGen?.release()
        } catch (_: Exception) {}
    }
}
