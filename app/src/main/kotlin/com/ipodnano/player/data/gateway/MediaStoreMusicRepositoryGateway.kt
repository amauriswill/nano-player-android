package com.ipodnano.player.data.gateway

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.ipodnano.player.domain.gateway.MusicRepositoryGateway
import com.ipodnano.player.domain.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MediaStoreMusicRepositoryGateway(
    private val context: Context,
    private val fallbackGateway: MusicRepositoryGateway = InMemoryMusicRepositoryGateway()
) : MusicRepositoryGateway {

    private val cachedTracks = mutableListOf<Track>()

    // Reads all audio formats and extracts local album covers
    fun reload() {
        cachedTracks.clear()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
                "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR " +
                "${MediaStore.Audio.Media.MIME_TYPE} = 'application/ogg' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.flac' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.aac' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.ogg' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.opus' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.wav' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.wma' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.mid' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.midi'"

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

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Sin título"
                    val artist = it.getString(artistCol) ?: "Artista desconocido"
                    val album = it.getString(albumCol) ?: "Álbum desconocido"
                    val duration = it.getLong(durCol)

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    var artUriStr: String? = null

                    // 1. Check cached extracted cover file
                    val artCacheFile = File(context.cacheDir, "art_${id}.jpg")
                    if (artCacheFile.exists()) {
                        artUriStr = "file://${artCacheFile.absolutePath}"
                    } else if (albumIdCol != -1) {
                        // 2. Try MediaStore album art URI
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

                    // 3. If no artwork URI yet, extract embedded ID3 picture in background
                    if (artUriStr == null && !artCacheFile.exists()) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            val pfd = context.contentResolver.openFileDescriptor(contentUri, "r")
                            if (pfd != null) {
                                retriever.setDataSource(pfd.fileDescriptor)
                                val picture = retriever.embeddedPicture
                                if (picture != null) {
                                    val fos = FileOutputStream(artCacheFile)
                                    fos.write(picture)
                                    fos.flush()
                                    fos.close()
                                    artUriStr = "file://${artCacheFile.absolutePath}"
                                }
                                pfd.close()
                            }
                            retriever.release()
                        } catch (_: Exception) {}
                    }

                    cachedTracks.add(
                        Track(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = if (duration > 0) duration else 180000L,
                            sourceUri = contentUri.toString(),
                            artworkUrl = artUriStr
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        if (cachedTracks.isEmpty()) {
            cachedTracks.addAll(fallbackGateway.getAllTracks())
        }
    }

    fun getTracksAsJson(): String {
        if (cachedTracks.isEmpty()) {
            reload()
        }
        val jsonArray = JSONArray()
        for (track in cachedTracks) {
            val obj = JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("durationMs", track.durationMs)
                put("uri", track.sourceUri)
                put("cover", track.artworkUrl ?: "")
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    override fun getAllTracks(): List<Track> {
        if (cachedTracks.isEmpty()) {
            reload()
        }
        return cachedTracks.toList()
    }

    override fun getTrackById(id: String): Track? {
        return getAllTracks().firstOrNull { it.id == id }
    }

    override fun getArtists(): List<String> {
        return getAllTracks().map { it.artist }.distinct().sorted()
    }

    override fun getTracksByArtist(artist: String): List<Track> {
        return getAllTracks().filter { it.artist.equals(artist, ignoreCase = true) }
    }

    override fun getAlbums(): List<String> {
        return getAllTracks().map { it.album }.distinct().sorted()
    }

    override fun getTracksByAlbum(album: String): List<Track> {
        return getAllTracks().filter { it.album.equals(album, ignoreCase = true) }
    }
}
