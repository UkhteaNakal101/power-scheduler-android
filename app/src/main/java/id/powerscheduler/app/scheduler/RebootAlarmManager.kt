package id.powerscheduler.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import id.powerscheduler.app.data.CustomSchedule
import id.powerscheduler.app.data.ScheduleConfig
import id.powerscheduler.app.data.SchedulePreferences
import id.powerscheduler.app.receiver.RebootAlarmReceiver
import id.powerscheduler.app.ui.MainActivity
import java.util.Calendar

data class NextTriggerResult(
    val schedule: CustomSchedule,
    val triggerAtMillis: Long
)

object RebootAlarmManager {
    private const val REQUEST_CODE_NEXT_REBOOT = 9017
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    const val EXTRA_SCHEDULE_LABEL = "extra_schedule_label"

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleNext(context: Context, customTriggerAtMillis: Long? = null, snoozeLabel: String? = null): NextTriggerResult? {
        val config = SchedulePreferences.load(context)
        if (!config.masterEnabled) {
            cancel(context)
            return null
        }

        val resolved: NextTriggerResult = if (customTriggerAtMillis != null) {
            val fallbackSchedule = config.schedules.firstOrNull { it.enabled }
                ?: return null
            NextTriggerResult(
                schedule = fallbackSchedule.copy(label = snoozeLabel ?: fallbackSchedule.label),
                triggerAtMillis = customTriggerAtMillis
            )
        } else {
            computeEarliestTrigger(config) ?: run {
                cancel(context)
                return null
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RebootAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULE_ID, resolved.schedule.id)
            putExtra(EXTRA_SCHEDULE_LABEL, resolved.schedule.label)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NEXT_REBOOT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Fallback bila izin SCHEDULE_EXACT_ALARM belum diaktifkan di Android 12–17
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, resolved.triggerAtMillis, pendingIntent)
            return resolved
        }

        if (config.useAlarmClockApi) {
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val info = AlarmManager.AlarmClockInfo(resolved.triggerAtMillis, showIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                resolved.triggerAtMillis,
                pendingIntent
            )
        }

        return resolved
    }

    fun scheduleSnooze(context: Context, minutes: Int, label: String): NextTriggerResult? {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        return scheduleNext(context, customTriggerAtMillis = triggerAt, snoozeLabel = "$label (Ditunda)")
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RebootAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NEXT_REBOOT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun computeEarliestTrigger(
        config: ScheduleConfig,
        nowMillis: Long = System.currentTimeMillis()
    ): NextTriggerResult? {
        if (!config.masterEnabled) return null

        var best: NextTriggerResult? = null
        for (schedule in config.schedules) {
            if (!schedule.enabled || schedule.activeCalendarDays.isEmpty()) continue
            val nextForSchedule = computeNextForSchedule(schedule, nowMillis) ?: continue
            if (best == null || nextForSchedule < best.triggerAtMillis) {
                best = NextTriggerResult(schedule = schedule, triggerAtMillis = nextForSchedule)
            }
        }
        return best
    }

    fun computeNextForSchedule(schedule: CustomSchedule, nowMillis: Long): Long? {
        val base = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, schedule.hour)
            set(Calendar.MINUTE, schedule.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (offsetDays in 0..7) {
            val check = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offsetDays)
            }
            val dayOfWeek = check.get(Calendar.DAY_OF_WEEK)
            if (check.timeInMillis > nowMillis + 5_000L && schedule.activeCalendarDays.contains(dayOfWeek)) {
                return check.timeInMillis
            }
        }
        return null
    }
}
