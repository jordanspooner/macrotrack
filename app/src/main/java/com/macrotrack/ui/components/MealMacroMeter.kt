package com.macrotrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroCarbsOverageColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroFatOverageColor
import com.macrotrack.ui.theme.macroProteinColor
import com.macrotrack.ui.theme.macroProteinOverageColor
import kotlin.math.roundToInt

/**
 * Compact visual summary of a meal's protein/carbs/fat progress against its
 * per-meal goals. Three equal lanes, each with the macro's label, an
 * actual/goal readout, and a [MacroBar] (goal track + overage tint).
 */
@Composable
fun MealMacroMeter(
    actual: Macros,
    goal: Macros,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        MealMacroLane(
            label = "P",
            actualG = actual.proteinG,
            goalG = goal.proteinG,
            color = macroProteinColor(),
            overageTint = macroProteinOverageColor(),
            modifier = Modifier.weight(1f),
        )
        MealMacroLane(
            label = "C",
            actualG = actual.carbsG,
            goalG = goal.carbsG,
            color = macroCarbsColor(),
            overageTint = macroCarbsOverageColor(),
            modifier = Modifier.weight(1f),
        )
        MealMacroLane(
            label = "F",
            actualG = actual.fatG,
            goalG = goal.fatG,
            color = macroFatColor(),
            overageTint = macroFatOverageColor(),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Compact actual-only macro summary used when per-meal goals are disabled.
 * Mirrors the [MealMacroMeter] lane layout (keeping the header height stable)
 * but shows only the three colored P/C/F values, without a goal readout or
 * progress track.
 */
@Composable
fun MealMacroSummary(
    macros: Macros,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        MacroValue(
            label = "P",
            grams = macros.proteinG,
            color = macroProteinColor(),
            modifier = Modifier.weight(1f),
        )
        MacroValue(
            label = "C",
            grams = macros.carbsG,
            color = macroCarbsColor(),
            modifier = Modifier.weight(1f),
        )
        MacroValue(
            label = "F",
            grams = macros.fatG,
            color = macroFatColor(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MacroValue(
    label: String,
    grams: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val g = grams.roundToInt()
    Column(
        modifier = modifier.semantics {
            contentDescription = "$label $g g"
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "$g g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MealMacroLane(
    label: String,
    actualG: Float,
    goalG: Float,
    color: Color,
    overageTint: Color,
    modifier: Modifier = Modifier,
) {
    val actual = actualG.roundToInt()
    val goal = goalG.roundToInt()
    val progress = if (goalG > 0f) actualG / goalG else 0f

    Column(
        modifier = modifier.semantics {
            contentDescription = "$label $actual g of $goal g goal"
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "$actual/$goal" + "g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        MacroBar(
            progress = progress,
            color = color,
            overageTint = overageTint,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        )
    }
}