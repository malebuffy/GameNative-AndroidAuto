package app.gamenative.auto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import java.lang.ref.WeakReference
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.gamenative.MainActivity
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.powercontrol.PowerManager
import app.gamenative.service.SteamService
import app.gamenative.ui.GameNativeContent
import app.gamenative.utils.ContainerUtils
import com.google.android.apps.auto.sdk.CarActivity
import com.winlator.inputcontrols.ControllerManager
import timber.log.Timber

/**
 * Android Auto only draws this car activity. The phone Compose tree is installed
 * here in [onCreate]; nothing is launched onto another display.
 */
class GameNativeCarActivity :
    CarActivity(),
    LifecycleOwner,
    ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner,
    ActivityResultRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val vmStore = ViewModelStore()
    private val backDispatcher = OnBackPressedDispatcher { super.onBackPressed() }
    private val results = CarActivityResults(this)
    private val viewModelFactory by lazy { CarViewModelFactory(applicationContext) }
    private var contentView: View? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = vmStore

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = viewModelFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras()

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = backDispatcher

    override val activityResultRegistry: ActivityResultRegistry
        get() = results

    override fun onCreate(savedInstanceState: Bundle?) {
        PluviaApp.isCarProjection = true
        CarProjectionGuard.install(this)
        super.onCreate(savedInstanceState)
        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        val chrome = carUiController
        chrome.statusBarController.hideAppHeader()
        chrome.menuController.hideMenuButton()
        c().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching { ControllerManager.getInstance().init(applicationContext) }
            .onFailure { Timber.w(it, "Controller init failed on Android Auto") }
        runCatching { ContainerUtils.setContainerDefaults(applicationContext) }
            .onFailure { Timber.w(it, "Container defaults failed on Android Auto") }

        savedStateController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Compose installs its window recomposer on the decor view, not on the child
        // we add. A phone activity does this in ComponentActivity.onCreate. The car
        // window does not, so the head unit crashes with ViewTreeLifecycleOwner not found.
        val decorInstalled = runCatching { c().decorView.installProjectedOwners(this) }
        if (decorInstalled.isFailure) {
            val error = decorInstalled.exceptionOrNull() ?: IllegalStateException("Car window unavailable")
            Timber.e(error, "Android Auto window has no lifecycle owner")
            setContentView(carErrorView(applicationContext, error))
            return
        }

        // applicationContext is the phone display. The car activity's resources are the
        // head unit's size and density; Compose has to use those or the layout is offset.
        val themed = ProjectedContext(
            projectionUiContext(),
            c().windowManager,
            c().decorView,
        )
        val root = CarContentLayout(themed)
        val composeView = ComposeView(themed).apply {
            installProjectedOwners(this@GameNativeCarActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(0xFF121212.toInt())
            setContent { CarRoot() }
            setOnGenericMotionListener { _, event -> forwardControllerMotion(event) }
        }
        contentView = composeView
        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        try {
            setContentView(root)
            composeView.requestFocus()
        } catch (error: Throwable) {
            Timber.e(error, "Android Auto could not show GameNative")
            contentView = null
            setContentView(carErrorView(applicationContext, error))
        }
        dispatchCarLifecycle(Lifecycle.Event.ON_START, Lifecycle.State.CREATED)
    }

    /**
     * Head-unit size and density, not the phone's. [createConfigurationContext] keeps
     * normal system services while adopting the car display metrics.
     */
    private fun projectionUiContext(): Context {
        val metrics = resources.displayMetrics
        val config = Configuration(resources.configuration)
        if (metrics.density > 0f && metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            config.densityDpi = metrics.densityDpi
            config.screenWidthDp = (metrics.widthPixels / metrics.density).toInt().coerceAtLeast(1)
            config.screenHeightDp = (metrics.heightPixels / metrics.density).toInt().coerceAtLeast(1)
            config.smallestScreenWidthDp = minOf(config.screenWidthDp, config.screenHeightDp)
            config.orientation = if (config.screenWidthDp >= config.screenHeightDp) {
                Configuration.ORIENTATION_LANDSCAPE
            } else {
                Configuration.ORIENTATION_PORTRAIT
            }
        }
        return createConfigurationContext(config)
    }

    private fun dispatchCarLifecycle(event: Lifecycle.Event, from: Lifecycle.State) {
        if (lifecycleRegistry.currentState != from) return
        runCatching { lifecycleRegistry.handleLifecycleEvent(event) }
            .onFailure { Timber.w(it, "Android Auto lifecycle %s", event) }
    }

    override fun onStart() {
        super.onStart()
        dispatchCarLifecycle(Lifecycle.Event.ON_START, Lifecycle.State.CREATED)
    }

    override fun onResume() {
        super.onResume()
        dispatchCarLifecycle(Lifecycle.Event.ON_RESUME, Lifecycle.State.STARTED)
        PluviaApp.isActivityInForeground = true
        runCatching { PowerManager.resume() }
            .onFailure { Timber.w(it, "Power resume failed on Android Auto") }
        SteamService.autoStopWhenIdle = false
        if (PluviaApp.xEnvironment != null && !PluviaApp.isNeverSuspendMode() && !PluviaApp.isOverlayPaused) {
            PluviaApp.xEnvironment?.onResume()
        }
    }

    override fun onPause() {
        dispatchCarLifecycle(Lifecycle.Event.ON_PAUSE, Lifecycle.State.RESUMED)
        if (!MainActivity.isPhoneResumed) {
            PluviaApp.isActivityInForeground = false
            runCatching { PowerManager.pause() }
            SteamService.autoStopWhenIdle = true
            if (PluviaApp.xEnvironment != null && !PluviaApp.isNeverSuspendMode()) {
                PluviaApp.xEnvironment?.onPause()
            }
        }
        super.onPause()
    }

    override fun onStop() {
        dispatchCarLifecycle(Lifecycle.Event.ON_PAUSE, Lifecycle.State.RESUMED)
        dispatchCarLifecycle(Lifecycle.Event.ON_STOP, Lifecycle.State.STARTED)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savedStateController.performSave(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        dispatchCarLifecycle(Lifecycle.Event.ON_PAUSE, Lifecycle.State.RESUMED)
        dispatchCarLifecycle(Lifecycle.Event.ON_STOP, Lifecycle.State.STARTED)
        dispatchCarLifecycle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.CREATED)
        vmStore.clear()
        contentView = null
        CarControllerActivity.close()
        CarProjectionGuard.clear(this)
        PluviaApp.isCarProjection = false
        super.onDestroy()
    }

    override fun onBackPressed() {
        backDispatcher.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (forwardControllerKey(event)) return true
        return contentView?.dispatchKeyEvent(event) == true || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (forwardControllerKey(event)) return true
        return contentView?.dispatchKeyEvent(event) == true || super.onKeyUp(keyCode, event)
    }

    @Composable
    private fun CarRoot() {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides this,
            LocalOnBackPressedDispatcherOwner provides this,
        ) {
            val metrics = resources.displayMetrics
            val fontScale = if (metrics.density > 0f) metrics.scaledDensity / metrics.density else 1f
            CompositionLocalProvider(
                LocalDensity provides Density(metrics.density, fontScale),
            ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                    .background(Color(0xFF121212)),
            ) {
                GameNativeContent(requestNotificationPermission = false)
            }
            }
        }
    }
}

