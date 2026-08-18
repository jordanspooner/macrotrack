package com.macrotrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.BitmapImageProvider
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import com.macrotrack.R
import com.macrotrack.domain.model.DailyGoals
import com.macrotrack.domain.model.DailySummary
import java.time.LocalDate
import kotlin.math.roundToInt

object MacroTrackWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 180.dp),
            DpSize(300.dp, 180.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val applicationContext = context.applicationContext
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetDependencies::class.java,
        )
        val summary = runCatching {
            dependencies.getDailySummaryUseCase().invokeOnce(LocalDate.now())
        }.getOrElse {
            DailySummary(
                date = LocalDate.now(),
                logged = com.macrotrack.domain.model.Macros(0f, 0f, 0f, 0f),
                goals = DailyGoals(150, 250, 65),
            )
        }
        android.util.Log.d(
            "MacroTrackWidget",
            "ProvideGlance kcal=${summary.logged.kcal.roundToInt()}/${summary.goals.kcal} " +
                "protein=${summary.logged.proteinG.roundToInt()}/${summary.goals.proteinG}",
        )
        provideContent {
            MacroTrackWidgetContent(
                context = applicationContext,
                summary = summary,
            )
        }
    }
}

private data class WidgetColors(
    val surface: ColorProvider,
    val surfaceVariant: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val actionSurface: ColorProvider,
    val actionContent: ColorProvider,
    val protein: ColorProvider,
    val proteinOverage: ColorProvider,
    val carbs: ColorProvider,
    val carbsOverage: ColorProvider,
    val fat: ColorProvider,
    val fatOverage: ColorProvider,
)

private fun widgetColors() = WidgetColors(
    surface = ColorProvider(R.color.widget_surface),
    surfaceVariant = ColorProvider(R.color.widget_surface_variant),
    onSurface = ColorProvider(R.color.widget_on_surface),
    onSurfaceVariant = ColorProvider(R.color.widget_on_surface_variant),
    actionSurface = ColorProvider(R.color.widget_action_surface),
    actionContent = ColorProvider(R.color.widget_action_content),
    protein = ColorProvider(R.color.widget_protein),
    proteinOverage = ColorProvider(R.color.widget_protein_overage),
    carbs = ColorProvider(R.color.widget_carbs),
    carbsOverage = ColorProvider(R.color.widget_carbs_overage),
    fat = ColorProvider(R.color.widget_fat),
    fatOverage = ColorProvider(R.color.widget_fat_overage),
)

