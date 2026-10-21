package com.ipodnano.player.domain.model

import androidx.annotation.Keep

@Keep
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sourceUri: String,
    val albumArtUri: String?,
    val trackNumber: Int
)
