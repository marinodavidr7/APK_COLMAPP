package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.font.FontFamily

@Composable
fun ColmaAppScreen(
  viewModel: WebViewModel,
  onShowFileChooser: ((ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean)? = null,
  nfcBridge: com.example.nfc.NfcBridge? = null,
  notificationBridge: com.example.notification.NotificationBridge? = null,
  biometricBridge: com.example.biometric.BiometricBridge? = null,
  onRequestBiometricAuth: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val activity = context as? android.app.Activity
  var backPressedTime by remember { mutableStateOf(0L) }
  var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

  // Hardware Back Button: navigate back within the web view history naturally
  BackHandler(enabled = true) {
    val wv = webViewRef
    if (wv != null && wv.canGoBack()) {
      // 1. Direct WebView history exists -> go back immediately without toast!
      wv.goBack()
    } else if (wv != null) {
      // 2. Query JavaScript to close modal / overlay or navigate client-side router
      wv.evaluateJavascript(
        """
        (function() {
          // Check for any visible close button (e.g. Fullscreen QR modal)
          var closeButtons = document.querySelectorAll('button[aria-label*="cerrar" i], button[aria-label*="close" i], .close-button, [data-dismiss], .modal-close, button.close, [class*="close"]');
          for (var i = 0; i < closeButtons.length; i++) {
            var b = closeButtons[i];
            if (b && (b.offsetParent !== null || b.getClientRects().length > 0)) {
              b.click();
              return true;
            }
          }
          // Check for any visible back button in page header
          var backButtons = document.querySelectorAll('button[aria-label*="atrás" i], button[aria-label*="back" i], [data-testid*="back"], .back-btn, .header-back, [class*="back"]');
          for (var j = 0; j < backButtons.length; j++) {
            var bb = backButtons[j];
            if (bb && (bb.offsetParent !== null || bb.getClientRects().length > 0)) {
              bb.click();
              return true;
            }
          }
          // Try standard window.history if there is history
          if (window.history && window.history.length > 1) {
            window.history.back();
            return true;
          }
          return false;
        })()
        """.trimIndent()
      ) { result ->
        val handledInJs = result?.trim()?.equals("true", ignoreCase = true) == true
        if (!handledInJs) {
          // Truly at the root screen of the app: double-tap to exit
          val currentTime = System.currentTimeMillis()
          if (currentTime - backPressedTime < 2000) {
            activity?.finish()
          } else {
            backPressedTime = currentTime
            Toast.makeText(context, "Presiona atrás de nuevo para salir", Toast.LENGTH_SHORT).show()
          }
        }
      }
    } else {
      activity?.finish()
    }
  }

  // 100% Native view: No top browser bar, seamless full-screen with system insets
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    // Web content rendering
    ColmaWebView(
      viewModel = viewModel,
      onShowFileChooser = onShowFileChooser,
      onWebViewReady = { webViewRef = it },
      nfcBridge = nfcBridge,
      notificationBridge = notificationBridge,
      biometricBridge = biometricBridge,
      modifier = Modifier
        .fillMaxSize()
        .testTag("colma_web_view")
    )

    // Subtle 2dp progress indicator at top during page navigation
    val animatedProgress by animateFloatAsState(
      targetValue = uiState.progress / 100f,
      label = "native_progress_bar"
    )

    AnimatedVisibility(
      visible = uiState.isLoading && uiState.progress in 1..99,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter)
    ) {
      LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
          .fillMaxWidth()
          .height(2.5.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent
      )
    }

    // Discreet floating pill when connection is lost
    AnimatedVisibility(
      visible = uiState.isOffline,
      enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 10.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shadowElevation = 4.dp
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "Sin conexión a internet",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }

    // Error recovery screen only if page completely fails to load
    AnimatedVisibility(
      visible = uiState.hasError && !uiState.isLoading,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      ErrorStateCard(
        errorMessage = uiState.errorMessage ?: "No pudimos conectar con ColmaApp. Revisa tu conexión a internet.",
        isMirrorActive = uiState.isMirrorActive,
        onRetry = { viewModel.reload() },
        onToggleMirror = { viewModel.toggleMirrorServer() },
        onOpenBrowser = { openInBrowser(context, uiState.currentUrl) }
      )
    }

    // NFC Tag Scan Result Dialog
    if (uiState.showNfcDialog && uiState.latestNfcTag != null) {
      val tag = uiState.latestNfcTag!!
      AlertDialog(
        onDismissRequest = { viewModel.dismissNfcDialog() },
        icon = {
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Nfc,
              contentDescription = "NFC",
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(28.dp)
            )
          }
        },
        title = {
          Text(
            text = if (tag.isNdef) "¡Etiqueta NDEF Leída!" else "¡Etiqueta NFC Leída!",
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 18.sp
          )
        },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = "ID de Etiqueta (UID):",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = tag.tagId,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = "Contenido NDEF:",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = tag.primaryText ?: tag.primaryUri ?: "Sin datos de texto",
                  fontSize = 14.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }

            if (tag.records.size > 1) {
              Text(
                text = "Registros (${tag.records.size}):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              tag.records.forEach { rec ->
                Text(
                  text = "• ${rec.type}: ${rec.payload}",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Text(
              text = "Tecnologías: ${tag.techList.joinToString(", ")}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.outline
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
              val clip = android.content.ClipData.newPlainText("NFC Tag Data", tag.primaryText ?: tag.tagId)
              clipboard.setPrimaryClip(clip)
              Toast.makeText(context, "Datos copiados al portapapeles", Toast.LENGTH_SHORT).show()
              viewModel.dismissNfcDialog()
            }
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copiar")
          }
        },
        dismissButton = {
          TextButton(onClick = { viewModel.dismissNfcDialog() }) {
            Text("Cerrar")
          }
        }
      )
    }

    // Biometric Security Lock Overlay
    AnimatedVisibility(
      visible = uiState.isBiometricLocked,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      BiometricLockOverlay(
        onUnlockClick = onRequestBiometricAuth,
        authFailed = uiState.biometricAuthFailed
      )
    }
  }
}

