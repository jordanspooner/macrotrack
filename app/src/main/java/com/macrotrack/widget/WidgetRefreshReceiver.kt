package com.macrotrack.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                EntryPointAccessors.fromApplication(
                    appContext,
                    WidgetDependencies::class.java,
                ).widgetRemoteViewsUpdater().update()
                Log.d(TAG, "Widget RemoteViews broadcast update completed")
            } catch (error: Throwable) {
                Log.w(TAG, "Unable to refresh widget", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MacroTrackWidget"

        fun intent(context: Context): Intent =
            Intent(context, WidgetRefreshReceiver::class.java)
    }
}
