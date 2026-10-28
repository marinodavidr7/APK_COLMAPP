package com.example.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.atomic.AtomicInteger

class NotificationHelper(private val context: Context) {

  companion object {
    const val CHANNEL_ID = "colma_vip_notifications"
    const val CHANNEL_NAME = "Notificaciones ColmaApp"
    const val CHANNEL_DESC = "Avisos de tarjeta VIP, transacciones y alertas"
    private val idCounter = AtomicInteger(1000)
  }

  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  init {
    createNotificationChannel()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = CHANNEL_DESC
        // Explicitly enable full visibility on the lock screen
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        enableVibration(true)
        enableLights(true)
        lightColor = 0xFF1E88E5.toInt()
        setShowBadge(true)
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  fun areNotificationsEnabled(): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  fun showNotification(
    title: String,
    body: String,
    tag: String? = null,
    id: Int = idCounter.incrementAndGet()
  ) {
    if (!areNotificationsEnabled()) {
      return
    }

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("notification_title", title)
      putExtra("notification_body", body)
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      id,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(0xFF1E88E5.toInt())
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      // VISIBILITY_PUBLIC ensures the notification displays fully on the lock screen
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setCategory(NotificationCompat.CATEGORY_EVENT)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .build()

    try {
      if (tag != null) {
        notificationManager.notify(tag, id, notification)
      } else {
        notificationManager.notify(id, notification)
      }
    } catch (e: SecurityException) {
      // Handles permission edge case gracefully
    }
  }
}
