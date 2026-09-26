package app.gamenative.service

import android.content.Context
import android.content.Intent
import app.gamenative.PluviaApp
import java.util.concurrent.CancellationException
import timber.log.Timber

/**
 * Starts one of this app's own foreground services.
 *
 * Android Auto draws [app.gamenative.auto.GameNativeCarActivity], which is not an
 * activity. From that surface [Context.startForegroundService] throws
 * [android.app.ForegroundServiceStartNotAllowedException] and the process dies.
 * A plain [Context.startService] is still allowed while the head unit is bound to
 * the projection service, and the service promotes itself when the system permits it.
 */
fun Context.startPlatformService(intent: Intent): Boolean {
    // startForegroundService() starts a timer. If the service cannot call startForeground()
    // the system kills the process several seconds later. The head unit is not an activity,
    // so that timer is what closes the app after it has already appeared.
    if (PluviaApp.isCarProjection) {
        return startQuietly(intent)
    }
    try {
        startForegroundService(intent)
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (error: IllegalStateException) {
        Timber.w(error, "Foreground start blocked for %s", intent.component)
    }
    return startQuietly(intent)
}

private fun Context.startQuietly(intent: Intent): Boolean {
    return try {
        startService(intent)
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: IllegalStateException) {
        Timber.w(error, "Could not start %s", intent.component)
        false
    }
}
