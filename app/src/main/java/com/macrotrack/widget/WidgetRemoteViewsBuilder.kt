package com.macrotrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.macrotrack.MainActivity
import com.macrotrack.R
import com.macrotrack.domain.model.DailySummary
import kotlin.math.roundToInt

internal class WidgetRemoteViewsBuilder(
    private val context: Context,
) {
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    fun update(summary: DailySummary) {
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, MacroTrackWidgetReceiver::class.java),
        )
        widgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, build(widgetId, summary))
        }
    }

    private fun build(widgetId: Int, summary: DailySummary): RemoteViews {
        val compact = isCompact(widgetId)
        val views = RemoteViews(
            context.packageName,
            if (compact) R.layout.widget_compact else R.layout.widget_wide,
        )
        val ringSizeDp = if (compact) 64 else 88
        val ringSizePx = (ringSizeDp * context.resources.displayMetrics.density)
            .roundToInt()
            .coerceAtLeast(1)

        views.setTextViewText(R.id.widget_title, "Today")
        views.setTextViewText(
            R.id.widget_kcal,
            "${summary.logged.kcal.roundToInt()} / ${summary.goals.kcal} kcal",
        )
        views.setImageViewBitmap(
            R.id.widget_kcal_ring,
            renderKcalRing(
                context = context,
                progress = summary.kcalPercent,
                sizePx = ringSizePx,
                loggedKcal = summary.logged.kcal,
                goalKcal = summary.goals.kcal,
            ),
        )

        setMacro(
            views = views,
            labelId = R.id.protein_label,
            valueId = R.id.protein_value,
            progressId = R.id.protein_progress,
            overageProgressId = R.id.protein_overage_progress,
            label = "Protein",
            logged = summary.logged.proteinG,
            goal = summary.goals.proteinG,
            progress = summary.proteinPercent,
        )
        setMacro(
            views = views,
            labelId = R.id.carbs_label,
            valueId = R.id.carbs_value,
            progressId = R.id.carbs_progress,
            overageProgressId = R.id.carbs_overage_progress,
            label = "Carbs",
            logged = summary.logged.carbsG,
            goal = summary.goals.carbsG,
            progress = summary.carbsPercent,
        )
        setMacro(
            views = views,
            labelId = R.id.fat_label,
            valueId = R.id.fat_value,
            progressId = R.id.fat_progress,
            overageProgressId = R.id.fat_overage_progress,
            label = "Fat",
            logged = summary.logged.fatG,
            goal = summary.goals.fatG,
            progress = summary.fatPercent,
        )

        val openIntent = pendingIntent(widgetId, index = 0)
        views.setOnClickPendingIntent(R.id.widget_title, openIntent)
        views.setOnClickPendingIntent(R.id.widget_summary, openIntent)
        listOf(
            R.id.action_search to "search",
            R.id.action_label to "label",
            R.id.action_barcode to "barcode",
            R.id.action_quick_add to "quick",
        ).forEachIndexed { index, (viewId, mode) ->
            views.setOnClickPendingIntent(
                viewId,
                pendingIntent(widgetId, index = index + 1, mode = mode),
            )
        }
        return views
    }

    private fun setMacro(
        views: RemoteViews,
        labelId: Int,
        valueId: Int,
        progressId: Int,
        overageProgressId: Int,
        label: String,
        logged: Float,
        goal: Int,
        progress: Float,
    ) {
        views.setTextViewText(labelId, label)
        views.setTextViewText(valueId, "${logged.roundToInt()}/${goal}g")

        val safeProgress = if (progress.isFinite()) progress else 0f
        val progressValue = (safeProgress.coerceIn(0f, 1f) * PROGRESS_MAX)
            .roundToInt()
        val overage = safeProgress > 1f
        views.setViewVisibility(
            progressId,
            if (overage) View.GONE else View.VISIBLE,
        )
        views.setViewVisibility(
            overageProgressId,
            if (overage) View.VISIBLE else View.GONE,
        )
        views.setProgressBar(progressId, PROGRESS_MAX, progressValue, false)
        views.setProgressBar(
            overageProgressId,
            PROGRESS_MAX,
            if (overage) PROGRESS_MAX else progressValue,
            false,
        )
    }

    private fun isCompact(widgetId: Int): Boolean {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val width = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            COMPACT_WIDTH_DP,
        )
        return width < WIDE_WIDTH_DP
    }

    private fun pendingIntent(
        widgetId: Int,
        index: Int,
        mode: String? = null,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (mode != null) {
                action = ACTION_WIDGET_ADD
                putExtra(EXTRA_WIDGET_ADD_MODE, mode)
            }
        }
        return PendingIntent.getActivity(
            context,
            widgetId * 10 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val COMPACT_WIDTH_DP = 180
        const val WIDE_WIDTH_DP = 300
        const val PROGRESS_MAX = 1000
    }
}
