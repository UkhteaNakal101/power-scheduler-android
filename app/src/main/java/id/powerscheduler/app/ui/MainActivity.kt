package id.powerscheduler.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.powerscheduler.app.data.CustomSchedule
import id.powerscheduler.app.data.ScheduleConfig
import id.powerscheduler.app.data.SchedulePreferences
import id.powerscheduler.app.scheduler.NextTriggerResult
import id.powerscheduler.app.scheduler.RebootAlarmManager
import id.powerscheduler.app.service.RebootForegroundService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Status diperiksa pada UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                PowerSchedulerScreen()
            }
        }
    }

    @Composable
    private fun PowerSchedulerScreen() {
        var config by remember { mutableStateOf(SchedulePreferences.load(this)) }
        var nextTrigger by remember {
            mutableStateOf<NextTriggerResult?>(
                if (config.masterEnabled) RebootAlarmManager.scheduleNext(this) else null
            )
        }
        var showAddDialog by remember { mutableStateOf(false) }

        fun applyConfig(updated: ScheduleConfig) {
            config = updated
            SchedulePreferences.save(this@MainActivity, updated)
            nextTrigger = if (updated.masterEnabled) {
                RebootAlarmManager.scheduleNext(this@MainActivity)
            } else {
                RebootAlarmManager.cancel(this@MainActivity)
                null
            }
        }

        val nextLabel = remember(nextTrigger) {
            nextTrigger?.let {
                val dateText = SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale("id", "ID"))
                    .format(Date(it.triggerAtMillis))
                "${it.schedule.label} · $dateText"
            } ?: "Tidak ada jadwal aktif"
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Power Scheduler", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Multi-Jadwal Restart Otomatis · Android 9–17",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aktifkan Penjadwalan Utama")
                        Switch(
                            checked = config.masterEnabled,
                            onCheckedChange = { checked ->
                                applyConfig(config.copy(masterEnabled = checked))
                            }
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Restart Terdekat Berikutnya", style = MaterialTheme.typography.labelLarge)
                            Text(nextLabel, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daftar Jadwal Kustom (${config.schedules.size})", style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = { showAddDialog = true }) {
                            Text("+ Tambah Jadwal")
                        }
                    }
                }

                items(config.schedules, key = { it.id }) { schedule ->
                    ScheduleItemCard(
                        schedule = schedule,
                        onToggle = { enabled ->
                            val updatedList = config.schedules.map {
                                if (it.id == schedule.id) it.copy(enabled = enabled) else it
                            }
                            applyConfig(config.copy(schedules = updatedList))
                        },
                        onEditTime = {
                            TimePickerDialog(
                                this@MainActivity,
                                { _, h, m ->
                                    val updatedList = config.schedules.map {
                                        if (it.id == schedule.id) it.copy(hour = h, minute = m) else it
                                    }
                                    applyConfig(config.copy(schedules = updatedList))
                                },
                                schedule.hour,
                                schedule.minute,
                                true
                            ).show()
                        },
                        onToggleDay = { calDay ->
                            val nextDays = if (schedule.activeCalendarDays.contains(calDay)) {
                                schedule.activeCalendarDays - calDay
                            } else {
                                schedule.activeCalendarDays + calDay
                            }
                            val updatedList = config.schedules.map {
                                if (it.id == schedule.id) it.copy(activeCalendarDays = nextDays) else it
                            }
                            applyConfig(config.copy(schedules = updatedList))
                        },
                        onDelete = {
                            val updatedList = config.schedules.filterNot { it.id == schedule.id }
                            applyConfig(config.copy(schedules = updatedList))
                        }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !RebootAlarmManager.canScheduleExact(this@MainActivity)) {
                    item {
                        OutlinedButton(
                            onClick = {
                                startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Izinkan Alarm Tepat Waktu (Android 12–17)")
                        }
                    }
                }

                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    item {
                        OutlinedButton(
                            onClick = {
                                startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Kecualikan dari Optimasi Baterai")
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            startForegroundService(
                                Intent(this@MainActivity, RebootForegroundService::class.java).apply {
                                    putExtra(
                                        RebootAlarmManager.EXTRA_SCHEDULE_LABEL,
                                        nextTrigger?.schedule?.label ?: "Uji Manual"
                                    )
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uji Hitungan Mundur Sekarang")
                    }
                }
            }
        }

        if (showAddDialog) {
            AddScheduleDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { newSchedule ->
                    applyConfig(config.copy(schedules = config.schedules + newSchedule))
                    showAddDialog = false
                }
            )
        }
    }

    @Composable
    private fun ScheduleItemCard(
        schedule: CustomSchedule,
        onToggle: (Boolean) -> Unit,
        onEditTime: () -> Unit,
        onToggleDay: (Int) -> Unit,
        onDelete: () -> Unit
    ) {
        val dayLabels = listOf(
            Calendar.MONDAY to "Sen",
            Calendar.TUESDAY to "Sel",
            Calendar.WEDNESDAY to "Rab",
            Calendar.THURSDAY to "Kam",
            Calendar.FRIDAY to "Jum",
            Calendar.SATURDAY to "Sab",
            Calendar.SUNDAY to "Min"
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(schedule.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            String.format(Locale.US, "%02d:%02d", schedule.hour, schedule.minute),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Switch(checked = schedule.enabled, onCheckedChange = onToggle)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEach { (calDay, label) ->
                        val selected = schedule.activeCalendarDays.contains(calDay)
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleDay(calDay) },
                            label = { Text(label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditTime) { Text("Ubah Jam") }
                    TextButton(onClick = onDelete) { Text("Hapus") }
                }
            }
        }
    }

    @Composable
    private fun AddScheduleDialog(
        onDismiss: () -> Unit,
        onConfirm: (CustomSchedule) -> Unit
    ) {
        var label by remember { mutableStateOf("Jadwal Restart Baru") }
        var hour by remember { mutableStateOf(3) }
        var minute by remember { mutableStateOf(0) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Tambah Jadwal Kustom") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Nama Jadwal") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                this@MainActivity,
                                { _, h, m ->
                                    hour = h
                                    minute = m
                                },
                                hour,
                                minute,
                                true
                            ).show()
                        }
                    ) {
                        Text(String.format(Locale.US, "Pilih Jam: %02d:%02d", hour, minute))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm(
                            CustomSchedule(
                                id = UUID.randomUUID().toString(),
                                label = label.ifBlank { "Jadwal Restart" },
                                enabled = true,
                                hour = hour,
                                minute = minute,
                                activeCalendarDays = setOf(
                                    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                                    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
                                )
                            )
                        )
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        )
    }
}
