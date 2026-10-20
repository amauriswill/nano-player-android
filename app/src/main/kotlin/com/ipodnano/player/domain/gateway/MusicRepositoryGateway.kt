package com.ipodnano.player.domain.gateway

import com.ipodnano.player.domain.model.Track

interface MusicRepositoryGateway {
    fun getAllTracks(): List<Track>
    fun getTrackById(id: String): Track?
    fun getArtists(): List<String>
    fun getTracksByArtist(artist: String): List<Track>
    fun getAlbums(): List<String>
    fun getTracksByAlbum(album: String): List<Track>
}
