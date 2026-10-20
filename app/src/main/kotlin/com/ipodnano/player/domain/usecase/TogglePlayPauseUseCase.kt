package com.ipodnano.player.domain.usecase

import com.ipodnano.player.domain.gateway.AudioPlayerGateway

class TogglePlayPauseUseCase(
    private val audioPlayerGateway: AudioPlayerGateway
) {
    operator fun invoke(): Boolean {
        return if (audioPlayerGateway.isPlaying()) {
            audioPlayerGateway.pause()
            false
        } else {
            audioPlayerGateway.resume()
            true
        }
    }
}