@Composable
private fun ErrorStateCard(
  errorMessage: String,
  isMirrorActive: Boolean,
  onRetry: () -> Unit,
  onToggleMirror: () -> Unit,
  onOpenBrowser: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp)),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(32.dp)
          )
        }

        Text(
          text = "No se pudo conectar a ColmaApp",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center
        )

        Text(
          text = errorMessage,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
          onClick = onRetry,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("error_retry_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Reintentar conexión")
        }

        OutlinedButton(
          onClick = onToggleMirror,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("switch_server_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (isMirrorActive) "Volver a servidor principal" else "Probar servidor alternativo"
          )
        }

        TextButton(
          onClick = onOpenBrowser,
          modifier = Modifier.testTag("open_external_browser_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Abrir en navegador externo")
        }
      }
    }
  }
}

private fun openInBrowser(context: Context, url: String) {
  try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
  } catch (e: Exception) {
    Toast.makeText(context, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
  }
}

@Composable
private fun BiometricLockOverlay(
  onUnlockClick: () -> Unit,
  authFailed: Boolean,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(28.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .size(96.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Fingerprint,
          contentDescription = "Desbloqueo biométrico",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(54.dp)
        )
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "ColmaApp Protegida",
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )

        Text(
          text = "Identifícate con tu huella digital, rostro o PIN para acceder a tu sesión y tarjeta VIP.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = 20.sp
        )
      }

      if (authFailed) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
          Text(
            text = "No se reconoció la autenticación. Intenta nuevamente.",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Button(
        onClick = onUnlockClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("unlock_biometric_button")
      ) {
        Icon(
          imageVector = Icons.Default.Lock,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Desbloquear con Biometría",
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }
  }
}
