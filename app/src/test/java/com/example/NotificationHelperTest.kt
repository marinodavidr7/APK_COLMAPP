package com.example

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.notification.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationHelperTest {

  @Test
  fun `notification channel has high importance and public lockscreen visibility`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val helper = NotificationHelper(context)

    val notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
      assertNotNull(channel)
      assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
      assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
    }
  }

  @Test
  fun `showNotification executes cleanly without throwing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val helper = NotificationHelper(context)

    helper.showNotification(
      title = "Prueba ColmaApp",
      body = "Notificación nativa para pantalla de bloqueo"
    )

    val notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    assertNotNull(notificationManager)
  }
}
