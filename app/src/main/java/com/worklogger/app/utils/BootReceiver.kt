package com.worklogger.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机广播接收器
 * 用于在设备重启后重新设置提醒
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 重新初始化通知提醒
            val notificationHelper = NotificationHelper(context)
            // 通知服务会在下次设置时自动生效
        }
    }
}
