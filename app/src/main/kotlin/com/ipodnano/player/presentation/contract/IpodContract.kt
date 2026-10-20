package com.ipodnano.player.presentation.contract

interface IpodContract {
    interface View {
        fun renderMenu(
            title: String,
            items: List<String>,
            selectedIndex: Int,
            batteryPercent: Int,
            canGoBack: Boolean,
            isDarkMode: Boolean
        )

        fun renderNowPlaying(
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
            artworkUrl: String? = null
        )

        fun triggerHapticFeedback()
        fun playClickSound()
    }

    interface Presenter {
        fun attachView(view: View)
        fun detachView()
        fun onTouchItemClick(index: Int)
        fun onTouchBackClick()
        fun onTouchPlayPauseClick()
        fun onTouchNextClick()
        fun onTouchPreviousClick()
        fun onTouchScrub(fraction: Float)
        fun onTouchVolume(percent: Int)
        fun onToggleDarkMode()
        fun onTick()
    }
}
