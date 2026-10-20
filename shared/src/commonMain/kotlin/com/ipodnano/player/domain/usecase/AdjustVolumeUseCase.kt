package com.ipodnano.player.domain.usecase

class AdjustVolumeUseCase {
    operator fun invoke(currentPercent: Int, delta: Int): Int {
        return (currentPercent + delta).coerceIn(0, 100)
    }
}
