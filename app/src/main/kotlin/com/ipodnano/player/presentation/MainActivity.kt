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
import android.media.AudioDeviceInfo
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
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ipodnano.player.data.gateway.AndroidAudioPlayerGateway
import com.ipodnano.player.data.gateway.MediaStoreMusicRepositoryGateway
import com.ipodnano.player.domain.model.Track
import com.ipodnano.player.domain.usecase.AdjustVolumeUseCase
import com.ipodnano.player.domain.usecase.NavigateMenuUseCase
import com.ipodnano.player.domain.usecase.PlayTrackUseCase
import com.ipodnano.player.domain.usecase.SkipTrackUseCase
import com.ipodnano.player.domain.usecase.TogglePlayPauseUseCase
import com.ipodnano.player.presentation.contract.IpodContract
import com.ipodnano.player.presentation.presenter.IpodPresenter

class MainActivity : Activity(), IpodContract.View {

    companion object {
        private const val REQUEST_MEDIA_PERMISSION = 1001
    }

    private lateinit var webView: WebView
    private lateinit var presenter: IpodContract.Presenter
    private lateinit var audioPlayerGateway: AndroidAudioPlayerGateway
    private lateinit var mediaStoreRepository: MediaStoreMusicRepositoryGateway
    private lateinit var audioManager: AudioManager

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    private val playbackSyncHandler = Handler(Looper.getMainLooper())
    private val playbackSyncRunnable = object : Runnable {
        override fun run() {
            if (::audioPlayerGateway.isInitialized && audioPlayerGateway.isPlaying()) {
                val posMs = audioPlayerGateway.getCurrentPositionMs()
                val durMs = audioPlayerGateway.getDurationMs()
                webView.evaluateJavascript(
                    "if (window.app) window.app.updateNativePlaybackState($posMs, $durMs, true);",
                    null
                )
            }
            playbackSyncHandler.postDelayed(this, 500L)
        }
    }

    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    updateAudioOutputStatus(false)
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    audioPlayerGateway.pause()
                    updateAudioOutputStatus(true)
                }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                if (level >= 0 && scale > 0) {
                    val pct = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                    runOnUiThread {
                        webView.evaluateJavascript("if (window.app) window.app.updateBattery($pct, $isCharging);", null)
                    }
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

        initArchitecture()
        initWebView()
        registerAudioRoutingDetection()
        registerBatteryMonitor()
        requestDefaultMediaPermissions()

        playbackSyncHandler.post(playbackSyncRunnable)
    }

    override fun onResume() {
        super.onResume()
        syncLibraryOnStartup()
    }

    private fun syncLibraryOnStartup() {
        Thread {
            try {
                mediaStoreRepository.reload()
                val tracksJson = mediaStoreRepository.getTracksAsJson()
                runOnUiThread {
                    webView.evaluateJavascript(
                        "if (window.app) window.app.syncDeviceTracks($tracksJson);",
                        null
                    )
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun registerAudioRoutingDetection() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(audioDeviceReceiver, filter)
    }

    private fun registerBatteryMonitor() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun getAudioOutputMode(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER -> return "bluetooth"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_LINE_ANALOG -> return "jack"
                }
            }
        } else {
            if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) return "bluetooth"
            @Suppress("DEPRECATION")
            if (audioManager.isWiredHeadsetOn) return "jack"
        }
        return "speaker"
    }

    private fun updateAudioOutputStatus(wasAutoPaused: Boolean) {
        val mode = getAudioOutputMode()
        runOnUiThread {
            webView.evaluateJavascript(
                "if (window.app) window.app.setAudioOutputMode('$mode', $wasAutoPaused);",
                null
            )
        }
    }

    private fun getInitialBatteryLevel(): Int {
        return try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 85
        } catch (_: Exception) {
            85
        }
    }

    private fun getInitialChargingStatus(): Boolean {
        return try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) {
            false
        }
    }

    private fun initArchitecture() {
        mediaStoreRepository = MediaStoreMusicRepositoryGateway(applicationContext)
        audioPlayerGateway = AndroidAudioPlayerGateway(applicationContext)
        mediaStoreRepository.reload()

        val playTrackUseCase = PlayTrackUseCase(audioPlayerGateway)
        val togglePlayPauseUseCase = TogglePlayPauseUseCase(audioPlayerGateway)
        val skipTrackUseCase = SkipTrackUseCase(audioPlayerGateway)
        val navigateMenuUseCase = NavigateMenuUseCase(mediaStoreRepository)
        val adjustVolumeUseCase = AdjustVolumeUseCase(audioPlayerGateway)

        presenter = IpodPresenter(
            musicRepository = mediaStoreRepository,
            audioPlayer = audioPlayerGateway,
            playTrackUseCase = playTrackUseCase,
            togglePlayPauseUseCase = togglePlayPauseUseCase,
            skipTrackUseCase = skipTrackUseCase,
            navigateMenuUseCase = navigateMenuUseCase,
            adjustVolumeUseCase = adjustVolumeUseCase
        )

        audioPlayerGateway.setOnTrackCompletedListener {
            runOnUiThread {
                webView.evaluateJavascript("if (window.app) window.app.onTrackCompleted();", null)
            }
        }
    }

    private fun initWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            addJavascriptInterface(AndroidNativeBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val tracksJson = mediaStoreRepository.getTracksAsJson()
                    val battPct = getInitialBatteryLevel()
                    val charging = getInitialChargingStatus()
                    val outputMode = getAudioOutputMode()
                    view?.evaluateJavascript(
                        "if (window.app) { window.app.setAudioOutputMode('$outputMode', false); window.app.updateBattery($battPct, $charging); window.app.loadDeviceTracks($tracksJson, false); }",
                        null
                    )
                    syncLibraryOnStartup()
                }
            }
            webChromeClient = WebChromeClient()

            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MEDIA_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            syncLibraryOnStartup()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("if (window.app) window.app.popMenu();", null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackSyncHandler.removeCallbacks(playbackSyncRunnable)
        try {
            unregisterReceiver(audioDeviceReceiver)
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        toneGenerator?.release()
        audioPlayerGateway.release()
        webView.destroy()
    }

    inner class AndroidNativeBridge {
        @JavascriptInterface
        fun triggerHaptic() {
            try {
                vibrator?.vibrate(12L)
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun getAudioOutput(): String = getAudioOutputMode()

        @JavascriptInterface
        fun getRealBatteryLevel(): Int = getInitialBatteryLevel()

        @JavascriptInterface
        fun isBatteryCharging(): Boolean = getInitialChargingStatus()

        @JavascriptInterface
        fun scanDeviceMusic(): String {
            mediaStoreRepository.reload()
            return mediaStoreRepository.getTracksAsJson()
        }

        @JavascriptInterface
        fun requestPermissions() {
            runOnUiThread {
                requestDefaultMediaPermissions()
            }
        }

        @JavascriptInterface
        fun playNativeAudio(sourceUri: String, trackId: String, title: String, artist: String, album: String, durationMs: Long): Boolean {
            val track = Track(trackId, title, artist, album, durationMs, sourceUri)
            audioPlayerGateway.play(track)
            return audioPlayerGateway.isPlaying()
        }

        @JavascriptInterface
        fun pauseNativeAudio() {
            audioPlayerGateway.pause()
        }

        @JavascriptInterface
        fun resumeNativeAudio() {
            audioPlayerGateway.resume()
        }

        @JavascriptInterface
        fun seekNativeAudio(positionMs: Long) {
            audioPlayerGateway.seekTo(positionMs)
        }

        @JavascriptInterface
        fun setNativeVolume(percent: Int) {
            audioPlayerGateway.setVolume(percent)
            try {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (maxVol * (percent.coerceIn(0, 100) / 100f)).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            } catch (_: Exception) {}
        }
    }

    override fun renderMenu(
        title: String,
        items: List<String>,
        selectedIndex: Int,
        batteryPercent: Int,
        canGoBack: Boolean,
        isDarkMode: Boolean
    ) {}

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
    ) {}

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
