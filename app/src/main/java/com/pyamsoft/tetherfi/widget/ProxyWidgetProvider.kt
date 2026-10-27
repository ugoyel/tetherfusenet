/*
 * Copyright 2026 pyamsoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pyamsoft.tetherfi.widget

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.CheckResult
import com.pyamsoft.pydroid.core.LintIgnoreTooGenericExceptionCaught
import com.pyamsoft.tetherfi.ObjectGraph
import com.pyamsoft.tetherfi.R
import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.status.RunningStatus
import com.pyamsoft.tetherfi.service.tile.TileHandler
import com.pyamsoft.tetherfi.tile.ProxyTileAction
import com.pyamsoft.tetherfi.tile.ProxyTileActionKeys
import com.pyamsoft.tetherfi.tile.ProxyTileActivity
import javax.inject.Inject

/**
 * Home screen widget that toggles the hotspot.
 *
 * A tap launches the same [ProxyTileActivity] the Quick Settings tile uses, so the hotspot is
 * started with whatever settings the user has saved in the main app. Launching an Activity from a
 * widget click is allowed on every Android version, unlike starting a Foreground Service from the
 * background.
 */
internal class ProxyWidgetProvider internal constructor() : AppWidgetProvider() {

  @Inject @JvmField internal var tileHandler: TileHandler? = null

  @CheckResult
  private fun resolveStatus(context: Context): RunningStatus {
    try {
      val app = context.applicationContext as Application
      ObjectGraph.ApplicationScope.retrieve(app).plusWidget().create().inject(this)
      return tileHandler?.getOverallStatus() ?: RunningStatus.NotRunning
    } catch (@LintIgnoreTooGenericExceptionCaught e: Throwable) {
      Timber.e(e) { "Failed to inject Widget, application not ready!" }
      return RunningStatus.NotRunning
    } finally {
      tileHandler = null
    }
  }

  override fun onUpdate(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
  ) {
    Timber.d { "Widget update requested: ${appWidgetIds.toList()}" }
    render(
        context = context,
        manager = appWidgetManager,
        widgetIds = appWidgetIds,
        status = resolveStatus(context),
    )
  }

  companion object {

    private const val REQUEST_CODE_TOGGLE = 31337

    /** Redraw every placed widget for the given status. No-op if no widgets are placed. */
    @JvmStatic
    fun updateWidgets(context: Context, status: RunningStatus) {
      val appContext = context.applicationContext
      try {
        val manager = AppWidgetManager.getInstance(appContext) ?: return
        val ids =
            manager.getAppWidgetIds(ComponentName(appContext, ProxyWidgetProvider::class.java))
        if (ids.isEmpty()) {
          return
        }

        render(
            context = appContext,
            manager = manager,
            widgetIds = ids,
            status = status,
        )
      } catch (@LintIgnoreTooGenericExceptionCaught e: Throwable) {
        Timber.e(e) { "Unable to update Widgets" }
      }
    }

    @JvmStatic
    private fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
        status: RunningStatus,
    ) {
      val isActive: Boolean
      val statusText: String
      when (status) {
        is RunningStatus.Error -> {
          isActive = false
          statusText = context.getString(R.string.widget_status_error)
        }
        is RunningStatus.NotRunning -> {
          isActive = false
          statusText = context.getString(R.string.widget_status_off)
        }
        is RunningStatus.Running -> {
          isActive = true
          statusText = context.getString(R.string.widget_status_on)
        }
        is RunningStatus.Starting -> {
          isActive = false
          statusText = context.getString(R.string.widget_status_starting)
        }
        is RunningStatus.Stopping -> {
          isActive = true
          statusText = context.getString(R.string.widget_status_stopping)
        }
      }

      val views =
          RemoteViews(context.packageName, R.layout.widget_proxy).apply {
            setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (isActive) R.drawable.widget_background_active
                else R.drawable.widget_background_inactive,
            )
            setTextViewText(R.id.widget_status, statusText)
            setContentDescription(
                R.id.widget_root,
                "${context.getString(R.string.app_name)}: $statusText",
            )
            setOnClickPendingIntent(R.id.widget_root, createTogglePendingIntent(context))
          }

      for (id in widgetIds) {
        manager.updateAppWidget(id, views)
      }
    }

    @JvmStatic
    @CheckResult
    private fun createTogglePendingIntent(context: Context): PendingIntent {
      // Same Intent the Quick Settings tile builds in DefaultProxyTileActivityLauncher
      val intent =
          Intent(context, ProxyTileActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(ProxyTileActionKeys.KEY_ACTION, ProxyTileAction.TOGGLE.name)
          }

      return PendingIntent.getActivity(
          context,
          REQUEST_CODE_TOGGLE,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }
  }
}
