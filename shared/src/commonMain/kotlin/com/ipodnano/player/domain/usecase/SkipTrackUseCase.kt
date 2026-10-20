package com.ipodnano.player.domain.usecase

import com.ipodnano.player.domain.model.Track

class SkipTrackUseCase {
    operator fun invoke(tracks: List<Track>, currentTrack: Track, forward: Boolean): Track? {
        if (tracks.isEmpty()) return null
        val index = tracks.indexOfFirst { it.id == currentTrack.id }
        if (index == -1) return tracks.first()
        val nextIndex = if (forward) {
            (index + 1) % tracks.size
        } else {
            (index - 1 + tracks.size) % tracks.size
        }
        return tracks[nextIndex]
    }
}
