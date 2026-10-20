package com.ipodnano.player.domain.usecase

import com.ipodnano.player.domain.gateway.MusicRepositoryGateway
import com.ipodnano.player.domain.model.MenuItem
import com.ipodnano.player.domain.model.MenuType
import com.ipodnano.player.domain.model.ScreenType

data class MenuNavigationState(
    val currentScreen: ScreenType = ScreenType.MENU,
    val currentMenu: MenuType = MenuType.MAIN,
    val menuHistory: List<MenuType> = emptyList(),
    val items: List<MenuItem> = emptyList(),
    val selectedIndex: Int = 0,
    val selectedArtist: String? = null,
    val selectedAlbum: String? = null,
    val isDarkMode: Boolean = false
)

class NavigateMenuUseCase(
    private val musicRepository: MusicRepositoryGateway
) {
    fun createInitialState(): MenuNavigationState {
        return loadMenu(MenuType.MAIN, emptyList(), null, null, false)
    }

    fun selectIndex(state: MenuNavigationState, index: Int): Pair<MenuNavigationState, String?> {
        if (index !in state.items.indices) return state to null
        val item = state.items[index]

        if (item.id == "toggle_dark_mode") {
            val newDark = !state.isDarkMode
            val updated = loadMenu(state.currentMenu, state.menuHistory, state.selectedArtist, state.selectedAlbum, newDark)
            return updated to null
        }

        if (item.targetScreen == ScreenType.NOW_PLAYING) {
            val newHistory = state.menuHistory + state.currentMenu
            val newState = state.copy(
                currentScreen = ScreenType.NOW_PLAYING,
                menuHistory = newHistory,
                selectedIndex = index
            )
            return newState to item.trackId
        }

        if (item.targetMenu != null) {
            val newHistory = state.menuHistory + state.currentMenu
            val nextArtist = if (state.currentMenu == MenuType.ARTISTS) item.label else state.selectedArtist
            val nextAlbum = if (state.currentMenu == MenuType.ALBUMS) item.label else state.selectedAlbum
            val nextState = loadMenu(item.targetMenu, newHistory, nextArtist, nextAlbum, state.isDarkMode)
            return nextState to null
        }

        return state.copy(selectedIndex = index) to null
    }

    fun pressBack(state: MenuNavigationState): MenuNavigationState {
        if (state.currentScreen == ScreenType.NOW_PLAYING) {
            return state.copy(currentScreen = ScreenType.MENU)
        }
        if (state.menuHistory.isEmpty()) {
            return state
        }
        val prevMenu = state.menuHistory.last()
        val remaining = state.menuHistory.dropLast(1)
        val prevArtist = if (prevMenu == MenuType.ARTISTS || prevMenu == MenuType.MAIN) null else state.selectedArtist
        val prevAlbum = if (prevMenu == MenuType.ALBUMS || prevMenu == MenuType.MAIN) null else state.selectedAlbum
        return loadMenu(prevMenu, remaining, prevArtist, prevAlbum, state.isDarkMode)
    }

    fun toggleDarkMode(state: MenuNavigationState): MenuNavigationState {
        val newDark = !state.isDarkMode
        return loadMenu(state.currentMenu, state.menuHistory, state.selectedArtist, state.selectedAlbum, newDark)
    }

    private fun loadMenu(
        menuType: MenuType,
        history: List<MenuType>,
        selectedArtist: String?,
        selectedAlbum: String?,
        isDarkMode: Boolean
    ): MenuNavigationState {
        val items = when (menuType) {
            MenuType.MAIN -> listOf(
                MenuItem("music_songs", "Songs", ScreenType.MENU, MenuType.SONGS),
                MenuItem("music_artists", "Artists", ScreenType.MENU, MenuType.ARTISTS),
                MenuItem("music_albums", "Albums", ScreenType.MENU, MenuType.ALBUMS),
                MenuItem("now_playing", "Now Playing", ScreenType.NOW_PLAYING),
                MenuItem("settings", "Settings", ScreenType.MENU, MenuType.SETTINGS)
            )
            MenuType.SONGS -> {
                val tracks = musicRepository.getAllTracks()
                tracks.map { track ->
                    MenuItem(
                        id = track.id,
                        label = "${track.title} - ${track.artist}",
                        targetScreen = ScreenType.NOW_PLAYING,
                        trackId = track.id
                    )
                }
            }
            MenuType.ARTISTS -> {
                musicRepository.getArtists().map { artist ->
                    MenuItem(
                        id = "artist_$artist",
                        label = artist,
                        targetScreen = ScreenType.MENU,
                        targetMenu = MenuType.SONGS
                    )
                }
            }
            MenuType.ALBUMS -> {
                musicRepository.getAlbums().map { album ->
                    MenuItem(
                        id = "album_$album",
                        label = album,
                        targetScreen = ScreenType.MENU,
                        targetMenu = MenuType.ALBUM_SONGS
                    )
                }
            }
            MenuType.ALBUM_SONGS -> {
                val tracks = if (selectedAlbum != null) {
                    musicRepository.getTracksByAlbum(selectedAlbum)
                } else {
                    musicRepository.getAllTracks()
                }
                tracks.map { track ->
                    MenuItem(
                        id = track.id,
                        label = track.title,
                        targetScreen = ScreenType.NOW_PLAYING,
                        trackId = track.id
                    )
                }
            }
            MenuType.PLAYLISTS -> listOf(
                MenuItem("all", "All Songs", ScreenType.MENU, MenuType.SONGS)
            )
            MenuType.SETTINGS -> listOf(
                MenuItem(
                    id = "toggle_dark_mode",
                    label = "Dark Mode: ${if (isDarkMode) "ON" else "OFF"}",
                    targetScreen = ScreenType.MENU
                ),
                MenuItem("about", "About Nano Player v2.0", ScreenType.MENU)
            )
        }
        return MenuNavigationState(
            currentScreen = ScreenType.MENU,
            currentMenu = menuType,
            menuHistory = history,
            items = items,
            selectedIndex = 0,
            selectedArtist = selectedArtist,
            selectedAlbum = selectedAlbum,
            isDarkMode = isDarkMode
        )
    }
}
