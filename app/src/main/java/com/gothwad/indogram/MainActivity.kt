package com.gothwad.indogram

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gothwad.indogram.data.IndogramDatabase
import com.gothwad.indogram.data.IndogramRepository
import com.gothwad.indogram.ui.IndogramJavascriptInterface
import com.gothwad.indogram.ui.IndogramViewModel
import com.gothwad.indogram.ui.IndogramViewModelFactory
import com.gothwad.indogram.ui.theme.MyApplicationTheme
import com.gothwad.indogram.utils.IndogramNotificationHelper
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {

    companion object {
        private var activeActivity: WeakReference<MainActivity>? = null

        /**
         * Requirement 3.2:
         * Pass new FCM token into the WebView via window.setDeviceFCMToken('$token')
         */
        fun sendFCMTokenToWebView(token: String) {
            val activity = activeActivity?.get() ?: return
            activity.runOnUiThread {
                activity.injectFCMToken(token)
            }
        }
    }

    private var currentWebView: WebView? = null
    private var isPageFinishedLoading = false
    private var pendingNotificationChatId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        activeActivity = WeakReference(this)
        enableEdgeToEdge()

        // Setup global WebView ServiceWorker preferences for offline background caching
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val swController = ServiceWorkerController.getInstance()
                val swSettings = swController.serviceWorkerWebSettings
                
                val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                val actuallyOnline = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                swSettings.cacheMode = if (actuallyOnline) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
                swSettings.allowContentAccess = true
                swSettings.allowFileAccess = true
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error configuring ServiceWorkerController on launch", e)
            }
        }

        // Initialize notification channels on launch
        IndogramNotificationHelper.createNotificationChannel(applicationContext)

        // Setup repository
        val database = IndogramDatabase.getDatabase(applicationContext)
        val repository = IndogramRepository(database.indogramDao())

        // Check for chatId from launch intent
        handleIntentForChatId(intent)

        // Initialize Firebase token retrieval asynchronously
        try {
            val hasFirebase = try {
                if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:1234567890:android:e1234567890abcdef")
                        .setApiKey("placeholder-api-key-to-allow-init")
                        .setProjectId("indogram-placeholder")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(applicationContext, options)
                }
                true
            } catch (initEx: Exception) {
                android.util.Log.w("MainActivity", "Firebase dynamic init warning: ${initEx.message}")
                false
            }

            if (hasFirebase) {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        android.util.Log.d("MainActivity", "FCM token retrieved: $token")
                        val sharedPrefs = getSharedPreferences("indogram_prefs", Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString("fcm_token", token).apply()
                        injectFCMToken(token)
                    } else {
                        android.util.Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Firebase init exception", e)
        }

        setContent {
            val indogramViewModel: IndogramViewModel = viewModel(
                factory = IndogramViewModelFactory(application, repository)
            )

            val isDarkThemeOverride by indogramViewModel.isDarkThemeOverride.collectAsStateWithLifecycle()
            val systemIsDark = isSystemInDarkTheme()
            val useDarkTheme = isDarkThemeOverride ?: systemIsDark

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IndogramChatScreen(
                        viewModel = indogramViewModel,
                        isDarkTheme = useDarkTheme,
                        onWebViewReady = { webView ->
                            currentWebView = webView
                        },
                        onPageLoaded = {
                            isPageFinishedLoading = true
                            // If a notification click was queued, execute it now
                            pendingNotificationChatId?.let { id ->
                                notifyWebViewChatClick(id)
                                pendingNotificationChatId = null
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentForChatId(intent)
    }

    private fun handleIntentForChatId(intent: Intent?) {
        val chatId = intent?.getStringExtra("chatId")
        if (!chatId.isNullOrEmpty()) {
            if (isPageFinishedLoading && currentWebView != null) {
                notifyWebViewChatClick(chatId)
            } else {
                pendingNotificationChatId = chatId
            }
        }
    }

    /**
     * Requirement 3.5:
     * When user taps notification, call: evaluateJavascript("window.onAndroidNotificationClick('$chatId')", null)
     */
    private fun notifyWebViewChatClick(chatId: String) {
        runOnUiThread {
            currentWebView?.evaluateJavascript("window.onAndroidNotificationClick('$chatId')", null)
            android.util.Log.d("MainActivity", "Delivered onAndroidNotificationClick for chatId: $chatId")
        }
    }

    /**
     * Requirement 3.2:
     * evaluateJavascript("window.setDeviceFCMToken('$token')")
     */
    private fun injectFCMToken(token: String) {
        runOnUiThread {
            currentWebView?.evaluateJavascript("window.setDeviceFCMToken('$token')", null)
            android.util.Log.d("MainActivity", "Injected FCM token to WebView")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeActivity?.get() == this) {
            activeActivity = null
        }
        currentWebView = null
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IndogramChatScreen(
    viewModel: IndogramViewModel,
    isDarkTheme: Boolean,
    onWebViewReady: (WebView) -> Unit,
    onPageLoaded: () -> Unit
) {
    val context = LocalContext.current
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isError by viewModel.isWebViewError.collectAsStateWithLifecycle()
    val progress by viewModel.loadProgress.collectAsStateWithLifecycle()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var customFilePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            var results: Array<Uri>? = null
            if (data != null) {
                val dataString = data.dataString
                val clipData = data.clipData
                if (clipData != null) {
                    results = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
            customFilePathCallback?.onReceiveValue(results)
        } else {
            customFilePathCallback?.onReceiveValue(null)
        }
        customFilePathCallback = null
    }

    // Intercept back actions so page history goes back rather than exiting app
    BackHandler(enabled = webViewInstance != null) {
        val webView = webViewInstance
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            (context as? Activity)?.finish()
        }
    }

    // Requirement 4: Request POST_NOTIFICATIONS (Android 13+), CAMERA, and RECORD_AUDIO at runtime before initiating calls
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            android.util.Log.d("MainActivity", "User denied runtime permissions: $denied")
        }
    }

    LaunchedEffect(Unit) {
        val permissionsList = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungranted = permissionsList.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Solid Status Bar with theme matching background
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background)
        )

        // Web view / offline core screen area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!isError) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                            val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                            val activeNetwork = connectivityManager.activeNetwork
                            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                            val actuallyOnline = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                            // Requirement 1.1: JavaScript enabled, DomStorage enabled, Database enabled
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                setGeolocationEnabled(true)
                                loadsImagesAutomatically = true
                                useWideViewPort = true
                                loadWithOverviewMode = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                textZoom = 100
                                mediaPlaybackRequiresUserGesture = false

                                // Dynamic dark mode selection
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    try {
                                        isAlgorithmicDarkeningAllowed = isDarkTheme
                                    } catch (e: Exception) {
                                        // ignored
                                    }
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    try {
                                        @Suppress("DEPRECATION")
                                        forceDark = if (isDarkTheme) {
                                            WebSettings.FORCE_DARK_ON
                                        } else {
                                            WebSettings.FORCE_DARK_OFF
                                        }
                                    } catch (e: Exception) {
                                        // ignored
                                    }
                                }

                                cacheMode = if (actuallyOnline) {
                                    WebSettings.LOAD_DEFAULT
                                } else {
                                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                                }

                                // Requirement 1.3: User-Agent includes "IndoGramAndroid" tag for environment detection
                                val defaultUA = userAgentString
                                val cleanedUA = defaultUA
                                    .replace("; wv", "")
                                    .replace("Version/\\d+\\.\\d+\\s".toRegex(), "")
                                    .replace("Version/\\d+\\.\\d+".toRegex(), "")
                                val baseUA = if (cleanedUA.isNotEmpty()) cleanedUA else defaultUA
                                userAgentString = if (!baseUA.contains("IndoGramAndroid")) {
                                    "$baseUA IndoGramAndroid"
                                } else {
                                    baseUA
                                }
                            }

                            // Enable Cookies
                            val webViewRef = this
                            try {
                                CookieManager.getInstance().apply {
                                    setAcceptCookie(true)
                                    setAcceptThirdPartyCookies(webViewRef, true)
                                }
                            } catch (e: Exception) {
                                // ignore
                            }

                            // Requirement 1.2: WebChromeClient with onPermissionRequest overridden to automatically grant
                            // android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE and RESOURCE_VIDEO_CAPTURE
                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    if (request == null) return
                                    try {
                                        val grantedResources = mutableListOf<String>()
                                        for (resource in request.resources) {
                                            if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                                resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                                                resource == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) {
                                                grantedResources.add(resource)
                                            }
                                        }
                                        if (grantedResources.isNotEmpty()) {
                                            request.grant(grantedResources.toTypedArray())
                                        } else {
                                            request.grant(request.resources)
                                        }
                                        android.util.Log.d("MainActivity", "WebRTC permission granted automatically: ${grantedResources.joinToString()}")
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Error granting WebRTC permissions", e)
                                        try {
                                            request.deny()
                                        } catch (denyEx: Exception) {
                                            // ignore
                                        }
                                    }
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    viewModel.setLoadProgress(newProgress)
                                }

                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String?,
                                    callback: GeolocationPermissions.Callback?
                                ) {
                                    callback?.invoke(origin, true, false)
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    customFilePathCallback?.onReceiveValue(null)
                                    customFilePathCallback = filePathCallback

                                    try {
                                        val intent = fileChooserParams?.createIntent()
                                        if (intent != null) {
                                            fileChooserLauncher.launch(intent)
                                        } else {
                                            filePathCallback?.onReceiveValue(null)
                                            customFilePathCallback = null
                                            return false
                                        }
                                    } catch (e: Exception) {
                                        filePathCallback?.onReceiveValue(null)
                                        customFilePathCallback = null
                                        return false
                                    }
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    viewModel.setLoadProgress(15)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    viewModel.setLoadProgress(100)
                                    onPageLoaded()

                                    // Push cached FCM token if available
                                    val sharedPrefs = ctx.getSharedPreferences("indogram_prefs", Context.MODE_PRIVATE)
                                    val cachedToken = sharedPrefs.getString("fcm_token", null)
                                    if (!cachedToken.isNullOrEmpty()) {
                                        view?.evaluateJavascript("window.setDeviceFCMToken('$cachedToken')", null)
                                    }

                                    // Theme observer injection
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            function checkAndUpdateTheme() {
                                                var isDark = false;
                                                if (document.documentElement.classList.contains('dark') || 
                                                    document.body.classList.contains('dark') ||
                                                    document.documentElement.getAttribute('data-theme') === 'dark' ||
                                                    document.body.getAttribute('data-theme') === 'dark' ||
                                                    document.documentElement.classList.contains('theme-dark') ||
                                                    document.body.classList.contains('theme-dark')) {
                                                    isDark = true;
                                                } else {
                                                    try {
                                                        var bg = window.getComputedStyle(document.body).backgroundColor;
                                                        if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
                                                            var rgb = bg.match(/\d+/g);
                                                            if (rgb && rgb.length >= 3) {
                                                                var r = parseInt(rgb[0]);
                                                                var g = parseInt(rgb[1]);
                                                                var b = parseInt(rgb[2]);
                                                                var luma = (r * 299 + g * 587 + b * 114) / 1000;
                                                                if (luma < 120) {
                                                                    isDark = true;
                                                                }
                                                            }
                                                        }
                                                    } catch(e) {}
                                                }
                                                
                                                if (window.AndroidBridge && window.AndroidBridge.setTheme) {
                                                    window.AndroidBridge.setTheme(isDark);
                                                } else if (window.IndogramApp && window.IndogramApp.setTheme) {
                                                    window.IndogramApp.setTheme(isDark);
                                                }
                                            }
                                            checkAndUpdateTheme();
                                            try {
                                                var themeObserver = new MutationObserver(function() {
                                                    checkAndUpdateTheme();
                                                });
                                                themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'data-theme'] });
                                                if (document.body) {
                                                    themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class', 'data-theme'] });
                                                }
                                            } catch(err) {}
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
                                    if (request?.isForMainFrame == true) {
                                        viewModel.setWebViewError(true)
                                    }
                                }

                                @Suppress("OVERRIDE_DEPRECATION")
                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    if (failingUrl != null && failingUrl.trimEnd('/').equals(viewModel.targetUrl.trimEnd('/'), ignoreCase = true)) {
                                        viewModel.setWebViewError(true)
                                    }
                                }
                            }

                            // Requirement 2: JavascriptInterface named "AndroidBridge"
                            val bridge = IndogramJavascriptInterface(ctx, viewModel)
                            addJavascriptInterface(bridge, "AndroidBridge")
                            addJavascriptInterface(bridge, "IndogramApp")
                            addJavascriptInterface(bridge, "GrixApp")

                            loadUrl(viewModel.targetUrl)
                            webViewInstance = this
                            onWebViewReady(this)
                        }
                    },
                    update = { webView ->
                        webView.settings.cacheMode = if (isOnline) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                webView.settings.isAlgorithmicDarkeningAllowed = isDarkTheme
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                @Suppress("DEPRECATION")
                                webView.settings.forceDark = if (isDarkTheme) {
                                    WebSettings.FORCE_DARK_ON
                                } else {
                                    WebSettings.FORCE_DARK_OFF
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }

                        if (isOnline && isError) {
                            viewModel.setWebViewError(false)
                            webView.loadUrl(viewModel.targetUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("indogram_webview_panel")
                )

                // Fade-out loading spinner overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = progress < 100,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(400))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("page_loader_spinner")
                        )
                    }
                }
            } else {
                // Offline recovery screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Offline Mode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Text(
                            text = "Connection Offline",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Make sure your Wi-Fi or cellular network is active and try reloading.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                viewModel.setWebViewError(false)
                                if (webViewInstance?.url.isNullOrBlank()) {
                                    webViewInstance?.loadUrl(viewModel.targetUrl)
                                } else {
                                    webViewInstance?.reload()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("offline_retry_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry Connection")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Solid Navigation Bar spacer
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.safeDrawing)
                .background(MaterialTheme.colorScheme.background)
        )
    }
}
