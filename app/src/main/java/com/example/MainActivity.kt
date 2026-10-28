package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.biometric.BiometricAuthManager
import com.example.biometric.BiometricAvailability
import com.example.biometric.BiometricBridge
import com.example.nfc.NdefParser
import com.example.nfc.NfcBridge
import com.example.notification.NotificationBridge
import com.example.notification.NotificationHelper
import com.example.ui.ColmaAppScreen
import com.example.ui.WebViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

  private val webViewModel: WebViewModel by viewModels()
  private var filePathCallback: ValueCallback<Array<Uri>>? = null
  private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
  private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

  val notificationHelper by lazy { NotificationHelper(this) }
  private val nfcBridge by lazy { NfcBridge(this) }
  private val notificationBridge by lazy {
    NotificationBridge(this, notificationHelper) {
      requestNotificationPermission()
    }
  }

  val biometricAuthManager by lazy { BiometricAuthManager(this) }
  private val biometricBridge by lazy {
    BiometricBridge(biometricAuthManager) {
      triggerBiometricPrompt()
    }
  }

  private val nfcAdapter: NfcAdapter? by lazy {
    try {
      NfcAdapter.getDefaultAdapter(this)
    } catch (e: Exception) {
      null
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    notificationPermissionLauncher = registerForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (!granted) {
        Toast.makeText(
          this,
          "Las notificaciones están desactivadas. Actívalas en Ajustes para verlas en pantalla de bloqueo.",
          Toast.LENGTH_LONG
        ).show()
      }
    }

    fileChooserLauncher = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        val data: Intent? = result.data
        val uris: Array<Uri>? = when {
          data?.clipData != null -> {
            val count = data.clipData!!.itemCount
            Array(count) { i -> data.clipData!!.getItemAt(i).uri }
          }
          data?.data != null -> arrayOf(data.data!!)
          else -> null
        }
        filePathCallback?.onReceiveValue(uris)
      } else {
        filePathCallback?.onReceiveValue(null)
      }
      filePathCallback = null
    }

    setContent {
      MyApplicationTheme {
        ColmaAppScreen(
          viewModel = webViewModel,
          onShowFileChooser = { callback, params ->
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback

            try {
              val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
              }
              fileChooserLauncher.launch(intent)
              true
            } catch (e: Exception) {
              filePathCallback?.onReceiveValue(null)
              filePathCallback = null
              false
            }
          },
          nfcBridge = nfcBridge,
          notificationBridge = notificationBridge,
          biometricBridge = biometricBridge,
          onRequestBiometricAuth = { triggerBiometricPrompt() }
        )
      }
    }

    // Check Biometric availability on launch
    val availability = biometricAuthManager.checkBiometricAvailability()
    val hasBiometric = availability == BiometricAvailability.READY
    webViewModel.setBiometricAvailability(hasBiometric)
    if (hasBiometric) {
      triggerBiometricPrompt()
    }

    // Request notification permission on Android 13+
    requestNotificationPermission()

    // Process launch NFC intent if started from NFC tag tap
    processNfcIntent(intent)
  }

  fun triggerBiometricPrompt() {
    biometricAuthManager.authenticate(
      activity = this,
      title = "Acceso Seguro a ColmaApp",
      subtitle = "Verifica tu huella digital, rostro o PIN",
      onSuccess = {
        biometricBridge.isUnlocked = true
        webViewModel.onBiometricSuccess()
      },
      onError = { _, _ ->
        webViewModel.onBiometricFailed()
      },
      onFailed = {
        webViewModel.onBiometricFailed()
      }
    )
  }

  fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    enableNfcReaderMode()
  }

  override fun onPause() {
    super.onPause()
    disableNfcReaderMode()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    processNfcIntent(intent)
  }

  private fun enableNfcReaderMode() {
    val adapter = nfcAdapter ?: return
    if (!adapter.isEnabled) return

    val flags = NfcAdapter.FLAG_READER_NFC_A or
      NfcAdapter.FLAG_READER_NFC_B or
      NfcAdapter.FLAG_READER_NFC_F or
      NfcAdapter.FLAG_READER_NFC_V

    try {
      adapter.enableReaderMode(
        this,
        { tag -> handleNfcTag(tag) },
        flags,
        null
      )
    } catch (e: Exception) {
      // Graceful fallback if device restricts reader mode
    }
  }

  private fun disableNfcReaderMode() {
    try {
      nfcAdapter?.disableReaderMode(this)
    } catch (ignored: Exception) {}
  }

  private fun processNfcIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action ?: return
    if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
      action == NfcAdapter.ACTION_TAG_DISCOVERED ||
      action == NfcAdapter.ACTION_TECH_DISCOVERED
    ) {
      val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
      }
      if (tag != null) {
        handleNfcTag(tag)
      }
    }
  }

  private fun handleNfcTag(tag: Tag) {
    val tagData = NdefParser.parseTag(tag)
    vibrateFeedback()

    runOnUiThread {
      nfcBridge.lastTagData = tagData
      webViewModel.onNfcTagScanned(tagData)

      // Post native high-priority notification with lock screen visibility
      notificationHelper.showNotification(
        title = "ColmaApp: Tarjeta VIP detectada",
        body = "UID: ${tagData.tagId} · ${tagData.primaryText ?: "Lectura NFC NDEF completada"}",
        tag = "nfc_scan"
      )

      Toast.makeText(
        this,
        "NFC: ${if (tagData.isNdef) "Etiqueta NDEF" else "Etiqueta"} detectada",
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  private fun vibrateFeedback() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.vibrate(
          VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
      } else {
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(80)
      }
    } catch (ignored: Exception) {}
  }
}
