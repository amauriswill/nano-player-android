package com.ipodnano.player.domain.model

data class MenuState(
    val title: String,
    val items: List<String>,
    val selectedIndex: Int
)
