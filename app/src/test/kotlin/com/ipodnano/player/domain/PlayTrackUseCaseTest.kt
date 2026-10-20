package com.ipodnano.player.domain

import com.ipodnano.player.domain.gateway.AudioPlayerGateway
import com.ipodnano.player.domain.model.Track
import com.ipodnano.player.domain.usecase.PlayTrackUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayTrackUseCaseTest {

    private class FakeAudioPlayer : AudioPlayerGateway {
        var playedTrack: Track? = null
        var isPlayingTrack = false

        override fun play(track: Track) {
            playedTrack = track
            isPlayingTrack = true
        }

        override fun pause() { isPlayingTrack = false }
        override fun resume() { isPlayingTrack = true }
        override fun stop() { isPlayingTrack = false }
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(percent: Int) {}
        override fun getCurrentPositionMs(): Long = 0L
        override fun getDurationMs(): Long = 1000L
        override fun isPlaying(): Boolean = isPlayingTrack
        override fun setOnTrackCompletedListener(listener: () -> Unit) {}
        override fun release() {}
    }

    @Test
    fun `invoke should delegate play to audio player gateway`() {
        val fakePlayer = FakeAudioPlayer()
        val useCase = PlayTrackUseCase(fakePlayer)
        val track = Track("1", "Title", "Artist", "Album", 180000L, "http://test.mp3")

        useCase(track)

        assertEquals("1", fakePlayer.playedTrack?.id)
        assertTrue(fakePlayer.isPlaying())
    }
}
