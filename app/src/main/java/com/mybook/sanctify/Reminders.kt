package com.mybook.sanctify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/** Конфигурация ежедневного напоминания. */
data class ReminderConfig(val on: Boolean, val hour: Int, val minute: Int)

object Reminders {
    private const val CHANNEL = "reminders"
    const val READING = "reading"
    const val PRAYER = "prayer"
    const val DUE = "due"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun config(ctx: Context, kind: String): ReminderConfig {
        val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val defH = if (kind == READING) 7 else 21
        val defM = if (kind == READING) 30 else 0
        return ReminderConfig(p.getBoolean("rem_${kind}_on", false), p.getInt("rem_${kind}_h", defH), p.getInt("rem_${kind}_m", defM))
    }

    fun saveConfig(ctx: Context, kind: String, c: ReminderConfig) {
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("rem_${kind}_on", c.on).putInt("rem_${kind}_h", c.hour).putInt("rem_${kind}_m", c.minute).apply()
        if (c.on) scheduleDaily(ctx, kind, c.hour, c.minute) else cancel(ctx, kind, 0)
    }

    private fun pending(ctx: Context, kind: String, id: Long, title: String = ""): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setData(Uri.parse("sanctify://reminder/$kind/$id"))
            .putExtra("kind", kind).putExtra("title", title)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun set(ctx: Context, at: Long, pi: PendingIntent) {
        // Неточный будильник (допускает небольшое смещение) — не требует особого разрешения.
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun scheduleDaily(ctx: Context, kind: String, hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        set(ctx, cal.timeInMillis, pending(ctx, kind, 0))
    }

    fun cancel(ctx: Context, kind: String, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, kind, id))
    }

    fun scheduleDue(ctx: Context, entryId: Long, title: String, at: Long) {
        if (at > System.currentTimeMillis()) set(ctx, at, pending(ctx, DUE, entryId, title))
    }

    fun cancelDue(ctx: Context, entryId: Long) = cancel(ctx, DUE, entryId)

    suspend fun rescheduleAll(ctx: Context) {
        for (kind in listOf(READING, PRAYER)) {
            val c = config(ctx, kind)
            if (c.on) scheduleDaily(ctx, kind, c.hour, c.minute)
        }
        val dao = (ctx.applicationContext as App).db.dao()
        dao.upcomingDue(System.currentTimeMillis()).forEach { scheduleDue(ctx, it.id, it.title, it.dueAt) }
    }

    fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (kind) {
                    Reminders.READING -> {
                        Reminders.notify(ctx, 1, "Время читать Библию", "Откройте приложение и продолжите чтение.")
                        val c = Reminders.config(ctx, kind)
                        if (c.on) Reminders.scheduleDaily(ctx, kind, c.hour, c.minute)
                    }
                    Reminders.PRAYER -> {
                        val n = (ctx.applicationContext as App).db.dao().openPrayersCount()
                        Reminders.notify(
                            ctx, 2, "Время молитвы",
                            if (n > 0) "Молитвенных нужд в списке: $n." else "Помолитесь и запишите мысли в дневник.",
                        )
                        val c = Reminders.config(ctx, kind)
                        if (c.on) Reminders.scheduleDaily(ctx, kind, c.hour, c.minute)
                    }
                    Reminders.DUE -> {
                        val title = intent.getStringExtra("title").orEmpty()
                        Reminders.notify(ctx, 1000 + (title.hashCode() and 0xFFFF), "Срок молитвенной нужды", title.ifBlank { "Наступил срок" })
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.rescheduleAll(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}
