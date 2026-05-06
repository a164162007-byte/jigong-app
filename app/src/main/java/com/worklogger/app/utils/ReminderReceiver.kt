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
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val helper = NotificationHelper(context)
        
        when (intent.action) {
            ACTION_OFF_WORK -> {
                helper.showOffWorkReminder()
            }
            ACTION_MISSED_DAY -> {
                // 检查是否今天还没记工
                CoroutineScope(Dispatchers.IO).launch {
                    val db = com.worklogger.app.data.local.AppDatabase.getInstance(context)
                    val records = db.workRecordDao().getRecordsByDateSync(DateUtils.today())
                    if (records.isEmpty()) {
                        helper.showMissedDayReminder()
                    }
                }
            }
        }
    }
}
