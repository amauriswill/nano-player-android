package com.ipodnano.player.presentation.presenter

import com.ipodnano.player.domain.gateway.AudioPlayerGateway
import com.ipodnano.player.domain.gateway.MusicRepositoryGateway
import com.ipodnano.player.domain.model.PlaybackStatus
import com.ipodnano.player.domain.model.ScreenType
import com.ipodnano.player.domain.model.Track
import com.ipodnano.player.domain.usecase.AdjustVolumeUseCase
import com.ipodnano.player.domain.usecase.NavigateMenuUseCase
import com.ipodnano.player.domain.usecase.PlayTrackUseCase
import com.ipodnano.player.domain.usecase.SkipTrackUseCase
import com.ipodnano.player.domain.usecase.TogglePlayPauseUseCase
import com.ipodnano.player.presentation.contract.IpodContract

class IpodPresenter(
    private val musicRepository: MusicRepositoryGateway,
    private val audioPlayer: AudioPlayerGateway,
    private val playTrackUseCase: PlayTrackUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val skipTrackUseCase: SkipTrackUseCase,
    private val navigateMenuUseCase: NavigateMenuUseCase,
    private val adjustVolumeUseCase: AdjustVolumeUseCase
) : IpodContract.Presenter {

    private var view: IpodContract.View? = null
    private var menuState = navigateMenuUseCase.createInitialState()
    private var playbackStatus = PlaybackStatus()
    private var playlist: List<Track> = emptyList()
    private val batteryPercent: Int = 94

    init {
        audioPlayer.setOnTrackCompletedListener {
            onTouchNextClick()
        }
    }

    override fun attachView(view: IpodContract.View) {
        this.view = view
        playlist = musicRepository.getAllTracks()
        renderCurrentState()
    }

    override fun detachView() {
        this.view = null
    }

    override fun onTouchItemClick(index: Int) {
        view?.playClickSound()
        view?.triggerHapticFeedback()

        val (nextState, trackIdToPlay) = navigateMenuUseCase.selectIndex(menuState, index)
        menuState = nextState

        if (trackIdToPlay != null) {
            val track = musicRepository.getTrackById(trackIdToPlay)
            if (track != null) {
                playTrack(track)
            }
        } else if (menuState.currentScreen == ScreenType.NOW_PLAYING && playbackStatus.currentTrack == null && playlist.isNotEmpty()) {
            val defaultTrack = playlist.first()
            playbackStatus = playbackStatus.copy(
                currentTrack = defaultTrack,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = defaultTrack.durationMs
            )
        }
        renderCurrentState()
    }

    override fun onTouchBackClick() {
        view?.playClickSound()
        menuState = navigateMenuUseCase.pressBack(menuState)
        renderCurrentState()
    }

    override fun onTouchPlayPauseClick() {
        view?.playClickSound()
        if (playbackStatus.currentTrack == null && playlist.isNotEmpty()) {
            playTrack(playlist.first())
            return
        }
        val isNowPlaying = togglePlayPauseUseCase()
        playbackStatus = playbackStatus.copy(isPlaying = isNowPlaying)
        renderCurrentState()
    }

    override fun onTouchNextClick() {
        view?.playClickSound()
        val nextTrack = skipTrackUseCase.skipNext(playlist, playbackStatus.currentTrack)
        if (nextTrack != null) {
            playbackStatus = playbackStatus.copy(
                currentTrack = nextTrack,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = nextTrack.durationMs
            )
            menuState = menuState.copy(currentScreen = ScreenType.NOW_PLAYING)
        }
        renderCurrentState()
    }

    override fun onTouchPreviousClick() {
        view?.playClickSound()
        val prevTrack = skipTrackUseCase.skipPrevious(playlist, playbackStatus.currentTrack)
        if (prevTrack != null) {
            playbackStatus = playbackStatus.copy(
                currentTrack = prevTrack,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = prevTrack.durationMs
            )
            menuState = menuState.copy(currentScreen = ScreenType.NOW_PLAYING)
        }
        renderCurrentState()
    }

    override fun onTouchScrub(fraction: Float) {
        val duration = playbackStatus.durationMs
        if (duration > 0) {
            val targetMs = (duration * fraction.coerceIn(0f, 1f)).toLong()
            audioPlayer.seekTo(targetMs)
            playbackStatus = playbackStatus.copy(currentPositionMs = targetMs)
            renderCurrentState()
        }
    }

    override fun onTouchVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        audioPlayer.setVolume(clamped)
        playbackStatus = playbackStatus.copy(volumePercent = clamped)
        renderCurrentState()
    }

    override fun onToggleDarkMode() {
        view?.playClickSound()
        menuState = navigateMenuUseCase.toggleDarkMode(menuState)
        renderCurrentState()
    }

    override fun onTick() {
        val isPlaying = audioPlayer.isPlaying()
        val currentPos = audioPlayer.getCurrentPositionMs()
        val duration = audioPlayer.getDurationMs().let { if (it > 0) it else (playbackStatus.currentTrack?.durationMs ?: 0L) }

        if (playbackStatus.isPlaying != isPlaying || playbackStatus.currentPositionMs != currentPos) {
            playbackStatus = playbackStatus.copy(
                isPlaying = isPlaying,
                currentPositionMs = currentPos,
                durationMs = duration
            )
            if (menuState.currentScreen == ScreenType.NOW_PLAYING) {
                renderNowPlayingScreen()
            }
        }
    }

    private fun playTrack(track: Track) {
        playTrackUseCase(track)
        playbackStatus = playbackStatus.copy(
            currentTrack = track,
            isPlaying = true,
            currentPositionMs = 0L,
            durationMs = track.durationMs
        )
        menuState = menuState.copy(currentScreen = ScreenType.NOW_PLAYING)
        renderCurrentState()
    }

    private fun renderCurrentState() {
        when (menuState.currentScreen) {
            ScreenType.MENU -> renderMenuScreen()
            ScreenType.NOW_PLAYING -> renderNowPlayingScreen()
        }
    }

    private fun renderMenuScreen() {
        val title = when (menuState.currentMenu) {
            com.ipodnano.player.domain.model.MenuType.MAIN -> "Nano Player"
            com.ipodnano.player.domain.model.MenuType.SONGS -> "Songs"
            com.ipodnano.player.domain.model.MenuType.ARTISTS -> "Artists"
            com.ipodnano.player.domain.model.MenuType.ALBUMS -> "Albums"
            com.ipodnano.player.domain.model.MenuType.ALBUM_SONGS -> menuState.selectedAlbum ?: "Album"
            com.ipodnano.player.domain.model.MenuType.PLAYLISTS -> "Playlists"
            com.ipodnano.player.domain.model.MenuType.SETTINGS -> "Settings"
        }
        val items = menuState.items.map { it.label }
        val canGoBack = menuState.menuHistory.isNotEmpty()
        view?.renderMenu(
            title = title,
            items = items,
            selectedIndex = menuState.selectedIndex,
            batteryPercent = batteryPercent,
            canGoBack = canGoBack,
            isDarkMode = menuState.isDarkMode
        )
    }

    private fun renderNowPlayingScreen() {
        val track = playbackStatus.currentTrack
        val title = track?.title ?: "No Track"
        val artist = track?.artist ?: "Unknown Artist"
        val album = track?.album ?: "Unknown Album"
        val currentIndex = playlist.indexOfFirst { it.id == track?.id }
        val indexText = if (currentIndex != -1) "${currentIndex + 1} of ${playlist.size}" else ""

        view?.renderNowPlaying(
            trackTitle = title,
            artist = artist,
            album = album,
            isPlaying = playbackStatus.isPlaying,
            progressFraction = playbackStatus.progressFraction,
            positionText = formatTime(playbackStatus.currentPositionMs),
            durationText = formatTime(playbackStatus.durationMs),
            trackIndexText = indexText,
            volumePercent = playbackStatus.volumePercent,
            batteryPercent = batteryPercent,
            isDarkMode = menuState.isDarkMode,
            artworkUrl = track?.artworkUrl ?: track?.sourceUri
        )
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%d:%02d", mins, secs)
    }
}
