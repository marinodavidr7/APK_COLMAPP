package com.example.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.collectLatest

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ColmaWebView(
  viewModel: WebViewModel,
  onShowFileChooser: ((ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean)? = null,
  onWebViewReady: ((WebView) -> Unit)? = null,
  nfcBridge: com.example.nfc.NfcBridge? = null,
  notificationBridge: com.example.notification.NotificationBridge? = null,
  biometricBridge: com.example.biometric.BiometricBridge? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val webView = remember {
    WebView(context).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
      setBackgroundColor(android.graphics.Color.WHITE)
      overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
      isVerticalScrollBarEnabled = false
      isHorizontalScrollBarEnabled = false
    }.also { onWebViewReady?.invoke(it) }
  }

  // Setup WebView settings and clients
  DisposableEffect(webView) {
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.allowFileAccess = false
    settings.allowContentAccess = true
    settings.mediaPlaybackRequiresUserGesture = false
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      settings.safeBrowsingEnabled = false
    }

    val defaultUa = settings.userAgentString
    settings.userAgentString = "$defaultUa ColmaApp-Android/1.0"

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val bridge = nfcBridge ?: com.example.nfc.NfcBridge(context)
    webView.addJavascriptInterface(bridge, "AndroidNfc")

    val notifBridge = notificationBridge ?: com.example.notification.NotificationBridge(
      context,
      com.example.notification.NotificationHelper(context)
    ) {}
    webView.addJavascriptInterface(notifBridge, "AndroidNotifications")

    if (biometricBridge != null) {
      webView.addJavascriptInterface(biometricBridge, "AndroidBiometric")
    }

    webView.addJavascriptInterface(object {
      @android.webkit.JavascriptInterface
      fun onHistoryUpdated(canGoBack: Boolean) {
        viewModel.updateCanGoBack(canGoBack)
      }
    }, "AndroidNavigation")

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val uri = request.url

        val scheme = uri.scheme?.lowercase() ?: ""
        if (scheme == "tel" || scheme == "mailto" || scheme == "whatsapp" || scheme == "sms" || scheme == "intent") {
          try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            context.startActivity(intent)
            return true
          } catch (e: Exception) {
            try {
              val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
              context.startActivity(fallbackIntent)
              return true
            } catch (ignored: ActivityNotFoundException) {
              Toast.makeText(context, "No hay aplicación disponible para este enlace", Toast.LENGTH_SHORT).show()
              return true
            }
          }
        }

        // Open non-http schemes in system
        if (scheme != "http" && scheme != "https") {
          try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
            return true
          } catch (e: Exception) {
            return false
          }
        }

        return false
      }

      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        viewModel.onPageStarted(url)
      }

      override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        viewModel.onPageFinished(
          url = url,
          canGoBack = view?.canGoBack() == true,
          canGoForward = view?.canGoForward() == true
        )
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        viewModel.onPageFinished(
          url = url,
          canGoBack = view?.canGoBack() == true,
          canGoForward = view?.canGoForward() == true
        )
        view?.evaluateJavascript(
          """
          (function() {
            if (window._colmaHistorySetup) return;
            window._colmaHistorySetup = true;
            var push = history.pushState;
            history.pushState = function() {
              var r = push.apply(this, arguments);
              if (window.AndroidNavigation) window.AndroidNavigation.onHistoryUpdated(true);
              return r;
            };
            window.addEventListener('popstate', function() {
              if (window.AndroidNavigation) window.AndroidNavigation.onHistoryUpdated(history.length > 1);
            });

            // HTML5 Native Notification polyfill
            window.Notification = function(title, options) {
              options = options || {};
              var body = options.body || "";
              var tag = options.tag || "";
              if (window.AndroidNotifications) {
                window.AndroidNotifications.showNotification(title, body, tag);
              }
            };
            window.Notification.permission = "granted";
            window.Notification.requestPermission = function(cb) {
              if (window.AndroidNotifications) {
                window.AndroidNotifications.requestPermission();
              }
              if (cb) cb("granted");
              return Promise.resolve("granted");
            };
          })();
          """.trimIndent(),
          null
        )
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
      ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
          viewModel.onReceivedError(error?.description?.toString())
        }
      }

      override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
      ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 500) {
          viewModel.onReceivedError("Error del servidor (${errorResponse?.statusCode}): ${errorResponse?.reasonPhrase}")
        }
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        viewModel.onProgressChanged(newProgress)
      }

      override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        viewModel.onTitleReceived(title)
      }

      override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
      ): Boolean {
        return onShowFileChooser?.invoke(filePathCallback, fileChooserParams) ?: super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
      }

      override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
      ) {
        callback?.invoke(origin, true, false)
      }
    }

    // Initial load
    webView.loadUrl(WebViewModel.PRIMARY_URL)

    onDispose {
      webView.stopLoading()
      webView.destroy()
    }
  }

  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.isOffline) {
    webView.settings.cacheMode = if (uiState.isOffline) {
      WebSettings.LOAD_CACHE_ELSE_NETWORK
    } else {
      WebSettings.LOAD_DEFAULT
    }
  }

  // Handle ViewModel commands
  LaunchedEffect(webView, viewModel) {
    viewModel.commands.collectLatest { command ->
      when (command) {
        is WebCommand.LoadUrl -> webView.loadUrl(command.url)
        is WebCommand.Reload -> webView.reload()
        is WebCommand.GoBack -> {
          if (webView.canGoBack()) {
            webView.goBack()
          } else {
            webView.evaluateJavascript("if (window.history.length > 1) { window.history.back(); }", null)
          }
        }
        is WebCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
        is WebCommand.ClearCache -> {
          webView.clearCache(true)
          CookieManager.getInstance().removeAllCookies(null)
          webView.reload()
        }
      }
    }
  }

  AndroidView(
    factory = { webView },
    modifier = modifier.fillMaxSize()
  )
}
