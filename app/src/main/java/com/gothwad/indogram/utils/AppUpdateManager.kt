package com.gothwad.indogram.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.gothwad.indogram.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val version: String,
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val assetName: String,
    val isNewer: Boolean
)

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Check GitHub Releases for the latest version.
     */
    suspend fun checkForUpdates(context: Context): AppReleaseInfo? = withContext(Dispatchers.IO) {
        val currentVersion = BuildConfig.VERSION_NAME
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "IndoGramAndroid/$currentVersion")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub release check failed: HTTP ${response.code}")
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyString)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "No changelog provided.")

            // Clean version tag (strip 'v' prefix if present)
            val releaseVersion = if (tagName.startsWith("v", ignoreCase = true)) {
                tagName.substring(1)
            } else {
                tagName
            }

            // Find the best APK download asset (prioritizing Release over Debug)
            var apkDownloadUrl = ""
            var apkName = "indogram-update.apk"
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        if (name.contains("Release", ignoreCase = true)) {
                            apkDownloadUrl = url
                            apkName = name
                            break // Perfect match found!
                        } else if (apkDownloadUrl.isEmpty()) {
                            apkDownloadUrl = url
                            apkName = name
                        }
                    }
                }
            }

            // Fallback: If no assets array or no APK in assets, look at html_url
            if (apkDownloadUrl.isEmpty()) {
                apkDownloadUrl = json.optString("html_url", "https://github.com/$owner/$repo/releases/latest")
            }

            val isNewer = isNewerVersion(releaseVersion, currentVersion)
            Log.d(TAG, "Checked update: current=$currentVersion, remote=$releaseVersion, isNewer=$isNewer, url=$apkDownloadUrl")

            AppReleaseInfo(
                tagName = tagName,
                version = releaseVersion,
                title = title,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl,
                assetName = apkName,
                isNewer = isNewer
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking updates from GitHub", e)
            null
        }
    }

    /**
     * SemVer comparator: returns true if candidate version > current version
     */
    fun isNewerVersion(candidate: String, current: String): Boolean {
        try {
            val candidateParts = candidate.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = current.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(candidateParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val candNum = candidateParts.getOrElse(i) { 0 }
                val currNum = currentParts.getOrElse(i) { 0 }
                if (candNum > currNum) return true
                if (candNum < currNum) return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error comparing version candidate: $candidate with current: $current", e)
        }
        return false
    }

    /**
     * Checks if user has granted "Install Unknown Apps" permission.
     */
    fun hasInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Prompts the user to grant "Install Unknown Apps" permission for this app.
     */
    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }

    /**
     * Download the APK with progress streaming and launch the system package installer.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "IndoGramAndroid/${BuildConfig.VERSION_NAME}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onError("Download failed: HTTP ${response.code}")
                }
                return@withContext
            }

            val body = response.body ?: run {
                withContext(Dispatchers.Main) {
                    onError("Empty response body from download")
                }
                return@withContext
            }

            val totalBytes = body.contentLength()
            val updateDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
            if (!updateDir.exists()) {
                updateDir.mkdirs()
            }

            val apkFile = File(updateDir, "Indogram-latest.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalBytes > 0) {
                    val progressPercent = ((totalRead * 100) / totalBytes).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progressPercent)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                onProgress(100)
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading and installing APK", e)
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Failed to download update")
            }
        }
    }

    /**
     * Trigger Android system package installer via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
        }
    }
}
