package com.lectoria.app.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lectoria.app.R

enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    Library("library", R.string.library, R.drawable.ic_library),
    Reader("reader", R.string.reader, R.drawable.ic_reader),
    Player("player", R.string.player, R.drawable.ic_player),
    Settings("settings", R.string.settings, R.drawable.ic_settings),
}
