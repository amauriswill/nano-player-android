package com.ipodnano.player.repository

import com.ipodnano.player.model.Track

interface MusicRepository {
    fun getAllTracks(): List<Track>
    fun getArtists(): List<String>
    fun getTracksByArtist(artist: String): List<Track>
    fun getAlbums(): List<String>
    fun getTracksByAlbum(album: String): List<Track>
}
