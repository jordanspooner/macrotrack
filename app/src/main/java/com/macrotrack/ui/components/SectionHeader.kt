package com.macrotrack.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor
import kotlin.math.roundToInt

@Composable
fun SectionHeader(
    name: String,
    totalMacros: Macros,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    accentColor: Color = macroProteinColor(),
    modifier: Modifier = Modifier,
) {
    val background = if (isExpanded) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .animateContentSize()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(accentColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(${totalMacros.kcal.roundToInt()} kcal)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${totalMacros.proteinG.roundToInt()}g P",
                style = MaterialTheme.typography.bodyMedium,
                color = macroProteinColor()
            )
            Text(
                text = "${totalMacros.carbsG.roundToInt()}g C",
                style = MaterialTheme.typography.bodyMedium,
                color = macroCarbsColor()
            )
            Text(
                text = "${totalMacros.fatG.roundToInt()}g F",
                style = MaterialTheme.typography.bodyMedium,
                color = macroFatColor()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand"
        )
    }
}
