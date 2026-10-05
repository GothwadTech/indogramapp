# Indogram Project Requirements

Act as an Android Kotlin engineer. Create a production-ready Android WebView application for our chat app with the following requirements:

## 1. Architecture & WebView Configuration:
- Configure a modern WebView with JavaScript enabled, DomStorage enabled, and Database enabled.
- Attach a WebChromeClient with onPermissionRequest overridden to automatically grant android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE and RESOURCE_VIDEO_CAPTURE for WebRTC audio/video calls.
- Ensure the WebView user agent includes "IndoGramAndroid" tag for environment detection.

## 2. JavaScript Interface Bridge:
- Add a JavascriptInterface named "AndroidBridge" with the following methods:
  * `getFCMToken()`: returns the cached Firebase Cloud Messaging registration token as a String.
  * `onUserLoggedIn(userId: String, phone: String, extraJson: String)`: stores the active logged-in user state.
  * `onUserLoggedOut()`: clears native user sessions and unregisters token if needed.
  * `onCallStateChanged(state: String, callDataJson: String)`: handles incoming/outgoing call notifications.
  * `showNotification(title: String, body: String, payloadJson: String)`: shows an Android native status bar notification.

## 3. Firebase Cloud Messaging (FCM):
- Implement FirebaseMessagingService (`onNewToken` and `onMessageReceived`).
- On new token, pass the token into the WebView via `evaluateJavascript("window.setDeviceFCMToken('$token')")`.
- In `onMessageReceived`, build and display a standard Android Notification with `NotificationCompat.Builder` (using `NotificationChannel` on Android 8.0+).
- Attach a `PendingIntent` to the notification with a target `"chatId"` extra.
- When the user taps the notification, the `MainActivity` should launch and call:
  `evaluateJavascript("window.onAndroidNotificationClick('$chatId')", null)`.

## 4. Runtime Permissions:
- Request `POST_NOTIFICATIONS` (Android 13+), `CAMERA`, and `RECORD_AUDIO` at runtime before initiating calls.
