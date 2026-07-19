package com.macrotrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.macroCaloriesColor
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor
import kotlin.math.roundToInt

@Composable
fun NutritionRow(
    macros: Macros,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${macros.kcal.roundToInt()} kcal",
            style = MaterialTheme.typography.bodySmall,
            color = macroCaloriesColor()
        )
        Text(
            text = "${macros.proteinG.roundToInt()}g P",
            style = MaterialTheme.typography.bodySmall,
            color = macroProteinColor()
        )
        Text(
            text = "${macros.carbsG.roundToInt()}g C",
            style = MaterialTheme.typography.bodySmall,
            color = macroCarbsColor()
        )
        Text(
            text = "${macros.fatG.roundToInt()}g F",
            style = MaterialTheme.typography.bodySmall,
            color = macroFatColor()
        )
    }
}
