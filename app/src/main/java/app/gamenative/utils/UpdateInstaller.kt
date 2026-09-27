package app.gamenative.utils

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.gamenative.BuildConfig
import app.gamenative.service.SteamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object UpdateInstaller {
    enum class Result {
        Started,
        NeedsPermission,
        Failed,
    }

    enum class ResumeAction {
        Idle,
        Started,
        PermissionDenied,
    }

    private var pendingApk: File? = null
    private var awaitingPermission = false

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (Float) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val apkFileName = "gamenative-v$versionName.apk"
            val destFile = File(context.cacheDir, apkFileName)

            Timber.i("Downloading update from URL: $downloadUrl")
            Timber.i("Saving to: ${destFile.absolutePath}")

            SteamService.fetchFile(
                url = downloadUrl,
                dest = destFile,
                onProgress = onProgress,
            )

            if (!destFile.exists() || destFile.length() == 0L) {
                Timber.e("Downloaded update is missing or empty: ${destFile.absolutePath}")
                return@withContext Result.Failed
            }

            Timber.i("Download complete: ${destFile.absolutePath}, size: ${destFile.length()} bytes")

            withContext(Dispatchers.Main) {
                if (!canRequestPackageInstalls(context)) {
                    pendingApk = destFile
                    awaitingPermission = true
                    Result.NeedsPermission
                } else {
                    pendingApk = null
                    awaitingPermission = false
                    if (installApk(context, destFile)) Result.Started else Result.Failed
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading/installing update")
            Result.Failed
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        startActivity(context, intent)
    }

    /**
     * Continues an update after the user returns from the install-permission screen.
     * Does nothing when no download is waiting.
     */
    fun onHostResume(context: Context): ResumeAction {
        val apk = pendingApk ?: return ResumeAction.Idle
        if (!canRequestPackageInstalls(context)) {
            if (!awaitingPermission) return ResumeAction.Idle
            awaitingPermission = false
            return ResumeAction.PermissionDenied
        }
        awaitingPermission = false
        pendingApk = null
        return if (installApk(context, apk)) ResumeAction.Started else ResumeAction.PermissionDenied
    }

    private fun canRequestPackageInstalls(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    private fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Timber.e("APK file is missing: ${apkFile.absolutePath}")
            return false
        }

        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                apkFile,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting FileProvider URI")
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resInfoList) {
            context.grantUriPermission(
                resolveInfo.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        return try {
            startActivity(context, intent)
            Timber.i("Install intent launched for ${apkFile.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error launching install intent")
            false
        }
    }

    private fun startActivity(context: Context, intent: Intent) {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
