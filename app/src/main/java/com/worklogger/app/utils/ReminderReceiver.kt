package com.worklogger.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 提醒广播接收器
 */
class ReminderReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_OFF_WORK = "com.worklogger.app.ACTION_OFF_WORK"
        const val ACTION_MISSED_DAY = "com.worklogger.app.ACTION_MISSED_DAY"
        const val ACTION_STANDARD_WORK = "com.worklogger.app.ACTION_STANDARD_WORK"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val helper = NotificationHelper(context)
        
        when (intent.action) {
            ACTION_OFF_WORK -> {
                helper.showOffWorkReminder()
                pendingResult.finish()
            }
            ACTION_MISSED_DAY -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = com.worklogger.app.data.local.AppDatabase.getInstance(context)
                        val records = db.workRecordDao().getRecordsByDate(DateUtils.today())
                        // 检查当天是否有标准工记录（非加班、非手动折算）
                        val hasStandard = records.any { !it.isOvertime && !it.isManual && !it.isDeleted }
                        if (!hasStandard) {
                            helper.showMissedDayReminder()
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_STANDARD_WORK -> {
                helper.showStandardWorkReminder()
                pendingResult.finish()
            }
            else -> pendingResult.finish()
        }
    }
}
