package com.example.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability {
  READY,
  NONE_ENROLLED,
  NOT_SUPPORTED
}

class BiometricAuthManager(private val context: Context) {

  private val biometricManager = BiometricManager.from(context)

  fun checkBiometricAvailability(): BiometricAvailability {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.DEVICE_CREDENTIAL

    return when (biometricManager.canAuthenticate(authenticators)) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.READY
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
      else -> BiometricAvailability.NOT_SUPPORTED
    }
  }

  fun authenticate(
    activity: FragmentActivity,
    title: String = "Acceso Seguro a ColmaApp",
    subtitle: String = "Verifica tu identidad para continuar",
    onSuccess: () -> Unit,
    onError: (errorCode: Int, errString: CharSequence) -> Unit,
    onFailed: () -> Unit = {}
  ) {
    val executor = ContextCompat.getMainExecutor(activity)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        onSuccess()
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        onError(errorCode, errString)
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        onFailed()
      }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setDescription("Usa tu huella dactilar, reconocimiento facial o PIN del dispositivo")
      .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL
      )
      .build()

    val biometricPrompt = BiometricPrompt(activity, executor, callback)
    biometricPrompt.authenticate(promptInfo)
  }
}
