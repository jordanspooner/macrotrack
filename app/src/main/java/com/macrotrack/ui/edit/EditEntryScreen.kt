package com.macrotrack.ui.edit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.ui.add.PortionSizeContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    onBack: () -> Unit,
    viewModel: EditEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState?.entry?.name ?: "Edit entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val state = uiState
        if (state != null) {
            PortionSizeContent(
                food = state.food,
                confirmLabel = "Save changes · ${state.food.macroPer100g.kcal.toInt()} kcal",
                initialPortionG = state.entry.portionG,
                initialPortionLabel = state.entry.portionLabel,
                onConfirm = { portionG, portionLabel ->
                    viewModel.confirmPortion(portionG, portionLabel)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}
