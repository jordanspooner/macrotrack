package com.macrotrack.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary

@Composable
fun SearchContent(
    uiState: AddUiState,
    onQueryChanged: (String) -> Unit,
    onFoodSelected: (FoodItem) -> Unit,
    onQuickAddClick: () -> Unit,
    onManageFoodSources: () -> Unit = {},
    onEditFood: (Long) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            placeholder = { Text("Search foods…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        if (!uiState.hasFoodData) {
            NoFoodDataState(onManageFoodSources = onManageFoodSources)
        } else if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MacroTrackPillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(Spacing.lg)
                    ) {
                        Text(
                            "No foods match \"${uiState.query}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onQuickAddClick) {
                            Text("Quick add manually", color = brandPrimary())
                        }
                        TextButton(onClick = onManageFoodSources) {
                            Text("Manage food sources", color = brandPrimary())
                        }
                    }
                }
            }
        } else if (uiState.results.isNotEmpty()) {
            if (uiState.query.isBlank()) {
                Text(
                    text = "Suggested for you",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                )
            } else {
                Text(
                    text = "Results",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                )
            }
            LazyColumn {
                items(uiState.results, key = { it.id to it.name }) { food ->
                    FoodResultItem(
                        food = food,
                        onClick = { onFoodSelected(food) },
                        onEdit = if (food.source == Source.USER) { { onEditFood(food.id) } } else null
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MacroTrackPillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(Spacing.lg)
                    ) {
                        Text(
                            "No foods match \"${uiState.query}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onQuickAddClick) {
                            Text("Quick add manually", color = brandPrimary())
                        }
                        TextButton(onClick = onManageFoodSources) {
                            Text("Manage food sources", color = brandPrimary())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoFoodDataState(onManageFoodSources: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "No food data sources installed",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Add food data sources to search from thousands of foods.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onManageFoodSources) {
                Text("Manage food sources")
            }
        }
    }
}
@Composable
fun FoodResultItem(food: FoodItem, onClick: () -> Unit, onEdit: (() -> Unit)? = null) {
    val portionG = food.defaultPortionG ?: 100f
    val portioned = food.macroPer100g * (portionG / 100f)
    val portionText = buildString {
        append("${portionG.toInt()}g")
        food.defaultPortionLabel?.let { append(" · $it") }
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = food.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                if (!food.brand.isNullOrBlank()) {
                    Text(
                        food.brand,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "$portionText · ${portioned.kcal.toInt()} kcal · P${portioned.proteinG.toInt()} C${portioned.carbsG.toInt()} F${portioned.fatG.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Row {
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit food",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Quick add",
                        tint = brandPrimary()
                    )
                }
            }
        }
    )
}
