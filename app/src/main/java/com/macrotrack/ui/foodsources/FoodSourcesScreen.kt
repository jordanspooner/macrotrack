package com.macrotrack.ui.foodsources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.domain.model.FoodSource
import com.macrotrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSourcesScreen(
    onBack: () -> Unit,
    onNavigateToMyFoods: () -> Unit = {},
    viewModel: FoodSourcesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var deleteTarget by remember { mutableStateOf<FoodSource?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoadingCatalog) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.sources.isEmpty() && uiState.catalogError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No food sources available",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            "Check your internet connection and try again, or add foods manually via Quick Add.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.lg))
                        TextButton(onClick = { viewModel.refreshCatalog() }) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                uiState.catalogError?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(Spacing.sm))
                            Button(onClick = { viewModel.refreshCatalog() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                uiState.downloadError?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }

                val installedSources = uiState.sources.filter { it.status != FoodSource.Status.NOT_INSTALLED && !it.isUserSource }
                val availableSources = uiState.sources.filter { it.status == FoodSource.Status.NOT_INSTALLED }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (installedSources.isNotEmpty()) {
                        item {
                            Text(
                                "Installed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.xs)
                            )
                        }
                        items(installedSources, key = { it.id }) { source ->
                            FoodSourceCard(
                                source = source,
                                progress = uiState.downloadProgress[source.id],
                                onInstall = { viewModel.downloadSource(source) },
                                onUpdate = { viewModel.updateSource(source) },
                                onDelete = { deleteTarget = source },
                            )
                        }
                    }
                    if (availableSources.isNotEmpty()) {
                        item {
                            Text(
                                "Available",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.xs)
                            )
                        }
                        items(availableSources, key = { it.id }) { source ->
                            FoodSourceCard(
                                source = source,
                                progress = uiState.downloadProgress[source.id],
                                onInstall = { viewModel.downloadSource(source) },
                                onUpdate = { viewModel.updateSource(source) },
                                onDelete = { deleteTarget = source },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                Button(
                    onClick = onNavigateToMyFoods,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("My custom foods")
                }

                Spacer(Modifier.height(Spacing.lg))
            }
        }

        if (deleteTarget != null) {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete ${deleteTarget?.name}?") },
                text = { Text("This will remove all ${deleteTarget?.itemCount ?: 0} foods from this source. Your log history will be preserved.") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget?.let { viewModel.deleteSource(it.id) }
                        deleteTarget = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun FoodSourceCard(
    source: FoodSource,
    progress: Float?,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    source.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (source.itemCount > 0) {
                Text(
                    text = "${source.itemCount} foods",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }

            if (progress != null) {
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (source.itemCount > 0) {
                    TextButton(onClick = onUpdate) {
                        Text("Update")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                } else {
                    TextButton(onClick = onInstall) {
                        Text("Install")
                    }
                }
            }
        }
    }
}
