package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebUiState(
  val currentUrl: String = WebViewModel.PRIMARY_URL,
  val pageTitle: String = "ColmaApp",
  val isLoading: Boolean = true,
  val progress: Int = 0,
  val hasError: Boolean = false,
  val errorMessage: String? = null,
  val isOffline: Boolean = false,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val isMirrorActive: Boolean = false,
  val latestNfcTag: com.example.nfc.NfcTagData? = null,
  val showNfcDialog: Boolean = false,
  val isBiometricLocked: Boolean = false,
  val isBiometricAvailable: Boolean = false,
  val biometricAuthFailed: Boolean = false,
)

sealed interface WebCommand {
  data class LoadUrl(val url: String) : WebCommand
  data object Reload : WebCommand
  data object GoBack : WebCommand
  data object GoForward : WebCommand
  data object ClearCache : WebCommand
}

class WebViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    const val PRIMARY_URL = "https://colmaapp.ai.studio/"
    const val MIRROR_URL = "https://ais-pre-l3obsctke3u5kt6e3dsurn-549728009085.us-west2.run.app"
  }

  private val networkMonitor = NetworkMonitor(application)

  private val _uiState = MutableStateFlow(WebUiState())
  val uiState: StateFlow<WebUiState> = _uiState.asStateFlow()

  private val _commands = MutableSharedFlow<WebCommand>(extraBufferCapacity = 10)
  val commands: SharedFlow<WebCommand> = _commands.asSharedFlow()

  init {
    viewModelScope.launch {
      networkMonitor.isOnline.collect { online ->
        _uiState.update { state ->
          state.copy(
            isOffline = !online,
            // If we regained internet and had an error, try reloading automatically
            hasError = if (online && state.hasError) false else state.hasError
          )
        }
        if (online && _uiState.value.hasError) {
          reload()
        }
      }
    }
  }

  fun onPageStarted(url: String?) {
    _uiState.update {
      it.copy(
        currentUrl = url ?: it.currentUrl,
        isLoading = true,
        progress = 10,
        hasError = false,
        errorMessage = null
      )
    }
  }

  fun onProgressChanged(progress: Int) {
    _uiState.update {
      it.copy(
        progress = progress,
        isLoading = progress < 100
      )
    }
  }

  fun onPageFinished(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
    _uiState.update {
      it.copy(
        currentUrl = url ?: it.currentUrl,
        isLoading = false,
        progress = 100,
        canGoBack = canGoBack,
        canGoForward = canGoForward
      )
    }
  }

  fun updateCanGoBack(canGoBack: Boolean) {
    _uiState.update { it.copy(canGoBack = canGoBack) }
  }

  fun onReceivedError(description: String?) {
    _uiState.update {
      it.copy(
        isLoading = false,
        hasError = true,
        errorMessage = description ?: "No se pudo establecer conexión con el servidor."
      )
    }
  }

  fun onTitleReceived(title: String?) {
    if (!title.isNullOrBlank() && !title.startsWith("http")) {
      _uiState.update { it.copy(pageTitle = title) }
    }
  }

  fun reload() {
    _uiState.update { it.copy(hasError = false, errorMessage = null, isLoading = true) }
    _commands.tryEmit(WebCommand.Reload)
  }

  fun goHome() {
    val targetUrl = if (_uiState.value.isMirrorActive) MIRROR_URL else PRIMARY_URL
    _uiState.update { it.copy(hasError = false, errorMessage = null) }
    _commands.tryEmit(WebCommand.LoadUrl(targetUrl))
  }

  fun loadUrl(url: String) {
    _uiState.update { it.copy(hasError = false, errorMessage = null) }
    _commands.tryEmit(WebCommand.LoadUrl(url))
  }

  fun toggleMirrorServer() {
    val willBeMirror = !_uiState.value.isMirrorActive
    val targetUrl = if (willBeMirror) MIRROR_URL else PRIMARY_URL
    _uiState.update {
      it.copy(
        isMirrorActive = willBeMirror,
        currentUrl = targetUrl,
        hasError = false,
        errorMessage = null
      )
    }
    _commands.tryEmit(WebCommand.LoadUrl(targetUrl))
  }

  fun goBack() {
    _commands.tryEmit(WebCommand.GoBack)
  }

  fun goForward() {
    _commands.tryEmit(WebCommand.GoForward)
  }

  fun clearCache() {
    _commands.tryEmit(WebCommand.ClearCache)
  }

  fun onNfcTagScanned(tagData: com.example.nfc.NfcTagData) {
    _uiState.update {
      it.copy(
        latestNfcTag = tagData,
        showNfcDialog = true
      )
    }
  }

  fun dismissNfcDialog() {
    _uiState.update { it.copy(showNfcDialog = false) }
  }

  fun setBiometricAvailability(available: Boolean) {
    _uiState.update {
      it.copy(
        isBiometricAvailable = available,
        isBiometricLocked = available
      )
    }
  }

  fun onBiometricSuccess() {
    _uiState.update {
      it.copy(
        isBiometricLocked = false,
        biometricAuthFailed = false
      )
    }
  }

  fun onBiometricFailed() {
    _uiState.update { it.copy(biometricAuthFailed = true) }
  }

  fun lockApp() {
    if (_uiState.value.isBiometricAvailable) {
      _uiState.update { it.copy(isBiometricLocked = true) }
    }
  }
}