/**
 * Keeps the head unit open when a later error would otherwise kill the process.
 * The phone crash handler still runs when Android Auto is not showing.
 */
private object CarProjectionGuard : Thread.UncaughtExceptionHandler {
    private var next: Thread.UncaughtExceptionHandler? = null
    private var host: WeakReference<GameNativeCarActivity>? = null

    fun install(activity: GameNativeCarActivity) {
        host = WeakReference(activity)
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current === this) return
        next = current
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun clear(activity: GameNativeCarActivity) {
        if (host?.get() === activity) host = null
        if (Thread.getDefaultUncaughtExceptionHandler() === this) {
            Thread.setDefaultUncaughtExceptionHandler(next)
        }
    }

    override fun uncaughtException(thread: Thread, error: Throwable) {
        val activity = host?.get()
        if (activity == null || !PluviaApp.isCarProjection) {
            next?.uncaughtException(thread, error)
            return
        }
        Timber.e(error, "Android Auto stayed open after %s", error.javaClass.simpleName)
        if (thread == Looper.getMainLooper().thread) {
            runCatching {
                activity.setContentView(carErrorView(activity.applicationContext, error))
            }
        }
    }
}

internal fun View.installProjectedOwners(owner: GameNativeCarActivity) {
    setViewTreeLifecycleOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    setViewTreeOnBackPressedDispatcherOwner(owner)
}

