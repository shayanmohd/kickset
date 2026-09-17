package com.mohdshayan.kickset.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import kotlin.math.min
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mohdshayan.kickset.ui.components.isWide
import com.mohdshayan.kickset.ui.cut.CutLengthScreen
import com.mohdshayan.kickset.ui.jobs.JobDetailScreen
import com.mohdshayan.kickset.ui.jobs.JobsScreen
import com.mohdshayan.kickset.ui.offsets.OffsetsScreen
import com.mohdshayan.kickset.ui.pipe.PipeDataScreen
import com.mohdshayan.kickset.ui.settings.LicencesScreen
import com.mohdshayan.kickset.ui.settings.MultipliersScreen
import com.mohdshayan.kickset.ui.settings.PrivacyScreen
import com.mohdshayan.kickset.ui.settings.SettingsScreen
import com.mohdshayan.kickset.ui.settings.SourcesScreen
import com.mohdshayan.kickset.ui.templates.SheetPreviewScreen
import com.mohdshayan.kickset.ui.templates.TemplatesScreen
import kotlinx.serialization.Serializable

@Serializable object OffsetsRoute
@Serializable object CutRoute
@Serializable object TemplatesRoute
@Serializable object PipeRoute
@Serializable object JobsRoute
@Serializable object SettingsRoute
@Serializable object SourcesRoute
@Serializable object MultipliersRoute
@Serializable object LicencesRoute
@Serializable object PrivacyRoute
@Serializable object SheetPreviewRoute
@Serializable data class JobDetailRoute(val id: Long)

/** What every top-level screen's overflow menu can open. */
class TopLevelActions(val settings: () -> Unit, val multipliers: () -> Unit, val sources: () -> Unit)

private class Tab(val label: String, val icon: ImageVector, val route: Any, val matches: (androidx.navigation.NavDestination) -> Boolean)

private val TABS = listOf(
    Tab("Offsets", Icons.Outlined.Timeline, OffsetsRoute) { it.hasRoute(OffsetsRoute::class) },
    Tab("Cut length", Icons.Outlined.ContentCut, CutRoute) { it.hasRoute(CutRoute::class) },
    Tab("Templates", Icons.Outlined.Straighten, TemplatesRoute) { it.hasRoute(TemplatesRoute::class) || it.hasRoute(SheetPreviewRoute::class) },
    Tab("Pipe data", Icons.Outlined.TableChart, PipeRoute) { it.hasRoute(PipeRoute::class) },
    Tab("Jobs", Icons.Outlined.FolderOpen, JobsRoute) { it.hasRoute(JobsRoute::class) || it.hasRoute(JobDetailRoute::class) },
)

/**
 * Five tab names have to sit across one phone. Their text stops growing at 110 percent of the system
 * font size, because "Cut length" clipped to "Cut" reads worse than a label that holds its size;
 * every other piece of text in the app scales the whole way.
 */
@Composable
private fun TabLabel(text: String, style: TextStyle) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, min(d.fontScale, 1.1f))) {
        Text(text, maxLines = 1, style = style)
    }
}

private fun NavHostController.goTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val topLevel = dest != null && listOf(OffsetsRoute::class, CutRoute::class, TemplatesRoute::class, PipeRoute::class, JobsRoute::class).any { dest.hasRoute(it) }
    val actions = TopLevelActions({ nav.navigate(SettingsRoute) }, { nav.navigate(MultipliersRoute) }, { nav.navigate(SourcesRoute) })
    val wide = isWide()

    val host: @Composable (Modifier) -> Unit = { m ->
        NavHost(nav, startDestination = OffsetsRoute, modifier = m) {
            composable<OffsetsRoute> { OffsetsScreen(actions) }
            composable<CutRoute> { CutLengthScreen(actions) }
            composable<TemplatesRoute> { TemplatesScreen(actions, onOpenSheets = { nav.navigate(SheetPreviewRoute) }) }
            composable<PipeRoute> { PipeDataScreen(actions) }
            composable<JobsRoute> { JobsScreen(actions, onOpenJob = { nav.navigate(JobDetailRoute(it)) }, onOpenOffsets = { nav.goTab(OffsetsRoute) }) }
            composable<JobDetailRoute> {
                JobDetailScreen(onBack = { nav.popBackStack() }, onReopen = { where ->
                    nav.goTab(when (where) { "cut" -> CutRoute; "templates" -> TemplatesRoute; else -> OffsetsRoute })
                })
            }
            composable<SheetPreviewRoute> { SheetPreviewScreen(onBack = { nav.popBackStack() }, onOpenTemplates = { nav.goTab(TemplatesRoute) }) }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { nav.popBackStack() }, onSources = { nav.navigate(SourcesRoute) }, onMultipliers = { nav.navigate(MultipliersRoute) },
                    onLicences = { nav.navigate(LicencesRoute) }, onPrivacy = { nav.navigate(PrivacyRoute) })
            }
            composable<SourcesRoute> { SourcesScreen(onBack = { nav.popBackStack() }) }
            composable<MultipliersRoute> { MultipliersScreen(onBack = { nav.popBackStack() }) }
            composable<LicencesRoute> { LicencesScreen(onBack = { nav.popBackStack() }) }
            composable<PrivacyRoute> { PrivacyScreen(onBack = { nav.popBackStack() }) }
        }
    }

    // One structure for every width, so the NavHost keeps its place (and its saved state) across rotation.
    Row(Modifier.fillMaxSize()) {
        if (wide) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets.systemBars) {
                TABS.forEach { tab ->
                    NavigationRailItem(
                        selected = dest?.let(tab.matches) == true, onClick = { nav.goTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) }, label = { TabLabel(tab.label, MaterialTheme.typography.labelMedium) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxSize()) {
            val showBar = !wide && (topLevel || dest == null)
            Box(Modifier.weight(1f).fillMaxSize().then(if (showBar) Modifier else Modifier.navigationBarsPadding())) { host(Modifier.fillMaxSize()) }
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = dest?.let(tab.matches) == true, onClick = { nav.goTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) }, label = { TabLabel(tab.label, MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
            }
        }
    }
}
