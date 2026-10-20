package com.ipodnano.player.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sourceUri: String,
    val artworkUrl: String? = null,
    val trackNumber: Int = 0
)
