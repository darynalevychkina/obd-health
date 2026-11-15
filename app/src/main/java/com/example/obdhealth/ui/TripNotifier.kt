package com.example.obdhealth.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.obdhealth.MainActivity

/** Сповіщення в шторці, поки триває поїздка: з прогрес-баром до зарахування в норму авто. */
class TripNotifier(private val ctx: Context) {
    private val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "OBD Health", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    fun update(title: String, text: String, progress: Int, max: Int) {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(max, progress.coerceIn(0, max), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(ID, n)
        } catch (_: SecurityException) {
            // немає дозволу на сповіщення: просто пропускаємо
        }
    }

    fun cancel() = nm.cancel(ID)

    /** Чи дозволені сповіщення для застосунку (дозвіл + налаштування системи). */
    fun enabled(): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Відкриває системні налаштування сповіщень цього застосунку. */
    fun openSettings() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL = "trip"
        private const val ID = 1001
    }
}
