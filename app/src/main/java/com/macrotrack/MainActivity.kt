package com.macrotrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.macrotrack.ui.add.AddScreen
import com.macrotrack.ui.edit.EditEntryScreen
import com.macrotrack.ui.editfood.EditFoodScreen
import com.macrotrack.ui.foodsources.FoodSourcesScreen
import com.macrotrack.ui.log.LogScreen
import com.macrotrack.ui.myfoods.MyFoodsScreen
import com.macrotrack.ui.settings.SettingsScreen
import com.macrotrack.ui.theme.MacroTrackTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MacroTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "log"
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
                                onNavigateToFoodSources = { navController.navigate("food-sources") }
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
}
