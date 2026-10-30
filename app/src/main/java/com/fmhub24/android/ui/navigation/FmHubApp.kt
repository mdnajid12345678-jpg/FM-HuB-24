package com.fmhub24.android.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.fmhub24.android.R
import com.fmhub24.android.data.model.HomeCategory
import com.fmhub24.android.ui.screens.about.AboutScreen
import com.fmhub24.android.ui.screens.details.DetailsScreen
import com.fmhub24.android.ui.screens.downloads.DownloadsScreen
import com.fmhub24.android.ui.screens.explore.ExploreScreen
import com.fmhub24.android.ui.screens.history.HistoryScreen
import com.fmhub24.android.ui.screens.home.HomeScreen
import com.fmhub24.android.ui.screens.live.ChannelScreen
import com.fmhub24.android.ui.screens.live.FavoritesScreen
import com.fmhub24.android.ui.screens.live.LiveScreen
import com.fmhub24.android.ui.screens.mylist.MyListScreen
import com.fmhub24.android.ui.screens.player.PlayerScreen
import com.fmhub24.android.ui.screens.profile.ProfileScreen
import com.fmhub24.android.ui.screens.search.SearchScreen
import com.fmhub24.android.ui.screens.seeall.SeeAllScreen
import com.fmhub24.android.ui.screens.settings.SettingsScreen

object Dest {
    const val HOME = "home"
    const val LIVE = "live"
    const val EXPLORE = "explore"
    const val DOWNLOADS = "downloads"
    const val PROFILE = "profile"

    const val SEE_ALL = "see_all/{category}"
    fun seeAll(category: HomeCategory) = "see_all/${category.name}"

    const val SEARCH = "search"

    const val DETAILS = "details/{contentId}"
    fun details(contentId: String) = "details/$contentId"

    const val PLAYER = "player/{contentId}?episodeKey={episodeKey}"
    fun player(contentId: String, episodeKey: String?) =
        "player/$contentId?episodeKey=${episodeKey.orEmpty()}"

    const val CHANNEL = "channel/{contentId}"
    fun channel(contentId: String) = "channel/$contentId"

    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val MY_LIST = "my_list"
    const val ABOUT = "about"
    const val SETTINGS = "settings"

    val topLevel = setOf(HOME, LIVE, EXPLORE, DOWNLOADS, PROFILE)
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Dest.HOME, R.string.tab_home, Icons.Outlined.Home),
    Tab(Dest.LIVE, R.string.tab_live, Icons.Outlined.LiveTv),
    Tab(Dest.EXPLORE, R.string.tab_explore, Icons.Outlined.Explore),
    Tab(Dest.DOWNLOADS, R.string.tab_downloads, Icons.Outlined.Download),
    Tab(Dest.PROFILE, R.string.tab_profile, Icons.Outlined.Person),
)

/** Five-tab shell: the bottom bar shows only on its own tabs (spec section 4). */
@Composable
fun FmHubApp(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in Dest.topLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Dest.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.HOME) {
                HomeScreen(
                    onSeeAll = { category -> navController.navigate(Dest.seeAll(category)) },
                    onOpenDetails = { id -> navController.navigate(Dest.details(id)) },
                    onOpenChannel = { id -> navController.navigate(Dest.channel(id)) },
                    onOpenSearch = { navController.navigate(Dest.SEARCH) },
                    onOpenLive = { navController.navigate(Dest.LIVE) },
                )
            }
            composable(Dest.LIVE) {
                LiveScreen(
                    onOpenChannel = { id -> navController.navigate(Dest.channel(id)) },
                    onOpenSearch = { navController.navigate(Dest.SEARCH) },
                    onOpenFavorites = { navController.navigate(Dest.FAVORITES) },
                )
            }
            composable(Dest.EXPLORE) {
                ExploreScreen(
                    onOpenDetails = { id -> navController.navigate(Dest.details(id)) },
                    onOpenSearch = { navController.navigate(Dest.SEARCH) },
                )
            }
            composable(Dest.DOWNLOADS) {
                DownloadsScreen(
                    onOpenDetails = { id -> navController.navigate(Dest.details(id)) },
                    onBrowse = { navController.navigate(Dest.EXPLORE) },
                )
            }
            composable(Dest.PROFILE) {
                ProfileScreen(
                    onOpenMyList = { navController.navigate(Dest.MY_LIST) },
                    onOpenHistory = { navController.navigate(Dest.HISTORY) },
                    onOpenSettings = { navController.navigate(Dest.SETTINGS) },
                    onOpenAbout = { navController.navigate(Dest.ABOUT) },
                )
            }
            composable(
                route = Dest.SEE_ALL,
                arguments = listOf(navArgument("category") { type = NavType.StringType }),
            ) { entry ->
                SeeAllScreen(
                    category = entry.arguments?.getString("category") ?: HomeCategory.ALL.name,
                    onBack = { navController.popBackStack() },
                    onOpenDetails = { id -> navController.navigate(Dest.details(id)) },
                )
            }
            composable(Dest.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetails = { id -> navController.navigate(Dest.details(id)) },
                )
            }
            composable(
                route = Dest.DETAILS,
                arguments = listOf(navArgument("contentId") { type = NavType.StringType }),
            ) { entry ->
                DetailsScreen(
                    contentId = entry.arguments?.getString("contentId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onPlay = { contentId, episodeKey ->
                        navController.navigate(Dest.player(contentId, episodeKey))
                    },
                    onOpenChannel = { id -> navController.navigate(Dest.channel(id)) },
                )
            }
            composable(
                route = Dest.PLAYER,
                arguments = listOf(
                    navArgument("contentId") { type = NavType.StringType },
                    navArgument("episodeKey") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                PlayerScreen(
                    contentId = entry.arguments?.getString("contentId").orEmpty(),
                    episodeKey = entry.arguments?.getString("episodeKey").orEmpty()
                        .takeIf { it.isNotEmpty() },
                    onBack = { navController.popBackStack() },
                    onOpenEpisode = { contentId, episodeKey ->
                        navController.navigate(Dest.player(contentId, episodeKey)) {
                            popUpTo(Dest.PLAYER) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Dest.CHANNEL,
                arguments = listOf(navArgument("contentId") { type = NavType.StringType }),
            ) { entry ->
                ChannelScreen(
                    contentId = entry.arguments?.getString("contentId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenChannel = { id ->
                        navController.navigate(Dest.channel(id)) {
                            popUpTo(Dest.CHANNEL) { inclusive = true }
                        }
                    },
                    onOpenSearch = { navController.navigate(Dest.SEARCH) },
                )
            }
            composable(Dest.FAVORITES) {
                FavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChannel = { id -> navController.navigate(Dest.channel(id)) },
                )
            }
            composable(Dest.HISTORY) {
                HistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.MY_LIST) {
                MyListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetails = { id -> navController.navigate(Dest.details(id)) },
                )
            }
            composable(Dest.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
