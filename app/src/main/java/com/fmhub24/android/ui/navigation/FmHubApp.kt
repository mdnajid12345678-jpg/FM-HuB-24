package com.fmhub24.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    const val PLAYER = "player/{contentId}?episodeKey={episodeKey}&fullscreen={fullscreen}"
    fun player(contentId: String, episodeKey: String?, fullscreen: Boolean = false) =
        "player/$contentId?episodeKey=${episodeKey.orEmpty()}&fullscreen=$fullscreen"

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

/** Screenshot-matched floating pill: the active destination has an amber icon+label pill. */
@Composable
private fun DesignTabBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val barBackground = Color(0xFF14221D)
    val accent = Color(0xFFF4B84A)
    val inactive = Color(0xFF9BAAA2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF08130F))
            .padding(horizontal = 18.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(40.dp),
            color = barBackground,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(68.dp).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Box(
                    modifier = Modifier
                        .weight(if (selected) 1.8f else 1f)
                        .height(54.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else Color.Transparent)
                        .clickable { onNavigate(tab.route) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = stringResource(tab.labelRes),
                            modifier = Modifier.size(22.dp),
                            tint = if (selected) Color(0xFF182018) else inactive,
                        )
                        if (selected) {
                            Text(
                                text = stringResource(tab.labelRes),
                                color = Color(0xFF182018),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

/** Five-tab shell: the bottom bar shows only on its own tabs (spec section 4). */
@Composable
fun FmHubApp(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in Dest.topLevel) {
                DesignTabBar(currentRoute = currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(Dest.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
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
                )
            }
            composable(Dest.DOWNLOADS) {
                DownloadsScreen(
                    onOpenDetails = { id -> navController.navigate(Dest.details(id)) },
                    onPlay = { id, episodeKey ->
                        navController.navigate(Dest.player(id, episodeKey))
                    },
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
                    navArgument("fullscreen") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                PlayerScreen(
                    contentId = entry.arguments?.getString("contentId").orEmpty(),
                    episodeKey = entry.arguments?.getString("episodeKey").orEmpty()
                        .takeIf { it.isNotEmpty() },
                    initialFullscreen = entry.arguments?.getBoolean("fullscreen") ?: false,
                    onBack = { navController.popBackStack() },
                    onOpenEpisode = { contentId, episodeKey, fullscreen ->
                        navController.navigate(Dest.player(contentId, episodeKey, fullscreen)) {
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
