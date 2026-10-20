package com.ipodnano.player.domain.gateway

import com.ipodnano.player.domain.model.Track

interface AudioPlayerGateway {
    fun play(track: Track)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(percent: Int)
    fun getCurrentPositionMs(): Long
    fun getDurationMs(): Long
    fun isPlaying(): Boolean
    fun setOnTrackCompletedListener(listener: () -> Unit)
    fun release()
}
