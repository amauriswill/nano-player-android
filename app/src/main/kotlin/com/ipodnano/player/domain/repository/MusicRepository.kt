package com.ipodnano.player.domain.repository

import com.ipodnano.player.domain.model.Track

interface MusicRepository {
    fun getAllTracks(): List<Track>
    fun getArtists(): List<String>
    fun getTracksByArtist(artist: String): List<Track>
    fun getAlbums(): List<String>
    fun getTracksByAlbum(album: String): List<Track>
}
