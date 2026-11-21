package id.powerscheduler.app.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import id.powerscheduler.app.data.SchedulePreferences
import id.powerscheduler.app.scheduler.RebootAlarmManager
import id.powerscheduler.app.service.RebootForegroundService

class RebootAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, RebootForegroundService::class.java).apply {
            putExtra(
                RebootAlarmManager.EXTRA_SCHEDULE_ID,
                intent.getStringExtra(RebootAlarmManager.EXTRA_SCHEDULE_ID)
            )
            putExtra(
                RebootAlarmManager.EXTRA_SCHEDULE_LABEL,
                intent.getStringExtra(RebootAlarmManager.EXTRA_SCHEDULE_LABEL)
            )
        }
        context.startForegroundService(serviceIntent)
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_SET ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        ) {
            val config = SchedulePreferences.load(context)
            if (config.masterEnabled && config.rescheduleAfterBoot) {
                RebootAlarmManager.scheduleNext(context)
            }
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val config = SchedulePreferences.load(context)
        val label = intent.getStringExtra(RebootAlarmManager.EXTRA_SCHEDULE_LABEL) ?: "Jadwal Restart"
        when (intent.action) {
            RebootForegroundService.ACTION_CANCEL -> {
                context.stopService(Intent(context, RebootForegroundService::class.java))
                RebootAlarmManager.scheduleNext(context)
            }
            RebootForegroundService.ACTION_SNOOZE -> {
                context.stopService(Intent(context, RebootForegroundService::class.java))
                RebootAlarmManager.scheduleSnooze(context, config.deferMinutes, label)
            }
        }
    }
}

