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
import com.macrotrack.ui.log.LogScreen
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
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "add?date={date}&sectionId={sectionId}&mode={mode}"
                        ) {
                            AddScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
