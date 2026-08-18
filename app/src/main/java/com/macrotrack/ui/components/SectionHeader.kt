package com.macrotrack.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import com.macrotrack.ui.theme.MacroTrackShapes
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActiveDropTarget: Boolean = false,
    isInvalidDropTarget: Boolean = false,
    moveAccessibilityLabel: String? = null,
    onMoveAccessibilityAction: (() -> Boolean)? = null,
) {
    val background = when {
        isActiveDropTarget && isInvalidDropTarget ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        isActiveDropTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isExpanded -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val targetBorder = if (isActiveDropTarget && !isInvalidDropTarget) {
        Modifier.border(1.5.dp, brandPrimary(), MacroTrackShapes.small)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggleExpand)
            .animateContentSize()
            .background(background)
            .then(targetBorder)
            .semantics {
                moveAccessibilityLabel?.let { label ->
                    customActions = listOf(
                        CustomAccessibilityAction(label) {
                            onMoveAccessibilityAction?.invoke() ?: false
                        }
                    )
                }
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(brandPrimary())
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

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniMacro(label = "P", value = totalMacros.proteinG, color = macroProteinColor())
            MiniMacro(label = "C", value = totalMacros.carbsG, color = macroCarbsColor())
            MiniMacro(label = "F", value = totalMacros.fatG, color = macroFatColor())
        }

        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand"
        )
    }
}

@Composable
private fun MiniMacro(label: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("${(value).roundToInt()}g $label", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
