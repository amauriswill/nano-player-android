package com.ipodnano.player.presentation

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import com.ipodnano.player.data.gateway.AndroidAudioPlayerGateway
import com.ipodnano.player.data.gateway.MediaStoreMusicRepositoryGateway
import com.ipodnano.player.domain.usecase.AdjustVolumeUseCase
import com.ipodnano.player.domain.usecase.NavigateMenuUseCase
import com.ipodnano.player.domain.usecase.PlayTrackUseCase
import com.ipodnano.player.domain.usecase.SkipTrackUseCase
import com.ipodnano.player.domain.usecase.TogglePlayPauseUseCase
import com.ipodnano.player.presentation.contract.IpodContract
import com.ipodnano.player.presentation.presenter.IpodPresenter
import com.ipodnano.player.presentation.view.IpodScreenView

class MainActivity : Activity(), IpodContract.View {

    companion object {
        private const val REQUEST_MEDIA_PERMISSION = 1001
    }

    private lateinit var ipodView: IpodScreenView
    private lateinit var presenter: IpodContract.Presenter
    private lateinit var audioPlayerGateway: AndroidAudioPlayerGateway
    private lateinit var mediaStoreRepository: MediaStoreMusicRepositoryGateway
    private lateinit var audioManager: AudioManager

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    // Headset state changed
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    audioPlayerGateway.pause()
                }
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

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 40)
            vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } catch (_: Exception) {}

        mediaStoreRepository = MediaStoreMusicRepositoryGateway(applicationContext)
        audioPlayerGateway = AndroidAudioPlayerGateway(applicationContext)
        mediaStoreRepository.reload()

        ipodView = IpodScreenView(this)
        setContentView(ipodView)

        presenter = IpodPresenter(
            musicRepository = mediaStoreRepository,
            audioPlayer = audioPlayerGateway,
            playTrackUseCase = PlayTrackUseCase(audioPlayerGateway),
            togglePlayPauseUseCase = TogglePlayPauseUseCase(audioPlayerGateway),
            skipTrackUseCase = SkipTrackUseCase(),
            navigateMenuUseCase = NavigateMenuUseCase(),
            adjustVolumeUseCase = AdjustVolumeUseCase()
        )

        ipodView.touchListener = object : IpodScreenView.TouchInteractionListener {
            override fun onItemClick(index: Int) { presenter.onTouchItemClick(index) }
            override fun onBackClick() { presenter.onTouchBackClick() }
            override fun onPlayPauseClick() { presenter.onTouchPlayPauseClick() }
            override fun onNextClick() { presenter.onTouchNextClick() }
            override fun onPreviousClick() { presenter.onTouchPreviousClick() }
            override fun onScrub(fraction: Float) { presenter.onTouchScrub(fraction) }
            override fun onVolumeChange(percent: Int) {
                presenter.onTouchVolume(percent)
                try {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVol = (maxVol * (percent.coerceIn(0, 100) / 100f)).toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                } catch (_: Exception) {}
            }
        }

        presenter.attachView(this)
        requestDefaultMediaPermissions()
        registerReceiver(audioDeviceReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(audioDeviceReceiver)
        } catch (_: Exception) {}
        presenter.detachView()
        audioPlayerGateway.release()
    }

    private fun requestDefaultMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), REQUEST_MEDIA_PERMISSION)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_MEDIA_PERMISSION)
            }
        }
    }

    override fun renderMenu(
        title: String,
        items: List<String>,
        selectedIndex: Int,
        batteryPercent: Int,
        canGoBack: Boolean,
        isDarkMode: Boolean
    ) {
        ipodView.showMenu(title, items, selectedIndex, batteryPercent, canGoBack, isDarkMode)
    }

    override fun renderNowPlaying(
        trackTitle: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        progressFraction: Float,
        positionText: String,
        durationText: String,
        trackIndexText: String,
        volumePercent: Int,
        batteryPercent: Int,
        isDarkMode: Boolean,
        artworkUrl: String?
    ) {
        ipodView.showNowPlaying(
            trackTitle, artist, album, isPlaying, progressFraction,
            positionText, durationText, trackIndexText, volumePercent, batteryPercent, isDarkMode
        )
    }

    override fun triggerHapticFeedback() {
        try {
            vibrator?.vibrate(12L)
        } catch (_: Exception) {}
    }

    override fun playClickSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 15)
        } catch (_: Exception) {}
    }
}