@Composable
private fun MacroTrackWidgetContent(
    context: Context,
    summary: DailySummary,
) {
    val size = LocalSize.current
    val compact = size.width < 250.dp
    val colors = widgetColors()
    val ringSize = if (compact) 64.dp else 100.dp
    val density = context.resources.displayMetrics.density
    val compactActionSize = ((size.width - 28.dp) / 4f).coerceIn(42.dp, 54.dp)
    val ringBitmap = renderKcalRing(
        context = context,
        progress = summary.kcalPercent,
        sizePx = (ringSize.value * density).toInt(),
        loggedKcal = summary.logged.kcal,
        goalKcal = summary.goals.kcal,
    )
    val actions = listOf(
        ActionIcon(
            iconRes = R.drawable.ic_widget_search,
            description = context.getString(R.string.widget_action_search),
            mode = "search",
        ),
        ActionIcon(
            iconRes = R.drawable.ic_widget_label,
            description = context.getString(R.string.widget_action_scan_label),
            mode = "label",
        ),
        ActionIcon(
            iconRes = R.drawable.ic_widget_barcode,
            description = context.getString(R.string.widget_action_scan_barcode),
            mode = "barcode",
        ),
        ActionIcon(
            iconRes = R.drawable.ic_widget_quick_add,
            description = context.getString(R.string.widget_action_quick_add),
            mode = "quick",
        ),
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Today",
                modifier = GlanceModifier.clickable(
                    actionStartActivity<com.macrotrack.MainActivity>()
                ),
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = 20.sp,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "${summary.logged.kcal.roundToInt()} / ${summary.goals.kcal} kcal",
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = 15.sp,
                ),
            )
        }

        if (compact) {
            CompactSummary(
                ringBitmap = ringBitmap,
                ringSize = ringSize,
                summary = summary,
                colors = colors,
                modifier = GlanceModifier.defaultWeight(),
            )
            ActionStrip(
                colors = colors,
                actions = actions,
                actionSize = compactActionSize,
                actionGap = 4.dp,
            )
        } else {
            WideSummary(
                ringBitmap = ringBitmap,
                ringSize = ringSize,
                summary = summary,
                colors = colors,
                actions = actions,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun CompactSummary(
    ringBitmap: android.graphics.Bitmap,
    ringSize: Dp,
    summary: DailySummary,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SummaryContent(
            ringBitmap = ringBitmap,
            ringSize = ringSize,
            summary = summary,
            colors = colors,
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            macroGap = 4.dp,
            ringTopPadding = 0.dp,
        )
    }
}

@Composable
private fun WideSummary(
    ringBitmap: android.graphics.Bitmap,
    ringSize: Dp,
    summary: DailySummary,
    colors: WidgetColors,
    actions: List<ActionIcon>,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryContent(
            ringBitmap = ringBitmap,
            ringSize = ringSize,
            summary = summary,
            colors = colors,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        ActionGrid(
            colors = colors,
            actions = actions,
            buttonSize = 52.dp,
            gap = 8.dp,
            modifier = GlanceModifier
                .width(112.dp)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun SummaryContent(
    ringBitmap: android.graphics.Bitmap,
    ringSize: Dp,
    summary: DailySummary,
    colors: WidgetColors,
    modifier: GlanceModifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    macroGap: Dp = 6.dp,
    ringTopPadding: Dp = 0.dp,
) {
    Row(
        modifier = modifier.clickable(
            actionStartActivity<com.macrotrack.MainActivity>()
        ),
        verticalAlignment = verticalAlignment,
    ) {
        Box(
            modifier = GlanceModifier
                .padding(top = ringTopPadding)
                .size(ringSize),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = BitmapImageProvider(ringBitmap),
                contentDescription = "Calories progress",
                modifier = GlanceModifier.size(ringSize),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetMacroRow(
                label = "Protein",
                logged = summary.logged.proteinG,
                goal = summary.goals.proteinG,
                progress = summary.proteinPercent,
                color = colors.protein,
                overageColor = colors.proteinOverage,
                trackColor = colors.surfaceVariant,
                textColor = colors.onSurface,
                valueColor = colors.onSurfaceVariant,
            )
            Spacer(GlanceModifier.height(macroGap))
            WidgetMacroRow(
                label = "Carbs",
                logged = summary.logged.carbsG,
                goal = summary.goals.carbsG,
                progress = summary.carbsPercent,
                color = colors.carbs,
                overageColor = colors.carbsOverage,
                trackColor = colors.surfaceVariant,
                textColor = colors.onSurface,
                valueColor = colors.onSurfaceVariant,
            )
            Spacer(GlanceModifier.height(macroGap))
            WidgetMacroRow(
                label = "Fat",
                logged = summary.logged.fatG,
                goal = summary.goals.fatG,
                progress = summary.fatPercent,
                color = colors.fat,
                overageColor = colors.fatOverage,
                trackColor = colors.surfaceVariant,
                textColor = colors.onSurface,
                valueColor = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetMacroRow(
    label: String,
    logged: Float,
    goal: Int,
    progress: Float,
    color: ColorProvider,
    overageColor: ColorProvider,
    trackColor: ColorProvider,
    textColor: ColorProvider,
    valueColor: ColorProvider,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = textColor, fontSize = 13.sp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = "${logged.roundToInt()}/${goal}g",
            style = TextStyle(color = valueColor, fontSize = 12.sp),
        )
    }
    MacroProgressBar(
        progress = progress,
        color = color,
        overageColor = overageColor,
        trackColor = trackColor,
    )
}

@Composable
private fun MacroProgressBar(
    progress: Float,
    color: ColorProvider,
    overageColor: ColorProvider,
    trackColor: ColorProvider,
) {
    val progressColor = if (progress > 1f) overageColor else color
    LinearProgressIndicator(
        progress.coerceIn(0f, 1f),
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp)
            .cornerRadius(3.dp),
        color = progressColor,
        backgroundColor = trackColor,
    )
}

private data class ActionIcon(
    val iconRes: Int,
    val description: String,
    val mode: String,
)

@Composable
private fun ActionStrip(
    colors: WidgetColors,
    actions: List<ActionIcon>,
    actionSize: Dp,
    actionGap: Dp,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) Spacer(GlanceModifier.width(actionGap))
            ActionButton(
                action = action,
                colors = colors,
                modifier = GlanceModifier.defaultWeight()
                    .height(actionSize),
                iconSize = 24.dp,
            )
        }
    }
}

@Composable
private fun ActionGrid(
    colors: WidgetColors,
    actions: List<ActionIcon>,
    buttonSize: Dp,
    gap: Dp,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionButton(
                action = actions[0],
                colors = colors,
                modifier = GlanceModifier.size(buttonSize),
                iconSize = 28.dp,
            )
            Spacer(GlanceModifier.width(gap))
            ActionButton(
                action = actions[1],
                colors = colors,
                modifier = GlanceModifier.size(buttonSize),
                iconSize = 28.dp,
            )
        }
        Spacer(GlanceModifier.height(gap))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionButton(
                action = actions[2],
                colors = colors,
                modifier = GlanceModifier.size(buttonSize),
                iconSize = 28.dp,
            )
            Spacer(GlanceModifier.width(gap))
            ActionButton(
                action = actions[3],
                colors = colors,
                modifier = GlanceModifier.size(buttonSize),
                iconSize = 28.dp,
            )
        }
    }
}

@Composable
private fun ActionButton(
    action: ActionIcon,
    colors: WidgetColors,
    modifier: GlanceModifier,
    iconSize: Dp,
) {
    Box(
        modifier = modifier
            .background(colors.actionSurface)
            .cornerRadius(12.dp)
            .clickable(actionStartActivity<com.macrotrack.MainActivity>(
                widgetAddParameters(action.mode)
            )),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(action.iconRes),
            contentDescription = action.description,
            colorFilter = ColorFilter.tint(colors.actionContent),
            modifier = GlanceModifier.size(iconSize),
        )
    }
}
