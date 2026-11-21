package id.powerscheduler.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import id.powerscheduler.app.data.ScheduleConfig
import id.powerscheduler.app.data.SchedulePreferences
import id.powerscheduler.app.receiver.NotificationActionReceiver
import id.powerscheduler.app.scheduler.RebootAlarmManager


class RebootForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "power_scheduler_reboot_channel"
        const val NOTIF_ID = 7001
        const val ACTION_CANCEL = "id.powerscheduler.app.ACTION_CANCEL_REBOOT"
        const val ACTION_SNOOZE = "id.powerscheduler.app.ACTION_SNOOZE_REBOOT"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()

        val scheduleLabel = intent?.getStringExtra(RebootAlarmManager.EXTRA_SCHEDULE_LABEL)
            ?: "Jadwal Restart Otomatis"

        val initialNotif = buildCountdownNotification(scheduleLabel, "Memeriksa kondisi perangkat...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, initialNotif)
        }

        if (intent?.action == ACTION_CANCEL) {
            countdownJob?.cancel()
            RebootAlarmManager.scheduleNext(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val config = SchedulePreferences.load(this)
        if (!passesPreflightChecks(config, scheduleLabel)) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock(config.countdownSeconds + 30)
        startCountdownAndReboot(config, scheduleLabel)

        return START_NOT_STICKY
    }

    private fun passesPreflightChecks(config: ScheduleConfig, scheduleLabel: String): Boolean {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

        if (batteryPct < config.minBatteryPercent) {
            RebootAlarmManager.scheduleNext(this)
            updateStatusNotification(
                scheduleLabel,
                "Restart dibatalkan: baterai $batteryPct% di bawah batas ${config.minBatteryPercent}%."
            )
            return false
        }

        if (config.requireCharging) {
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            if (plugged == 0) {
                RebootAlarmManager.scheduleNext(this)
                updateStatusNotification(scheduleLabel, "Restart dibatalkan: perangkat tidak sedang diisi daya.")
                return false
            }
        }

        if (config.skipDuringCall) {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) {
                RebootAlarmManager.scheduleSnooze(this, config.deferMinutes, scheduleLabel)
                updateStatusNotification(
                    scheduleLabel,
                    "Restart ditunda ${config.deferMinutes} menit karena panggilan aktif."
                )
                return false
            }
        }

        if (config.deferIfScreenOn) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) {
                RebootAlarmManager.scheduleSnooze(this, config.deferMinutes, scheduleLabel)
                updateStatusNotification(
                    scheduleLabel,
                    "Layar sedang aktif. Restart ditunda ${config.deferMinutes} menit."
                )
                return false
            }
        }

        return true
    }

    private fun startCountdownAndReboot(config: ScheduleConfig, scheduleLabel: String) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (sec in config.countdownSeconds downTo 1) {
                if (!isActive) return@launch
                updateStatusNotification(scheduleLabel, "Perangkat akan restart otomatis dalam $sec detik.")
                delay(1000L)
            }
            // Jadwalkan alarm berikutnya di antara daftar jadwal kustom sebelum restart dijalankan
            RebootAlarmManager.scheduleNext(this@RebootForegroundService)
            executeDeviceReboot()
            stopSelf()
        }
    }

    private fun executeDeviceReboot() {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "svc power reboot"))
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
            }
        } catch (e: Exception) {
            updateStatusNotification("Gagal restart Root: ${e.message}")
        }
    }

    private fun acquireWakeLock(seconds: Int) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "id.powerscheduler.app:RebootWakeLock").apply {
            acquire(seconds * 1000L)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Penjadwal Restart Otomatis",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Peringatan hitungan mundur sebelum perangkat di-restart otomatis"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildCountdownNotification(title: String, message: String): Notification {
        val cancelIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, NotificationActionReceiver::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, NotificationActionReceiver::class.java).setAction(ACTION_SNOOZE).apply {
                putExtra(RebootAlarmManager.EXTRA_SCHEDULE_LABEL, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentTitle("Power Scheduler · $title")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(0, "Tunda 10m", snoozeIntent)
            .addAction(0, "Batalkan", cancelIntent)
            .build()
    }

    private fun updateStatusNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildCountdownNotification(title, message))
    }

    private fun updateStatusNotification(message: String) {
        updateStatusNotification("Power Scheduler", message)
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        scope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }
}
