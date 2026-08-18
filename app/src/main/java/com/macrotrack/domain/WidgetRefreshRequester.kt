package com.macrotrack.domain

interface WidgetRefreshRequester {
    suspend fun requestUpdate()
}

object NoOpWidgetRefreshRequester : WidgetRefreshRequester {
    override suspend fun requestUpdate() = Unit
}
