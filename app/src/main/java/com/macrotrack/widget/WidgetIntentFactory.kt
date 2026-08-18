package com.macrotrack.widget

import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf

const val ACTION_WIDGET_ADD = "com.macrotrack.action.WIDGET_ADD"
const val EXTRA_WIDGET_ADD_MODE = "com.macrotrack.extra.WIDGET_ADD_MODE"

val WidgetModeKey = ActionParameters.Key<String>(EXTRA_WIDGET_ADD_MODE)

fun widgetAddParameters(mode: String): ActionParameters = actionParametersOf(
    WidgetModeKey.to(mode)
)
