# Indogram - The Telegram Client

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-FF6F00?style=for-the-badge)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)

**A modern, open-source Android Telegram client built with Jetpack Compose, Material 3, Room Database, Firebase Cloud Messaging, and In-App GitHub Auto-Updates.**

[Features](#-key-features) • [Tech Stack](#-tech-stack) • [JavaScript Bridge API](#-javascript-bridge-androidbridge) • [Website Integration Prompt](#-prompt-for-website-developer--ai) • [Auto-Updates](#-in-app-github-auto-updates) • [Getting Started](#-getting-started) • [CI/CD & Releases](#-cicd--signing-secrets)

</div>

---

## 📱 Overview

**Indogram** is an open-source Swadeshi Telegram client for Android developed by Gothwad Technologies. It loads the Indogram Telegram Web client from `indogram.gothwadtech.com` inside an advanced, hardware-accelerated WebView integrated seamlessly with native Android capabilities including Jetpack Compose UI, Room Database offline persistence, WebRTC video/audio calling, Firebase Push Notifications, and direct GiHub Release In-App Auto-Updates.

---

## ✨ Key Features

- 💬 **Telegram Web Integration**: Direct, high-performance integration with `indogram.gothwadtech.com` providing full Telegram messaging capabilities.
- 🔄 **GitHub In-App Auto-Updates**: Automatically checks `GothwadTech/indogramapp` releases, displays a Material 3 changelog dialog, requests install permission, downloads the APK, and launches the native package installer.
- 🌉 **Bidirectional JavaScript Bridge (`AndroidBridge`)**: Rich JavaScript interface allowing the web app to query app version, check updates, manage permissions, control native themes, and send notifications.
- 📹 **WebRTC Calling Support**: Custom `WebChromeClient` automatically grants audio/video permissions for seamless WebRTC voice and video calls.
- 🎨 **Material 3 & Edge-to-Edge**: Modern UI following Material Design 3 guidelines with dynamic theme switching and edge-to-edge layout.
- ⚡ **Offline-First Reliability**: Local Room database persistence for offline drafts and notification logging, combined with WebView ServiceWorker caching.
- 🔔 **Push Notifications**: Firebase Cloud Messaging (FCM) integration with dedicated high-priority channels for chats and calls.

---

## 🛠 Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **App Name** | Indogram |
| **Package Name** | `com.gothwad.indogram` |
| **Target URL** | `https://indogram.gothwadtech.com` |
| **GitHub Repository** | `GothwadTech/indogramapp` |
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose (BOM), Material 3 |
| **Architecture** | MVVM (Model-View-ViewModel) + Repository Pattern |
| **Local Storage** | Room Database + SQLite |
| **Push Notifications** | Firebase Cloud Messaging (FCM) |
| **Build System** | Gradle (Kotlin DSL), Android Gradle Plugin (AGP) |
| **Dynamic Versions** | Supports `-PversionCode=...` & `-PversionName=...` via CLI/CI |

---

## 🌉 JavaScript Bridge (`AndroidBridge`)

When Indogram loads `indogram.gothwadtech.com`, it injects a JavaScript bridge object into `window.AndroidBridge` (with backward-compatible alias `window.IndogramApp`).

### Environment Detection

```javascript
// Check 1: User Agent contains "IndoGramAndroid"
const isIndogramAppByUA = navigator.userAgent.includes("IndoGramAndroid");

// Check 2: JavaScript bridge presence
const isIndogramAppByBridge = typeof window.AndroidBridge !== "undefined" && window.AndroidBridge.isAndroidApp?.();

// Combined check
export const isIndogramAndroid = isIndogramAppByUA || isIndogramAppByBridge;
```

### Available Native Methods

| Method | Return Type | Description |
| :--- | :--- | :--- |
| `window.AndroidBridge.isAndroidApp()` | `boolean` | Always returns `true`. Ideal for environment detection. |
| `window.AndroidBridge.getAppVersion()` | `string` | Returns current installed Android version name (e.g. `"1.0.0"`). |
| `window.AndroidBridge.getAppVersionCode()` | `number` | Returns current installed Android version code (e.g. `1`). |
| `window.AndroidBridge.checkForUpdates()` | `void` | Triggers a GitHub update check. Shows native update popup if available, and fires `window.onUpdateCheckResult(data)`. |
| `window.AndroidBridge.hasInstallPermission()` | `boolean` | Returns `true` if "Install Unknown Apps" permission is granted on Android 8.0+. |
| `window.AndroidBridge.requestInstallPermission()` | `void` | Opens Android system settings directly to allow installing APK updates for Indogram. |
| `window.AndroidBridge.getFCMToken()` | `string` | Returns cached Firebase Cloud Messaging registration token. |
| `window.AndroidBridge.onUserLoggedIn(userId, phone, extraJson)` | `void` | Stores active user login state natively. |
| `window.AndroidBridge.onUserLoggedOut()` | `void` | Clears native user sessions and stops call notifications. |
| `window.AndroidBridge.onCallStateChanged(state, callDataJson)` | `void` | Controls native high-priority incoming call banner and ringtone (`"incoming"`, `"ended"`, etc.). |
| `window.AndroidBridge.showNotification(title, body, payloadJson)` | `void` | Shows a native Android status bar notification. |
| `window.AndroidBridge.setTheme(isDark: boolean)` | `void` | Synchronizes Android container status bar and background with web theme. |

### Web Window Callbacks (Received from Android)

1. **Update Result Callback**:
   ```javascript
   window.onUpdateCheckResult = function(result) {
     // result is a JSON object:
     // {
     //   hasUpdate: true,
     //   latestVersion: "1.0.1",
     //   currentVersion: "1.0.0",
     //   downloadUrl: "https://github.com/...",
     //   releaseNotes: "Bug fixes & new features"
     // }
     console.log("Update check:", result);
   };
   ```

2. **FCM Token Push Callback**:
   ```javascript
   window.setDeviceFCMToken = function(token) {
     console.log("Received native FCM Token:", token);
     // Send token to your backend server
   };
   ```

3. **Notification Click Navigation**:
   ```javascript
   window.onAndroidNotificationClick = function(chatId) {
     console.log("User tapped notification for chat:", chatId);
     // Navigate directly to the chat
   };
   ```

---

## 🤖 Prompt for Website Developer / AI

Copy and paste the prompt below into the AI or developer working on the Indogram website (`indogram.gothwadtech.com`):

```markdown
Add an "Android App Settings & Updates" section to our website's Settings page that only shows when the user is using our official Android app.

Requirements:
1. Environment Detection:
   - Check if running inside our Android app using:
     const isAndroidApp = navigator.userAgent.includes("IndoGramAndroid") || (typeof window.AndroidBridge !== "undefined");
   - If FALSE, hide this section completely.
   - If TRUE, render a dedicated card in Settings titled "Indogram Android App".

2. App Information & Version Display:
   - Current Version: window.AndroidBridge.getAppVersion() (e.g. "1.0.0")
   - Version Code: window.AndroidBridge.getAppVersionCode()
   - "Install Unknown Apps" Permission Status:
     - Check: window.AndroidBridge.hasInstallPermission()
     - If false, show a badge "Permission Required for Updates" with a button: "Grant Permission" calling window.AndroidBridge.requestInstallPermission().

3. Check for Updates Button:
   - Provide a button "Check for Updates" with a loading spinner.
   - When clicked, call window.AndroidBridge.checkForUpdates().
   - Listen for the native response via window.onUpdateCheckResult = function(data) { ... }:
     - If data.hasUpdate is true:
       Show a badge: "Update Available: v" + data.latestVersion.
       Display the changelog (data.releaseNotes) and a button to re-trigger the native updater dialog.
     - If data.hasUpdate is false:
       Show a green checkmark: "You are on the latest version!".

4. Notifications & Sessions:
   - Ensure that after user login, call window.AndroidBridge.onUserLoggedIn(userId, phone, JSON.stringify(userData)).
   - On logout, call window.AndroidBridge.onUserLoggedOut().
   - Register window.setDeviceFCMToken(token) to sync the device push token with our backend.
   - Register window.onAndroidNotificationClick(chatId) to automatically open the target chat when the user taps an Android notification.
```

---

## 🔄 In-App GitHub Auto-Updates

Indogram connects directly to GitHub Releases at `GothwadTech/indogramapp`:

1. **Automatic Check on Launch**: 2 seconds after startup, the app checks `https://api.github.com/repos/GothwadTech/indogramapp/releases/latest`.
2. **Version Comparison**: Compares `BuildConfig.VERSION_NAME` with the latest release tag (e.g. `v1.0.1` vs `1.0.0`).
3. **Material 3 Dialog**: If a newer release is found, a Material 3 dialog shows the version number, changelog/notes, and an "Update Now" button.
4. **Self-Contained Installer**:
   - Checks `canRequestPackageInstalls()`. If disabled, opens Settings to toggle permission.
   - Downloads the `.apk` asset with progress tracking.
   - Launches Android Package Installer using `FileProvider` (`com.gothwad.indogram.fileprovider`).

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Ladybug (2024.2.1+) or newer recommended.
- **JDK**: Java 17 or Java 21.
- **Android SDK**: API Level 35 (compileSdk & targetSdk), Minimum API Level 23.

### Local Installation & Build

1. **Clone the repository:**
   ```bash
   git clone https://github.com/GothwadTech/indogramapp.git
   cd indogramapp
   ```

2. **Setup environment variables:**
   ```bash
   cp .env.example .env
   ```
   By default, `TARGET_URL` is set to `https://indogram.gothwadtech.com`.

3. **Build the Debug APK:**
   ```bash
   chmod +x ./gradlew
   ./gradlew assembleDebug
   ```
   The debug APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

4. **Dynamic Versioning (GitHub Actions CI/CD):**
   ```bash
   ./gradlew assembleRelease -PversionCode=2 -PversionName=1.0.1
   ```
   Or using environment variables:
   ```bash
   VERSION_CODE=2 VERSION_NAME=1.0.1 ./gradlew assembleRelease
   ```

5. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 🔐 CI/CD & Signing Secrets

The repository includes pre-configured GitHub Actions workflows for continuous integration and automated release deployments.

### Required GitHub Secrets

To build and sign Release APKs & Play Store AAB bundles automatically, add the following secrets to your GitHub repository under **Settings > Secrets and variables > Actions**:

| Secret Name | Description | Required |
| :--- | :--- | :---: |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release `.jks` or `.keystore` file | **Yes** |
| `KEYSTORE_PASSWORD` | Password for your release keystore | **Yes** |
| `KEY_ALIAS` | Key alias name inside the keystore | Optional |
| `KEY_PASSWORD` | Password for the key alias | Optional |

---

## 🏷️ Triggering a Release

To trigger an official GitHub Release:

1. Create and push a version tag:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
2. The `Release to GitHub Releases` workflow will automatically:
   - Build signed Release APK (`Indogram-v1.0.1-Release.apk`), Debug APK (`Indogram-v1.0.1-Debug.apk`), and Play Store AAB.
   - Publish a new GitHub Release with downloadable APK assets.
   - Indogram app instances will automatically detect this release and prompt users to update!

---

## 📄 License

Distributed under the Apache License 2.0. See `LICENSE` for more information.
