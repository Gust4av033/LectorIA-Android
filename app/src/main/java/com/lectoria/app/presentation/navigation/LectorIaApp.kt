package com.lectoria.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lectoria.app.R
import com.lectoria.app.core.designsystem.Midnight
import com.lectoria.app.presentation.library.LibraryViewModel
import com.lectoria.app.presentation.screens.LibraryScreen
import com.lectoria.app.presentation.screens.PendingScreen

@Composable
fun LectorIaApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()

    Scaffold(
        containerColor = Midnight.Background,
        bottomBar = {
            NavigationBar(containerColor = Midnight.Surface) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${destination.route}"),
                        selected = backStack?.destination?.route == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Midnight.Highlight,
                            selectedTextColor = Midnight.Text,
                            indicatorColor = Midnight.Container.copy(alpha = 0.5f),
                            unselectedIconColor = Midnight.MutedText,
                            unselectedTextColor = Midnight.MutedText,
                        ),
                    )
                }
            }
        },
    ) { insets ->
        Box(
            Modifier.fillMaxSize().padding(insets).background(
                Brush.verticalGradient(listOf(Midnight.Surface, Midnight.Background)),
            ),
        ) {
            NavHost(navController = navController, startDestination = Destination.Library.route) {
                composable(Destination.Library.route) {
                    val viewModel: LibraryViewModel = hiltViewModel()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    LibraryScreen(state)
                }
                composable(Destination.Reader.route) {
                    PendingScreen(
                        title = R.string.reader,
                        headline = R.string.reader_headline,
                        description = R.string.reader_pending,
                        icon = R.drawable.ic_reader,
                        tag = "screen_reader",
                    )
                }
                composable(Destination.Player.route) {
                    PendingScreen(
                        title = R.string.player,
                        headline = R.string.player_headline,
                        description = R.string.player_pending,
                        icon = R.drawable.ic_player,
                        tag = "screen_player",
                    )
                }
                composable(Destination.Settings.route) {
                    PendingScreen(
                        title = R.string.settings,
                        headline = R.string.settings_headline,
                        description = R.string.settings_pending,
                        icon = R.drawable.ic_settings,
                        tag = "screen_settings",
                    )
                }
            }
        }
    }
}
