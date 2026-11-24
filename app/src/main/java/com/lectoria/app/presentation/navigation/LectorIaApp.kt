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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lectoria.app.R
import com.lectoria.app.core.designsystem.Midnight
import com.lectoria.app.presentation.library.LibraryRoute
import com.lectoria.app.presentation.screens.PendingScreen
import com.lectoria.app.presentation.reader.ReaderRoute

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
                        selected = backStack?.destination?.route?.substringBefore('?') == destination.route,
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
            Modifier.fillMaxSize().padding(insets).background(Midnight.Background),
        ) {
            NavHost(navController = navController, startDestination = Destination.Library.route) {
                composable(Destination.Library.route) {
                    LibraryRoute(onOpenBook = { id -> navController.navigate("reader?bookId=${android.net.Uri.encode(id)}") { launchSingleTop = true } })
                }
                composable("reader?bookId={bookId}", arguments = listOf(navArgument("bookId") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    ReaderRoute(onListen = { navController.navigate("player") }, onLibrary = { navController.navigate(Destination.Library.route) { popUpTo(Destination.Library.route); launchSingleTop = true } })
                }
                composable(Destination.Player.route) {
                    com.lectoria.app.presentation.player.PlayerRoute(
                        onLibrary = { navController.navigate("library") },
                        onReader = { id -> navController.navigate("reader?bookId=$id") })
                }
                composable(Destination.Settings.route) {
                    com.lectoria.app.presentation.player.PlayerRoute(onLibrary = {}, onReader = {}, settingsOnly = true)
                }
            }
        }
    }
}
