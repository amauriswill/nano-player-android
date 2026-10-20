package com.ipodnano.player.presentation

import com.ipodnano.player.data.gateway.InMemoryMusicRepositoryGateway
import com.ipodnano.player.domain.gateway.AudioPlayerGateway
import com.ipodnano.player.domain.model.Track
import com.ipodnano.player.domain.usecase.AdjustVolumeUseCase
import com.ipodnano.player.domain.usecase.NavigateMenuUseCase
import com.ipodnano.player.domain.usecase.PlayTrackUseCase
import com.ipodnano.player.domain.usecase.SkipTrackUseCase
import com.ipodnano.player.domain.usecase.TogglePlayPauseUseCase
import com.ipodnano.player.presentation.contract.IpodContract
import com.ipodnano.player.presentation.presenter.IpodPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IpodPresenterTest {

    private class FakeView : IpodContract.View {
        var lastMenuTitle: String? = null
        var lastSelectedIndex: Int = -1
        var renderNowPlayingCalled = false
        var clickSoundPlayed = false
        var hapticTriggered = false
        var isDark: Boolean = false

        override fun renderMenu(
            title: String,
            items: List<String>,
            selectedIndex: Int,
            batteryPercent: Int,
            canGoBack: Boolean,
            isDarkMode: Boolean
        ) {
            lastMenuTitle = title
            lastSelectedIndex = selectedIndex
            isDark = isDarkMode
        }

        override fun renderNowPlaying(
            trackTitle: String,
            artist: String,
            album: String,
            isPlaying: Boolean,
            progressFraction: Float,
            positionText: String,
            durationText: String,
            trackIndexText: String,
            volumePercent: Int,
            batteryPercent: Int,
            isDarkMode: Boolean,
            artworkUrl: String?
        ) {
            renderNowPlayingCalled = true
            isDark = isDarkMode
        }

        override fun triggerHapticFeedback() { hapticTriggered = true }
        override fun playClickSound() { clickSoundPlayed = true }
    }

    private class FakeAudioPlayer : AudioPlayerGateway {
        var isPlayingTrack = false
        var currentPos = 0L
        var currentVol = 70

        override fun play(track: Track) { isPlayingTrack = true }
        override fun pause() { isPlayingTrack = false }
        override fun resume() { isPlayingTrack = true }
        override fun stop() { isPlayingTrack = false }
        override fun seekTo(positionMs: Long) { currentPos = positionMs }
        override fun setVolume(percent: Int) { currentVol = percent }
        override fun getCurrentPositionMs(): Long = currentPos
        override fun getDurationMs(): Long = 200000L
        override fun isPlaying(): Boolean = isPlayingTrack
        override fun setOnTrackCompletedListener(listener: () -> Unit) {}
        override fun release() {}
    }

    private lateinit var presenter: IpodPresenter
    private lateinit var fakeView: FakeView
    private lateinit var fakePlayer: FakeAudioPlayer

    @Before
    fun setUp() {
        fakePlayer = FakeAudioPlayer()
        val repo = InMemoryMusicRepositoryGateway()
        presenter = IpodPresenter(
            musicRepository = repo,
            audioPlayer = fakePlayer,
            playTrackUseCase = PlayTrackUseCase(fakePlayer),
            togglePlayPauseUseCase = TogglePlayPauseUseCase(fakePlayer),
            skipTrackUseCase = SkipTrackUseCase(fakePlayer),
            navigateMenuUseCase = NavigateMenuUseCase(repo),
            adjustVolumeUseCase = AdjustVolumeUseCase(fakePlayer)
        )
        fakeView = FakeView()
        presenter.attachView(fakeView)
    }

    @Test
    fun `attachView initializes menu screen with title Nano Player`() {
        assertEquals("Nano Player", fakeView.lastMenuTitle)
        assertEquals(0, fakeView.lastSelectedIndex)
    }

    @Test
    fun `onTouchItemClick on Now Playing switches to now playing screen`() {
        presenter.onTouchItemClick(3) // Index 3 is Now Playing
        assertTrue(fakeView.renderNowPlayingCalled)
    }

    @Test
    fun `onTouchItemClick on Songs loads song list`() {
        presenter.onTouchItemClick(0) // 0 is Songs
        assertEquals("Songs", fakeView.lastMenuTitle)
    }

    @Test
    fun `onToggleDarkMode toggles view dark mode flag`() {
        presenter.onToggleDarkMode()
        assertTrue(fakeView.isDark)
    }

    @Test
    fun `onTouchPlayPauseClick starts playback and switches to now playing`() {
        presenter.onTouchPlayPauseClick()
        assertTrue(fakeView.renderNowPlayingCalled)
        assertTrue(fakePlayer.isPlaying())
    }
}
