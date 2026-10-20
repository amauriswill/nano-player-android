package com.ipodnano.player.data.gateway

import com.ipodnano.player.domain.gateway.MusicRepositoryGateway
import com.ipodnano.player.domain.model.Track

class InMemoryMusicRepositoryGateway(
    initialTracks: List<Track> = defaultDemoTracks()
) : MusicRepositoryGateway {

    private val tracks = initialTracks.toMutableList()

    override fun getAllTracks(): List<Track> = tracks.toList()

    override fun getTrackById(id: String): Track? = tracks.firstOrNull { it.id == id }

    override fun getArtists(): List<String> = tracks.map { it.artist }.distinct().sorted()

    override fun getTracksByArtist(artist: String): List<Track> =
        tracks.filter { it.artist.equals(artist, ignoreCase = true) }

    override fun getAlbums(): List<String> = tracks.map { it.album }.distinct().sorted()

    override fun getTracksByAlbum(album: String): List<Track> =
        tracks.filter { it.album.equals(album, ignoreCase = true) }

    fun addTrack(track: Track) {
        tracks.add(track)
    }

    companion object {
        fun defaultDemoTracks(): List<Track> = listOf(
            Track(
                id = "track_2",
                title = "Feel Good, Inc.",
                artist = "Gorillaz",
                album = "Demon Days",
                durationMs = 223000L,
                sourceUri = "android.resource://com.ipodnano.player/raw/track2",
                artworkUrl = "https://coverartarchive.org/release/bfe6c5e1-e2ba-4ddc-b099-76440af0111a/front-250.jpg"
            ),
            Track(
                id = "track_1",
                title = "1901",
                artist = "Phoenix",
                album = "Wolfgang Amadeus Phoenix",
                durationMs = 193000L,
                sourceUri = "android.resource://com.ipodnano.player/raw/track1",
                artworkUrl = "https://coverartarchive.org/release-group/32304f6e-5b80-3818-a66b-47f09efff0d9/front-250.jpg"
            ),
            Track(
                id = "track_3",
                title = "Viva La Vida",
                artist = "Coldplay",
                album = "Viva la Vida",
                durationMs = 242000L,
                sourceUri = "android.resource://com.ipodnano.player/raw/track3",
                artworkUrl = "https://coverartarchive.org/release/ab5e9dce-dc97-482d-a29f-786fe2211812/front-250.jpg"
            ),
            Track(
                id = "track_4",
                title = "Harder, Better, Faster, Stronger",
                artist = "Daft Punk",
                album = "Discovery",
                durationMs = 224000L,
                sourceUri = "android.resource://com.ipodnano.player/raw/track4",
                artworkUrl = "https://coverartarchive.org/release/098060d4-ba3c-469d-88b0-08642261c7ab/front-250.jpg"
            ),
            Track(
                id = "track_5",
                title = "Mr. Brightside",
                artist = "The Killers",
                album = "Hot Fuss",
                durationMs = 222000L,
                sourceUri = "android.resource://com.ipodnano.player/raw/track5",
                artworkUrl = "https://coverartarchive.org/release/f5ee7a25-0121-432b-8ac2-0cdf3e714c2a/front-250.jpg"
            )
        )
    }
}
