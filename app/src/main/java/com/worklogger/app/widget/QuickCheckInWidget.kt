package com.worklogger.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.worklogger.app.MainActivity
import com.worklogger.app.R

/**
 * 桌面小组件
 */
class QuickCheckInWidget : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onEnabled(context: Context) {
        // 第一个小组件被创建时调用
    }
    
    override fun onDisabled(context: Context) {
        // 最后一个小组件被删除时调用
    }
    
    companion object {
        private const val ACTION_QUICK_CHECK_IN = "com.worklogger.app.ACTION_QUICK_CHECK_IN"
        
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_checkin)
            
            // 设置点击事件 - 打开应用
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_CHECK_IN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_text, pendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
