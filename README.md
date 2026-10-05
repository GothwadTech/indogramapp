# Indogram - The Telegram Client

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-FF6F00?style=for-the-badge)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)

**A modern, offline-first Android Telegram client built with Jetpack Compose, Material 3, Room Database, and Firebase Cloud Messaging.**

[Features](#-key-features) • [Tech Stack](#-tech-stack) • [Getting Started](#-getting-started) • [CI/CD & Releases](#-cicd--signing-secrets) • [Architecture](#-architecture)

</div>

---

## 📱 Overview

**Indogram** is an open-source Swadeshi Telegram client for Android developed by Gothwad Technologies. It loads the Indogram Telegram Web client from `indogram.gothwadtech.com` inside an advanced, hardware-accelerated WebView integrated seamlessly with native Android capabilities including Jetpack Compose UI, Room Database offline persistence, ServiceWorker background caching, and Firebase Push Notifications.

---

## ✨ Key Features

- 💬 **Telegram Web Integration**: Direct, high-performance integration with `indogram.gothwadtech.com` providing full Telegram messaging capabilities.
- 🎨 **Material 3 & Edge-to-Edge**: Modern UI following Material Design 3 guidelines with dynamic theme switching and edge-to-edge layout.
- ⚡ **Offline-First Reliability**: Local Room database persistence for offline drafts and notification logging, combined with WebView ServiceWorker caching.
- 🔔 **Push Notifications**: Firebase Cloud Messaging (FCM) integration with dedicated Android notification channels.
- 🔄 **Modern Architecture**: Clean MVVM architecture utilizing Kotlin Coroutines, `StateFlow`, and `collectAsStateWithLifecycle`.
- 🛡️ **Automated CI/CD**: Pre-configured GitHub Actions workflows for building signed Release APKs, Debug APKs, and Play Store AAB bundles.

---

## 🛠 Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **App Name** | Indogram |
| **Package Name** | `com.gothwad.indogram` |
| **Target URL** | `https://indogram.gothwadtech.com` |
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose (BOM), Material 3 |
| **Architecture** | MVVM (Model-View-ViewModel) + Repository Pattern |
| **Local Storage** | Room Database + SQLite |
| **Push Notifications** | Firebase Cloud Messaging (FCM) |
| **Build System** | Gradle 9.3.1 (Kotlin DSL), Android Gradle Plugin (AGP) |
| **Testing** | Robolectric, Roborazzi, JUnit 4, AndroidX Test |

---

## 📂 Project Structure

```text
indogramapp/
├── .github/
│   └── workflows/
│       ├── build.yml          # Build & Sign APK / AAB on push to main
│       └── release.yml        # Build & Publish to GitHub Releases on tag (v*)
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/        # App assets & graphics
│   │   │   ├── java/com/gothwad/indogram/
│   │   │   │   ├── data/      # Room Database, DAO, Repository
│   │   │   │   ├── ui/        # Compose Screens, ViewModels, Theme
│   │   │   │   └── utils/     # FCM Service, Notification Helpers
│   │   │   ├── res/           # Layouts, mipmaps, drawables, strings
│   │   │   └── AndroidManifest.xml
│   │   └── test/              # Local JVM and Robolectric unit tests
│   ├── build.gradle.kts       # App module configuration (com.gothwad.indogram)
│   └── proguard-rules.pro     # ProGuard / R8 rules
├── gradle/
│   ├── libs.versions.toml     # Version catalog
│   └── wrapper/               # Gradle wrapper executable & properties
├── build.gradle.kts           # Root build configuration
├── settings.gradle.kts        # Project settings & plugin resolution
└── README.md                  # Documentation
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Ladybug (2024.2.1+) or newer recommended.
- **JDK**: Java 17 or Java 21 (Temurin / Eclipse Adoptium recommended).
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

4. **Run Unit Tests:**
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

> **Note**: For security, if `RELEASE_KEYSTORE_BASE64` or `KEYSTORE_PASSWORD` is not configured, the release build step will automatically abort to prevent deploying unverified or improperly signed builds.

---

## 🏷️ Triggering a Release

To trigger an official GitHub Release:

1. Create a version tag locally:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. The `Release to GitHub Releases` workflow will automatically:
   - Validate signing secrets.
   - Self-heal Gradle wrapper if needed.
   - Build signed Release APK (`Indogram-v1.0.0-Release.apk`), Debug APK (`Indogram-v1.0.0-Debug.apk`), and Play Store AAB (`Indogram-v1.0.0-PlayStore.aab`).
   - Publish a new GitHub Release with generated release notes and downloadable assets.

---

## 📄 License

Distributed under the Apache License 2.0. See `LICENSE` for more information.
