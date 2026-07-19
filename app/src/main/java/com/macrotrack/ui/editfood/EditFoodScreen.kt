package com.macrotrack.ui.editfood

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.ui.myfoods.FoodItemEditorForm
import com.macrotrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodScreen(
    onBack: () -> Unit,
    viewModel: EditFoodViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "Add food" else "Edit food") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
        ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Food not found", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(uiState.error ?: "", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(Spacing.lg))
                        TextButton(onClick = onBack) { Text("Go back") }
                    }
                }
            } else if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        FoodItemEditorForm(
                            title = if (viewModel.isNew) "Add food" else "Edit food",
                            draft = uiState.draft,
                            onDraftChanged = viewModel::updateDraft,
                            onSubmit = viewModel::save,
                            submitEnabled = uiState.submitEnabled,
                            consistencyWarning = uiState.consistencyWarning,
                        )
                    }
                    if (!viewModel.isNew) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        ) {
                            Text("Delete food")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete food") },
            text = { Text("Are you sure you want to delete this food?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
