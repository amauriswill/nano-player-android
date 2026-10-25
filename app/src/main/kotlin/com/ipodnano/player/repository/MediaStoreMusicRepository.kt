package com.ipodnano.player.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.ipodnano.player.model.Track

class MediaStoreMusicRepository(
    private val context: Context
) : MusicRepository {

    private val cachedTracks = mutableListOf<Track>()

    init {
        cachedTracks.add(
            Track(
                id = 1L,
                title = "Feel Good Inc. (Demo)",
                artist = "Gorillaz",
                album = "Demon Days",
                durationMs = 222000L,
                sourceUri = "",
                albumArtUri = null,
                trackNumber = 6
            )
        )
    }

    fun reload() {
        cachedTracks.clear()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.TRACK
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val trackCol = it.getColumnIndex(MediaStore.Audio.Media.TRACK)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Sin título"
                    val artist = it.getString(artistCol) ?: "Artista desconocido"
                    val album = it.getString(albumCol) ?: "Álbum desconocido"
                    val duration = it.getLong(durCol)
                    val rawTrack = if (trackCol != -1) it.getInt(trackCol) else 0
                    val trackNum = if (rawTrack > 1000) rawTrack % 1000 else rawTrack

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    var artUriStr: String? = null
                    if (albumIdCol != -1) {
                        try {
                            val albumId = it.getLong(albumIdCol)
                            val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
                            val candidateUri = ContentUris.withAppendedId(sArtworkUri, albumId)
                            val pfd = context.contentResolver.openFileDescriptor(candidateUri, "r")
                            if (pfd != null) {
                                pfd.close()
                                artUriStr = candidateUri.toString()
                            }
                        } catch (_: Exception) {}
                    }

                    cachedTracks.add(
                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = if (duration > 0) duration else 180000L,
                            sourceUri = contentUri.toString(),
                            albumArtUri = artUriStr,
                            trackNumber = trackNum
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        if (cachedTracks.isEmpty()) {
            cachedTracks.add(
                Track(
                    id = 1L,
                    title = "Feel Good Inc. (Demo)",
                    artist = "Gorillaz",
                    album = "Demon Days",
                    durationMs = 222000L,
                    sourceUri = "",
                    albumArtUri = null,
                    trackNumber = 6
                )
            )
        }
    }

    override fun getAllTracks(): List<Track> = cachedTracks.toList()
    override fun getArtists(): List<String> = cachedTracks.map { it.artist }.distinct().sorted()
    override fun getTracksByArtist(artist: String): List<Track> = cachedTracks.filter { it.artist.equals(artist, ignoreCase = true) }
    override fun getAlbums(): List<String> = cachedTracks.map { it.album }.distinct().sorted()
    override fun getTracksByAlbum(album: String): List<Track> = cachedTracks.filter { it.album.equals(album, ignoreCase = true) }
}
