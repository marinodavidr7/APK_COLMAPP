package com.example.notification

import android.content.Context
import android.webkit.JavascriptInterface

class NotificationBridge(
  private val context: Context,
  private val notificationHelper: NotificationHelper,
  private val onRequestPermission: () -> Unit
) {

  @JavascriptInterface
  fun showNotification(title: String, body: String, tag: String?) {
    notificationHelper.showNotification(
      title = title,
      body = body,
      tag = tag?.takeIf { it.isNotBlank() }
    )
  }

  @JavascriptInterface
  fun areNotificationsEnabled(): Boolean {
    return notificationHelper.areNotificationsEnabled()
  }

  @JavascriptInterface
  fun requestPermission() {
    onRequestPermission()
  }
}
