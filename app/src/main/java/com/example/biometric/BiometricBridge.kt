package com.example.biometric

import android.webkit.JavascriptInterface

class BiometricBridge(
  private val authManager: BiometricAuthManager,
  private val onTriggerAuth: () -> Unit
) {

  @Volatile
  var isUnlocked: Boolean = false

  @JavascriptInterface
  fun isBiometricAvailable(): Boolean {
    return authManager.checkBiometricAvailability() == BiometricAvailability.READY
  }

  @JavascriptInterface
  fun isAuthenticated(): Boolean {
    return isUnlocked
  }

  @JavascriptInterface
  fun authenticate() {
    onTriggerAuth()
  }
}
