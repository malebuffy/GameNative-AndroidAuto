package app.gamenative.auto

import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

/**
 * Android Auto projection entry, same category Xtream Player uses.
 * Gearhead shows this app in the launcher and opens [GameNativeCarActivity].
 */
class CarService : CarActivityService() {
    override fun getCarActivity(): Class<out CarActivity> = GameNativeCarActivity::class.java
}
