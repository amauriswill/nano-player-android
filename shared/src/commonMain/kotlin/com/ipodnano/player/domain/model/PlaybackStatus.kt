package com.ipodnano.player.domain.model

data class PlaybackStatus(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
