package com.macrotrack.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.restingSurfaceColor

@Composable
fun SearchContent(
    uiState: AddUiState,
    onQueryChanged: (String) -> Unit,
    onFoodSelected: (FoodItem) -> Unit,
    onQuickAddClick: () -> Unit,
    onManageFoodSources: () -> Unit = {},
    onEditFood: (Long) -> Unit = {},
    onFoodQuickAdd: (FoodItem) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.hasFoodData) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                placeholder = { Text("Search foods…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (uiState.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
            )
        }

        when {
            !uiState.hasFoodData -> {
                NoFoodDataState(
                    onQuickAddClick = onQuickAddClick,
                    onManageFoodSources = onManageFoodSources,
                )
            }
            uiState.results.isNotEmpty() -> {
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
                            onEdit = if (food.source == Source.USER) { { onEditFood(food.id) } } else null,
                            onQuickAdd = { onFoodQuickAdd(food) },
                            lastPortionG = uiState.lastPortions[food.id],
                        )
                    }
                }
            }
            uiState.query.isBlank() -> {
                SearchEmptyState(
                    title = "Search your foods",
                    message = "Start typing to find foods in your installed databases.",
                    onQuickAddClick = null,
                    onManageFoodSources = onManageFoodSources,
                )
            }
            else -> {
                SearchEmptyState(
                    title = "No foods found",
                    message = "Nothing matches \"${uiState.query}\".",
                    onQuickAddClick = onQuickAddClick,
                    onManageFoodSources = onManageFoodSources,
                )
            }
        }
    }
}

@Composable
private fun NoFoodDataState(
    onQuickAddClick: () -> Unit,
    onManageFoodSources: () -> Unit,
) {
    SearchEmptyState(
        title = "No food databases installed",
        message = "Install a database or add your own food manually to start searching.",
        onQuickAddClick = onQuickAddClick,
        onManageFoodSources = onManageFoodSources,
    )
}

@Composable
private fun SearchEmptyState(
    title: String,
    message: String,
    onQuickAddClick: (() -> Unit)?,
    onManageFoodSources: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Surface(
                color = restingSurfaceColor(),
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            onQuickAddClick?.let { onClick ->
                Button(onClick = onClick) {
                    Text("Quick add manually")
                }
            }
            TextButton(onClick = onManageFoodSources) {
                Text("Manage food databases", color = brandPrimary())
            }
        }
    }
}

@Composable
fun FoodResultItem(
    food: FoodItem,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onQuickAdd: (() -> Unit)? = null,
    lastPortionG: Float? = null,
) {
    val portionG = lastPortionG?.takeIf { it > 0f } ?: food.defaultPortionG ?: 100f
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
                onQuickAdd?.let { onClick ->
                    IconButton(onClick = onClick) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Quick add",
                            tint = brandPrimary()
                        )
                    }
                }
            }
        }
    )
}
