package app.gamenative.auto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import com.winlator.inputcontrols.ExternalController

/**
 * A Bluetooth controller paired to the phone is delivered to whichever phone window
 * is in front. Android Auto takes that window, so the controller drives the car
 * interface. This activity sits on the phone while a game is open on the head unit
 * and hands the controller to the game instead.
 */
class CarControllerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        setContent { WirelessControllerCapture() }
    }

    override fun onResume() {
        super.onResume()
        if (!PluviaApp.isCarProjection || PluviaApp.xServerView == null) {
            finish()
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (forwardControllerKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (forwardControllerMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    companion object {
        private var instance: CarControllerActivity? = null

        fun open(context: Context) {
            if (!PluviaApp.isCarProjection || PluviaApp.xServerView == null) return
            if (instance != null) return
            val app = context.applicationContext
            val intent = Intent(app, CarControllerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            runCatching { app.startActivity(intent) }
        }

        fun close() {
            instance?.finish()
            instance = null
        }
    }
}

internal fun forwardControllerKey(event: KeyEvent): Boolean {
    if (!isControllerSource(event.source, event.device)) return false
    PluviaApp.events.emit(AndroidEvent.KeyEvent(event)) { results ->
        results.any { it }
    }
    return true
}

internal fun forwardControllerMotion(event: MotionEvent): Boolean {
    if (!isControllerSource(event.source, event.device)) return false
    PluviaApp.events.emit(AndroidEvent.MotionEvent(event)) { results ->
        results.any { it }
    }
    return true
}

@Composable
fun WirelessControllerCapture() {
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp)
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.car_controller_capture),
            color = Color.White,
        )
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

private fun isControllerSource(source: Int, device: InputDevice?): Boolean {
    if (device != null && ExternalController.isGameController(device)) return true
    return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
        source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
}
