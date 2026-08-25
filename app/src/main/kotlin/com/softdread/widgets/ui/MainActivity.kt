package com.softdread.widgets.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.softdread.widgets.R
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.components.NavTabs
import com.softdread.widgets.ui.detail.WidgetDetailScreen
import com.softdread.widgets.ui.detail.WidgetDetailViewModel
import com.softdread.widgets.ui.gallery.GalleryScreen
import com.softdread.widgets.ui.onboarding.OnboardingScreen
import com.softdread.widgets.ui.settings.SettingsScreen
import com.softdread.widgets.widgets.common.EXTRA_APP_WIDGET_ID
import com.softdread.widgets.widgets.common.EXTRA_WIDGET_TYPE

private object Routes {
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{type}?appWidgetId={appWidgetId}"

    fun detail(type: WidgetType, appWidgetId: Int? = null) =
        "detail/${type.id}?appWidgetId=${appWidgetId ?: -1}"
}

class MainActivity : ComponentActivity() {

    private val viewModel: SoftDreadViewModel by viewModels()
    private val detailViewModel: WidgetDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (preferences.appearance) {
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
                AppearanceMode.SYSTEM -> systemDark
            }

            SoftDreadTheme(
                darkTheme = isDark,
                pack = ThemePack.fromKeyOrDefault(preferences.themePackKey),
            ) {
                var showOnboarding by remember(preferences.onboardingComplete) {
                    mutableStateOf(!preferences.onboardingComplete)
                }

                if (showOnboarding) {
                    OnboardingScreen(
                        selectedPersonality = preferences.defaultPersonality,
                        onSelectPersonality = viewModel::setDefaultPersonality,
                        onFinish = {
                            viewModel.completeOnboarding()
                            showOnboarding = false
                        },
                    )
                } else {
                    SoftDreadApp(
                        viewModel = viewModel,
                        detailViewModel = detailViewModel,
                        isDark = isDark,
                        initialDeepLink = intent?.let(::deepLinkFrom),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun deepLinkFrom(intent: Intent): WidgetType? {
        val typeId = intent.getStringExtra(EXTRA_WIDGET_TYPE)
            ?: intent.getStringExtra(PinWidget.EXTRA_PINNED_TYPE)
            ?: intent.data?.pathSegments?.getOrNull(0)
        return WidgetType.fromId(typeId)
    }
}

@Composable
private fun SoftDreadApp(
    viewModel: SoftDreadViewModel,
    detailViewModel: WidgetDetailViewModel,
    isDark: Boolean,
    initialDeepLink: WidgetType?,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val chrome = SoftDreadTheme.chrome

    LaunchedEffect(isDark) { viewModel.refresh(isDark) }

    // A tap on a placed widget opens that widget's detail screen, so the tile
    // itself is the entry point to its own settings.
    LaunchedEffect(initialDeepLink) {
        initialDeepLink?.let { navController.navigate(Routes.detail(it)) }
    }

    Scaffold(
        containerColor = chrome.wallpaper,
        bottomBar = {
            if (route == Routes.GALLERY || route == Routes.SETTINGS) {
                NavTabs(
                    destinations = listOf(
                        stringResource(R.string.nav_gallery) to (route == Routes.GALLERY),
                        stringResource(R.string.nav_settings) to (route == Routes.SETTINGS),
                    ),
                    onSelect = { index ->
                        val target = if (index == 0) Routes.GALLERY else Routes.SETTINGS
                        if (target != route) {
                            navController.navigate(target) {
                                popUpTo(Routes.GALLERY) { inclusive = target == Routes.GALLERY }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.GALLERY,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Routes.GALLERY) {
                LaunchedEffect(Unit) { viewModel.refresh(isDark) }
                GalleryScreen(
                    viewModel = viewModel,
                    onOpenWidget = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = viewModel)
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("appWidgetId") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                ),
            ) { entry ->
                val type = WidgetType.fromId(entry.arguments?.getString("type"))
                val appWidgetId = entry.arguments?.getInt("appWidgetId")?.takeIf { it >= 0 }
                if (type == null) {
                    navController.popBackStack()
                } else {
                    WidgetDetailScreen(
                        type = type,
                        appWidgetId = appWidgetId,
                        viewModel = detailViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/** Kept for the deep link from a placed widget, which carries its own id. */
internal fun detailRouteFor(type: WidgetType, appWidgetId: Int?) = Routes.detail(type, appWidgetId)

internal const val EXTRA_APP_WIDGET_ID_ALIAS = EXTRA_APP_WIDGET_ID
