package id.powerscheduler.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CustomSchedule(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val activeCalendarDays: Set<Int>
)

data class ScheduleConfig(
    val masterEnabled: Boolean = true,
    val schedules: List<CustomSchedule> = DEFAULT_SCHEDULES,
    val countdownSeconds: Int = 30,
    val minBatteryPercent: Int = 15,
    val requireCharging: Boolean = false,
    val skipDuringCall: Boolean = true,
    val deferIfScreenOn: Boolean = true,
    val deferMinutes: Int = 10,
    val useAlarmClockApi: Boolean = true,
    val rescheduleAfterBoot: Boolean = true
)

val DEFAULT_SCHEDULES: List<CustomSchedule> = listOf(
        CustomSchedule(
            id = "sched-weekday-night",
            label = "Restart Dini Hari Kerja",
            enabled = true,
            hour = 3,
            minute = 0,
            activeCalendarDays = setOf(2, 3, 4, 5, 6)
        ),
        CustomSchedule(
            id = "sched-weekend-maint",
            label = "Pemeliharaan Akhir Pekan",
            enabled = true,
            hour = 4,
            minute = 30,
            activeCalendarDays = setOf(7, 1)
        )
    )

object SchedulePreferences {
    private const val PREF_NAME = "power_scheduler_prefs"

    fun load(context: Context): ScheduleConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString("schedules_json", null)
        val parsedSchedules = if (rawJson != null) {
            decodeSchedules(rawJson)
        } else {
            DEFAULT_SCHEDULES
        }

        return ScheduleConfig(
            masterEnabled = prefs.getBoolean("master_enabled", true),
            schedules = parsedSchedules,
            countdownSeconds = prefs.getInt("countdown_sec", 30),
            minBatteryPercent = prefs.getInt("min_battery", 15),
            requireCharging = prefs.getBoolean("require_charging", false),
            skipDuringCall = prefs.getBoolean("skip_call", true),
            deferIfScreenOn = prefs.getBoolean("defer_screen_on", true),
            deferMinutes = prefs.getInt("defer_minutes", 10),
            useAlarmClockApi = prefs.getBoolean("use_alarm_clock", true),
            rescheduleAfterBoot = prefs.getBoolean("reschedule_boot", true)
        )
    }

    fun save(context: Context, config: ScheduleConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("master_enabled", config.masterEnabled)
            .putString("schedules_json", encodeSchedules(config.schedules))
            .putInt("countdown_sec", config.countdownSeconds)
            .putInt("min_battery", config.minBatteryPercent)
            .putBoolean("require_charging", config.requireCharging)
            .putBoolean("skip_call", config.skipDuringCall)
            .putBoolean("defer_screen_on", config.deferIfScreenOn)
            .putInt("defer_minutes", config.deferMinutes)
            .putBoolean("use_alarm_clock", config.useAlarmClockApi)
            .putBoolean("reschedule_boot", config.rescheduleAfterBoot)
            .apply()
    }

    private fun encodeSchedules(schedules: List<CustomSchedule>): String {
        val arr = JSONArray()
        schedules.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("label", s.label)
            obj.put("enabled", s.enabled)
            obj.put("hour", s.hour)
            obj.put("minute", s.minute)
            obj.put("days", JSONArray(s.activeCalendarDays.toList()))
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeSchedules(json: String): List<CustomSchedule> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { index ->
                val obj = arr.getJSONObject(index)
                val daysArr = obj.getJSONArray("days")
                val daysSet = mutableSetOf<Int>()
                for (i in 0 until daysArr.length()) {
                    daysSet.add(daysArr.getInt(i))
                }
                CustomSchedule(
                    id = obj.getString("id"),
                    label = obj.getString("label"),
                    enabled = obj.getBoolean("enabled"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    activeCalendarDays = daysSet
                )
            }
        } catch (_: Exception) {
            DEFAULT_SCHEDULES
        }
    }
}
