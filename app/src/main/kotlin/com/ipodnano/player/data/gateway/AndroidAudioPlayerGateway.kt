package com.ipodnano.player.data.gateway

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ipodnano.player.domain.gateway.AudioPlayerGateway
import com.ipodnano.player.domain.model.Track

class AndroidAudioPlayerGateway(
    private val context: Context
) : AudioPlayerGateway {

    private var mediaPlayer: MediaPlayer? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var currentTrackId: String? = null
    private var activePfd: ParcelFileDescriptor? = null

    var isHeadsetConnected: Boolean = true

    override fun play(track: Track) {
        try {
            activePfd?.close()
            activePfd = null

            val player = mediaPlayer ?: MediaPlayer().also { mediaPlayer = it }
            player.reset()
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)

            var dataSourceSet = false

            if (track.sourceUri.startsWith("content://") || track.sourceUri.startsWith("file://")) {
                try {
                    val uri = Uri.parse(track.sourceUri)
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        activePfd = pfd
                        player.setDataSource(pfd.fileDescriptor)
                        dataSourceSet = true
                    }
                } catch (_: Exception) {}
            }

            if (!dataSourceSet && track.sourceUri.startsWith("asset:///")) {
                try {
                    val assetPath = track.sourceUri.removePrefix("asset:///")
                    val afd = context.assets.openFd(assetPath)
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    dataSourceSet = true
                } catch (_: Exception) {}
            }

            if (!dataSourceSet && !track.sourceUri.startsWith("content://") && !track.sourceUri.startsWith("asset:///")) {
                try {
                    player.setDataSource(track.sourceUri)
                    dataSourceSet = true
                } catch (_: Exception) {}
            }

            if (dataSourceSet) {
                player.prepare()
                player.start()
                player.setOnCompletionListener {
                    onCompletionListener?.invoke()
                }
                currentTrackId = track.id
            } else {
                // If native file could not be opened, release player so fallback engine plays
                release()
            }
        } catch (_: Exception) {
            release()
        }
    }

    override fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    override fun resume() {
        try {
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
            }
        } catch (_: Exception) {}
    }

    override fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
    }

    override fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
        } catch (_: Exception) {}
    }

    override fun setVolume(percent: Int) {
        val clamped = (percent.toFloat() / 100f).coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (_: Exception) {}
    }

    override fun getCurrentPositionMs(): Long {
        return try {
            mediaPlayer?.currentPosition?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override fun getDurationMs(): Long {
        return try {
            mediaPlayer?.duration?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    override fun setOnTrackCompletedListener(listener: () -> Unit) {
        this.onCompletionListener = listener
    }

    override fun release() {
        try {
            activePfd?.close()
        } catch (_: Exception) {}
        activePfd = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentTrackId = null
    }
}
