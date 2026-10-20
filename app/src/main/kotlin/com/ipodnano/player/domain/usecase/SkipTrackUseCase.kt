package com.ipodnano.player.domain.usecase

import com.ipodnano.player.domain.gateway.AudioPlayerGateway
import com.ipodnano.player.domain.model.Track

class SkipTrackUseCase(
    private val audioPlayerGateway: AudioPlayerGateway
) {
    fun skipNext(playlist: List<Track>, currentTrack: Track?): Track? {
        if (playlist.isEmpty()) return null
        val currentIndex = playlist.indexOfFirst { it.id == currentTrack?.id }
        val nextIndex = if (currentIndex == -1 || currentIndex == playlist.lastIndex) 0 else currentIndex + 1
        val nextTrack = playlist[nextIndex]
        audioPlayerGateway.play(nextTrack)
        return nextTrack
    }

    fun skipPrevious(playlist: List<Track>, currentTrack: Track?): Track? {
        if (playlist.isEmpty()) return null
        val currentPos = audioPlayerGateway.getCurrentPositionMs()
        if (currentPos > 3000L && currentTrack != null) {
            audioPlayerGateway.seekTo(0L)
            return currentTrack
        }

        val currentIndex = playlist.indexOfFirst { it.id == currentTrack?.id }
        val prevIndex = if (currentIndex <= 0) playlist.lastIndex else currentIndex - 1
        val prevTrack = playlist[prevIndex]
        audioPlayerGateway.play(prevTrack)
        return prevTrack
    }
}
