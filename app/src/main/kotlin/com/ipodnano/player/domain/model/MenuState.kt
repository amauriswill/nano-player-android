package com.ipodnano.player.domain.model

enum class ScreenType {
    MENU,
    NOW_PLAYING
}

enum class MenuType {
    MAIN,
    PLAYLISTS,
    ARTISTS,
    ALBUMS,
    ALBUM_SONGS,
    SONGS,
    SETTINGS
}

data class MenuItem(
    val id: String,
    val label: String,
    val targetScreen: ScreenType = ScreenType.MENU,
    val targetMenu: MenuType? = null,
    val trackId: String? = null
)
