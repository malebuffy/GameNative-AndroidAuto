package app.gamenative.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gamenative.auto.CarViewModelFactory
import dagger.hilt.internal.GeneratedComponentManager

@Composable
inline fun <reified VM : ViewModel> gamenativeViewModel(
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
): VM {
    val context = LocalContext.current
    val hiltActivity = findHiltActivity(context)
    return if (hiltActivity != null) {
        hiltViewModel(viewModelStoreOwner)
    } else {
        val app = context.applicationContext
        viewModel(
            viewModelStoreOwner = viewModelStoreOwner,
            factory = remember(app) { CarViewModelFactory(app) },
        )
    }
}

fun findHiltActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity && current is GeneratedComponentManager<*>) return current
        current = current.baseContext
    }
    return null
}
