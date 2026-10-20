package com.ipodnano.player.domain.usecase

import com.ipodnano.player.domain.gateway.AudioPlayerGateway
import com.ipodnano.player.domain.model.Track

class PlayTrackUseCase(
    private val audioPlayerGateway: AudioPlayerGateway
) {
    operator fun invoke(track: Track) {
        audioPlayerGateway.play(track)
    }
}
