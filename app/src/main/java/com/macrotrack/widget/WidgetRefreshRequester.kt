package com.macrotrack.widget

import android.content.Context
import android.util.Log
import com.macrotrack.domain.WidgetRefreshRequester
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetRefreshRequester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetRemoteViewsUpdater: WidgetRemoteViewsUpdater,
) : WidgetRefreshRequester {
    override suspend fun requestUpdate() {
        try {
            widgetRemoteViewsUpdater.update()
            Log.d(TAG, "Widget RemoteViews update completed")
        } catch (error: Throwable) {
            Log.w(TAG, "Widget RemoteViews update failed, falling back to broadcast", error)
            context.sendBroadcast(WidgetRefreshReceiver.intent(context))
        }
    }

    companion object {
        private const val TAG = "MacroTrackWidget"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetRefreshModule {
    @Binds
    abstract fun bindWidgetRefreshRequester(
        implementation: GlanceWidgetRefreshRequester,
    ): WidgetRefreshRequester
}
