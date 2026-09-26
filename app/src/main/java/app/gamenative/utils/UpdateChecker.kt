package app.gamenative.utils

import android.content.Context
import app.gamenative.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateInfo(
    val updateAvailable: Boolean,
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String? = null
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

object UpdateChecker {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(Constants.Misc.UPDATE_CHECK_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GameNative-AndroidAuto")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("Update check failed: HTTP ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val release = json.decodeFromString<GithubRelease>(body)
            val parsed = parseReleaseTag(release.tagName) ?: return@withContext null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext null
            Timber.i("Update check: versionName=${parsed.first}, versionCode=${parsed.second}")
            return@withContext UpdateInfo(
                updateAvailable = true,
                versionCode = parsed.second,
                versionName = parsed.first,
                downloadUrl = apk.browserDownloadUrl,
                releaseNotes = release.body,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
        }
        return@withContext null
    }

    /** Tags look like v1.2.1.23: version name 1.2.1, version code 23. */
    internal fun parseReleaseTag(tag: String): Pair<String, Int>? {
        val parts = tag.removePrefix("v").split(".")
        if (parts.size < 4 || parts.any { it.toIntOrNull() == null }) return null
        val versionCode = parts.last().toInt()
        val versionName = parts.dropLast(1).joinToString(".")
        return versionName to versionCode
    }
}

