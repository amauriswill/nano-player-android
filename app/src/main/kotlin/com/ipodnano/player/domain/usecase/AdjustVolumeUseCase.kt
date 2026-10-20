package com.ipodnano.player.domain.usecase

import com.ipodnano.player.domain.gateway.AudioPlayerGateway

class AdjustVolumeUseCase(
    private val audioPlayerGateway: AudioPlayerGateway
) {
    operator fun invoke(currentPercent: Int, delta: Int): Int {
        val newPercent = (currentPercent + delta).coerceIn(0, 100)
        audioPlayerGateway.setVolume(newPercent)
        return newPercent
    }
}
