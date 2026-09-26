package app.gamenative.auto

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.db.dao.EpicGameDao
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.db.dao.LibraryPlayHistoryDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.di.IAppTheme
import app.gamenative.ui.model.DownloadsViewModel
import app.gamenative.ui.model.GogRecommendationsViewModel
import app.gamenative.ui.model.HomeViewModel
import app.gamenative.ui.model.LibraryViewModel
import app.gamenative.ui.model.MainViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarGraph {
    fun appTheme(): IAppTheme
    fun libraryPlayHistoryDao(): LibraryPlayHistoryDao
    fun steamAppDao(): SteamAppDao
    fun gogGameDao(): GOGGameDao
    fun epicGameDao(): EpicGameDao
    fun amazonGameDao(): AmazonGameDao
}

/**
 * Builds the same screen models Hilt would build on the phone. The projection
 * surface is not an `@AndroidEntryPoint` activity, so it cannot use `hiltViewModel()`.
 */
class CarViewModelFactory(app: Context) : ViewModelProvider.Factory {
    private val appContext = app.applicationContext
    private val deps: CarGraph by lazy {
        EntryPointAccessors.fromApplication(appContext, CarGraph::class.java)
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(modelClass, CreationExtras.Empty)

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val model = when (modelClass) {
            MainViewModel::class.java -> MainViewModel(
                SavedStateHandle(),
                deps.appTheme(),
                deps.libraryPlayHistoryDao(),
            )
            LibraryViewModel::class.java -> LibraryViewModel(
                deps.libraryPlayHistoryDao(),
                deps.steamAppDao(),
                deps.gogGameDao(),
                deps.epicGameDao(),
                deps.amazonGameDao(),
                appContext,
            )
            DownloadsViewModel::class.java -> DownloadsViewModel(
                appContext,
                deps.steamAppDao(),
                deps.epicGameDao(),
                deps.gogGameDao(),
                deps.amazonGameDao(),
            )
            GogRecommendationsViewModel::class.java -> GogRecommendationsViewModel(
                deps.libraryPlayHistoryDao(),
                deps.gogGameDao(),
                deps.epicGameDao(),
                deps.amazonGameDao(),
                appContext,
            )
            HomeViewModel::class.java -> HomeViewModel()
            else -> modelClass.getDeclaredConstructor().newInstance()
        }
        return modelClass.cast(model)
            ?: throw IllegalArgumentException("Cannot create ${modelClass.name} for Android Auto")
    }
}
