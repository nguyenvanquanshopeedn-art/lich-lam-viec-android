package com.lichlamviec.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

data class RingtoneSettings(
    var ringtoneUri: String = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString() ?: "",
    var ringtoneTitle: String = "Nhạc chuông báo thức mặc định",
    var volumePercent: Int = 85,
    var vibrateEnabled: Boolean = true,
    var loopAlarm: Boolean = true
)

object RingtonePrefs {
    private const val PREFS_NAME = "lich_lam_viec_ringtone_prefs"
    private const val KEY_URI = "ringtone_uri"
    private const val KEY_TITLE = "ringtone_title"
    private const val KEY_VOLUME = "ringtone_volume"
    private const val KEY_VIBRATE = "ringtone_vibrate"
    private const val KEY_LOOP = "ringtone_loop"

    fun load(context: Context): RingtoneSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString()
            ?: ""
        return RingtoneSettings(
            ringtoneUri = prefs.getString(KEY_URI, defaultUri) ?: defaultUri,
            ringtoneTitle = prefs.getString(KEY_TITLE, "Nhạc chuông mặc định") ?: "Nhạc chuông mặc định",
            volumePercent = prefs.getInt(KEY_VOLUME, 85).coerceIn(0, 100),
            vibrateEnabled = prefs.getBoolean(KEY_VIBRATE, true),
            loopAlarm = prefs.getBoolean(KEY_LOOP, true)
        )
    }

    fun save(context: Context, settings: RingtoneSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, settings.ringtoneUri)
            .putString(KEY_TITLE, settings.ringtoneTitle)
            .putInt(KEY_VOLUME, settings.volumePercent.coerceIn(0, 100))
            .putBoolean(KEY_VIBRATE, settings.vibrateEnabled)
            .putBoolean(KEY_LOOP, settings.loopAlarm)
            .apply()
    }
}

object AlarmSoundPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    var isRinging: Boolean = false
        private set

    fun playPreview(context: Context, uriString: String, volumePercent: Int) {
        stop()
        val uri = parseUri(context, uriString) ?: return
        try {
            val vol = (volumePercent.coerceIn(0, 100)) / 100f
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setVolume(vol, vol)
                isLooping = false
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            isRinging = true
        } catch (_: Exception) {
            stop()
        }
    }

    fun playAlarm(context: Context, settings: RingtoneSettings) {
        stop()
        val uri = parseUri(context, settings.ringtoneUri) ?: return
        try {
            val vol = (settings.volumePercent.coerceIn(0, 100)) / 100f
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                setVolume(vol, vol)
                isLooping = settings.loopAlarm
                setOnCompletionListener {
                    if (!settings.loopAlarm) stop()
                }
                prepare()
                start()
            }
            isRinging = true
        } catch (_: Exception) {
            stop()
        }

        if (settings.vibrateEnabled) {
            startVibrate(context, settings.loopAlarm)
        }
    }

    fun stop() {
        isRinging = false
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        } finally {
            vibrator = null
        }
    }

    private fun parseUri(context: Context, uriString: String): Uri? {
        return try {
            if (uriString.isNotBlank()) Uri.parse(uriString)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } catch (_: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    private fun startVibrate(context: Context, repeat: Boolean) {
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib
            if (vib == null) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0L, 600L, 400L)
                val effect = if (repeat) {
                    VibrationEffect.createWaveform(pattern, 0)
                } else {
                    VibrationEffect.createOneShot(800L, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                if (repeat) {
                    vib.vibrate(longArrayOf(0L, 600L, 400L), 0)
                } else {
                    vib.vibrate(800L)
                }
            }
        } catch (_: Exception) {
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.lichlamviec.app.ACTION_STOP_ALARM") {
            AlarmSoundPlayer.stop()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
            return
        }

        val alarmId = intent.getIntExtra("EXTRA_ID", 1)
        val taskText = intent.getStringExtra("EXTRA_TASK") ?: "Nhắc nhở khung giờ làm việc"
        val slotLabel = intent.getStringExtra("EXTRA_SLOT") ?: ""
        val settings = RingtonePrefs.load(context)

        AlarmSoundPlayer.playAlarm(context, settings)

        val channelId = "lich_lam_viec_alarm_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Báo thức Lịch Làm Việc",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo và nhạc chuông khi tới khung giờ làm việc"
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.lichlamviec.app.ACTION_STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId + 500000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (slotLabel.isNotBlank()) "Báo thức: $slotLabel" else "Báo thức công việc")
            .setContentText(taskText.ifBlank { "Đã tới giờ làm việc theo kế hoạch" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(settings.loopAlarm)
            .setAutoCancel(!settings.loopAlarm)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tắt chuông", stopPendingIntent)
            .build()

        manager.notify(alarmId, notification)
    }
}

object AlarmHelper {
    fun scheduleAlarm(
        context: Context,
        alarmId: Int,
        hourOfDay: Int,
        minute: Int,
        task: String,
        slotLabel: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_ID", alarmId)
            putExtra("EXTRA_TASK", task)
            putExtra("EXTRA_SLOT", slotLabel)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (_: Exception) {
        }
    }

    fun cancelAlarm(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        AlarmSoundPlayer.stop()
    }
}
