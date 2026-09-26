package app.gamenative.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.gamenative.BuildConfig
import app.gamenative.NetworkMonitor
import app.gamenative.ui.util.LocalSnackbarHostController
import app.gamenative.ui.util.SnackbarHostController
import app.gamenative.utils.AnimatedPngDecoder
import app.gamenative.utils.IconDecoder
import coil.ImageLoader
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.skydoves.landscapist.coil.LocalCoilImageLoader
import okio.Path.Companion.toOkioPath

/**
 * The same composition the phone activity shows. Android Auto hosts this directly
 * so the head unit draws the library, login, and game screens.
 */
@Composable
fun GameNativeContent(
    requestNotificationPermission: Boolean = true,
    onImageLoader: (ImageLoader) -> Unit = {},
) {
    var hasNotificationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (requestNotificationPermission &&
            !BuildConfig.MODERN_XR &&
            !hasNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val context = LocalContext.current
    val imageLoader = remember {
        val memoryCache = MemoryCache.Builder(context)
            .maxSizePercent(0.1)
            .strongReferencesEnabled(true)
            .build()

        val diskCache = DiskCache.Builder()
            .maxSizePercent(0.03)
            .directory(context.cacheDir.resolve("image_cache").toOkioPath())
            .build()

        ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache(memoryCache)
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache(diskCache)
            .components {
                add(Interceptor { chain ->
                    val request = if (!NetworkMonitor.hasInternet.value) {
                        chain.request.newBuilder()
                            .networkCachePolicy(CachePolicy.DISABLED)
                            .build()
                    } else {
                        chain.request
                    }
                    chain.proceed(request)
                })
                add(IconDecoder.Factory())
                add(AnimatedPngDecoder.Factory())
            }
            .build()
            .also(onImageLoader)
    }

    val snackbarController = remember { SnackbarHostController() }
    CompositionLocalProvider(
        LocalCoilImageLoader provides imageLoader,
        LocalSnackbarHostController provides snackbarController,
    ) {
        PluviaMain()
    }
}