internal class ProjectedContext(
    base: Context,
    hostWindow: WindowManager,
    tokenView: View,
) : ContextThemeWrapper(base, R.style.Theme_Pluvia) {
    private val windows = attachingWindowManager(hostWindow, tokenView)

    override fun getSystemService(name: String): Any? {
        if (name == Context.WINDOW_SERVICE) return windows
        return super.getSystemService(name)
    }

    override fun startActivity(intent: Intent) {
        openExternal(intent, null)
    }

    override fun startActivity(intent: Intent, options: android.os.Bundle?) {
        openExternal(intent, options)
    }

    private fun openExternal(intent: Intent, options: android.os.Bundle?) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            super.startActivity(intent, options)
        } catch (error: android.content.ActivityNotFoundException) {
            Timber.w(error, "Android Auto could not open %s", intent.component ?: intent.data)
        } catch (error: RuntimeException) {
            Timber.w(error, "Android Auto could not open %s", intent.component ?: intent.data)
        }
    }
}

/**
 * Dialogs ask the window manager for a child window via [createLocalWindowManager], which
 * only the real framework class implements. A Kotlin delegate keeps the throwing default
 * and Play dies while building the loading dialog. This proxy forwards that call, then
 * attaches the resulting window to the car display.
 */
private fun attachingWindowManager(host: WindowManager, tokenView: View): WindowManager {
    return Proxy.newProxyInstance(
        ProjectedContext::class.java.classLoader,
        arrayOf(WindowManager::class.java),
        AttachingWindowHandler(host, tokenView),
    ) as WindowManager
}

private class AttachingWindowHandler(
    private val host: WindowManager,
    private val tokenView: View,
) : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val parameters = args ?: emptyArray()
        if (method.name == "addView" || method.name == "updateViewLayout") {
            val params = parameters.getOrNull(1) as? WindowManager.LayoutParams
            if (params != null && params.token == null) {
                val token = tokenView.windowToken ?: tokenView.applicationWindowToken
                if (token != null) {
                    params.token = token
                    if (params.type < WindowManager.LayoutParams.FIRST_SUB_WINDOW) {
                        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
                    }
                }
            }
        }
        val result = try {
            method.invoke(host, *parameters)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
        return if (method.name == "createLocalWindowManager" && result is WindowManager) {
            attachingWindowManager(result, tokenView)
        } else {
            result
        }
    }
}

internal fun carErrorView(context: Context, error: Throwable): View {
    val text = TextView(context).apply {
        setTextColor(android.graphics.Color.WHITE)
        setPadding(48, 48, 48, 48)
        textSize = 16f
        this.text = buildString {
            append("GameNative could not open on Android Auto.\n\n")
            append(error.javaClass.simpleName)
            append(": ")
            append(error.message.orEmpty())
            append("\n\n")
            append(error.stackTraceToString().take(3500))
        }
    }
    return ScrollView(context).apply {
        setBackgroundColor(0xFF121212.toInt())
        isFillViewport = true
        addView(text)
    }
}

private class CarContentLayout(context: android.content.Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = resources.displayMetrics
        val width = resolve(widthMeasureSpec, metrics.widthPixels.coerceAtLeast(1))
        val height = resolve(heightMeasureSpec, metrics.heightPixels.coerceAtLeast(1))
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }

    private fun resolve(spec: Int, fallback: Int): Int {
        val size = MeasureSpec.getSize(spec)
        return when (MeasureSpec.getMode(spec)) {
            MeasureSpec.EXACTLY -> size.coerceAtLeast(1)
            MeasureSpec.AT_MOST -> if (size > 1) size else fallback
            else -> fallback
        }
    }
}

internal class CarActivityResults(
    private val host: android.content.Context,
) : ActivityResultRegistry() {
    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        val intent = try {
            contract.createIntent(host, input).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (error: RuntimeException) {
            Timber.w(error, "Android Auto could not build a request")
            dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
            return
        }
        try {
            host.startActivity(intent)
        } catch (error: RuntimeException) {
            Timber.w(error, "Android Auto could not open %s", intent.component ?: intent.action)
            dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
        }
    }
}
