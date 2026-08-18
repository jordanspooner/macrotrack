package com.macrotrack.widget

import com.macrotrack.domain.usecase.log.GetDailySummaryUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependencies {
    fun getDailySummaryUseCase(): GetDailySummaryUseCase
    fun widgetRemoteViewsUpdater(): WidgetRemoteViewsUpdater
}
