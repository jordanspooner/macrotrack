package com.macrotrack.widget

import android.content.Context
import com.macrotrack.domain.usecase.log.GetDailySummaryUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Singleton
class WidgetRemoteViewsUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getDailySummaryUseCase: GetDailySummaryUseCase,
) {
    suspend fun update() = withContext(Dispatchers.Default) {
        val summary = getDailySummaryUseCase.invokeOnce(LocalDate.now())
        WidgetRemoteViewsBuilder(context).update(summary)
    }
}
