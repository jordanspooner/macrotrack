package com.macrotrack.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.DailySummary
import com.macrotrack.ui.theme.MacroCalories
import com.macrotrack.ui.theme.MacroCarbs
import com.macrotrack.ui.theme.MacroFat
import com.macrotrack.ui.theme.MacroProtein
import kotlin.math.roundToInt

@Composable
fun MacroSummaryCard(
    summary: DailySummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔥 ${summary.logged.kcal.roundToInt()} / ${summary.goals.kcal} kcal",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${(summary.kcalPercent * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            MacroBar(progress = summary.kcalPercent, color = MacroCalories)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MacroColumn("Protein", summary.logged.proteinG, summary.goals.proteinG, summary.proteinPercent, MacroProtein)
                MacroColumn("Carbs", summary.logged.carbsG, summary.goals.carbsG, summary.carbsPercent, MacroCarbs)
                MacroColumn("Fat", summary.logged.fatG, summary.goals.fatG, summary.fatPercent, MacroFat)
            }
        }
    }
}

@Composable
private fun MacroColumn(
    label: String,
    logged: Float,
    goal: Int,
    percent: Float,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = "${logged.roundToInt()} / ${goal}g",
            style = MaterialTheme.typography.bodyMedium
        )
        MacroBar(
            progress = percent,
            color = color,
            modifier = Modifier.width(60.dp).padding(top = 4.dp)
        )
    }
}
