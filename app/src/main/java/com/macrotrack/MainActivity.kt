package com.macrotrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.macrotrack.domain.WidgetRefreshRequester
import com.macrotrack.ui.add.AddScreen
import com.macrotrack.ui.edit.EditEntryScreen
import com.macrotrack.ui.editfood.EditFoodScreen
import com.macrotrack.ui.foodsources.FoodSourcesScreen
import com.macrotrack.ui.log.LogScreen
import com.macrotrack.ui.myfoods.MyFoodsScreen
import com.macrotrack.ui.settings.SettingsScreen
import com.macrotrack.ui.theme.MacroTrackTheme
import com.macrotrack.widget.ACTION_WIDGET_ADD
import com.macrotrack.widget.EXTRA_WIDGET_ADD_MODE
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var widgetRefreshRequester: WidgetRefreshRequester

    private val widgetActions = Channel<String>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialWidgetMode = intent.widgetAddMode()
        if (initialWidgetMode != null) consumeWidgetIntent(intent)
        setContent {
            MacroTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val initialDestination = remember(initialWidgetMode) {
                        initialWidgetMode?.let(::addRoute) ?: "log"
                    }
                    LaunchedEffect(navController) {
                        widgetActions.receiveAsFlow().collect { mode ->
                            navController.navigate(addRoute(mode))
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = initialDestination
                    ) {
                        composable("log") {
                            LogScreen(
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToAddFood = { sectionId, date, mode ->
                                    navController.navigate("add?date=$date&sectionId=$sectionId&mode=$mode")
                                },
                                onEditEntry = { entryId, date ->
                                    val dateIso = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                                    navController.navigate("edit-entry/$entryId/$dateIso")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToFoodSources = { navController.navigate("food-sources") },
                            )
                        }
                        composable(
                            route = "add?date={date}&sectionId={sectionId}&mode={mode}"
                        ) {
                            AddScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToFoodSources = { navController.navigate("food-sources") },
                                onEditFood = { foodId -> navController.navigate("edit-food/$foodId") }
                            )
                        }
                        composable(
                            route = "edit-entry/{entryId}/{dateIso}"
                        ) { backStackEntry ->
                            EditEntryScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("food-sources") {
                            FoodSourcesScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToMyFoods = { navController.navigate("my-foods") }
                            )
                        }
                        composable("my-foods") {
                            MyFoodsScreen(
                                onBack = { navController.popBackStack() },
                                onEditFood = { foodId -> navController.navigate("edit-food/$foodId") }
                            )
                        }
                        composable("edit-food/{foodId}") {
                            EditFoodScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            widgetRefreshRequester.requestUpdate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.widgetAddMode()?.let { mode ->
            consumeWidgetIntent(intent)
            widgetActions.trySend(mode)
        }
    }

    private fun consumeWidgetIntent(intent: Intent) {
        setIntent(Intent(intent).apply {
            action = null
            removeExtra(EXTRA_WIDGET_ADD_MODE)
        })
    }
}

private fun Intent.widgetAddMode(): String? {
    val mode = getStringExtra(EXTRA_WIDGET_ADD_MODE)
    if (action != ACTION_WIDGET_ADD && mode == null) return null
    return mode?.takeIf {
        it in setOf("search", "label", "barcode", "quick")
    }
}

private fun addRoute(mode: String): String {
    val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    return "add?date=$date&sectionId=0&mode=$mode"
}
