package fr.perso.hublot

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

/**
 * Affichage bord à bord. Il est imposé à partir d'Android 15 (targetSdk 35+) ; on
 * l'active aussi sur les versions antérieures pour un comportement identique, et on
 * décale le contenu de la taille des barres système à la main (sans AndroidX).
 */
object BordABord {

    fun activer(activity: Activity, racine: View) {
        val w = activity.window
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            w.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            activerAncien(activity)
        }
        if (Build.VERSION.SDK_INT < 35) barresTransparentes(activity)

        val g = racine.paddingLeft
        val h = racine.paddingTop
        val d = racine.paddingRight
        val b = racine.paddingBottom
        racine.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(g + i.left, h + i.top, d + i.right, b + i.bottom)
            } else {
                v.setPadding(
                    g + anciensInsets(insets, 0),
                    h + anciensInsets(insets, 1),
                    d + anciensInsets(insets, 2),
                    b + anciensInsets(insets, 3),
                )
            }
            insets
        }
        racine.requestApplyInsets()
    }

    @Suppress("DEPRECATION")
    private fun activerAncien(activity: Activity) {
        val decor = activity.window.decorView
        decor.systemUiVisibility = decor.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    @Suppress("DEPRECATION")
    private fun barresTransparentes(activity: Activity) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
    }

    @Suppress("DEPRECATION")
    private fun anciensInsets(insets: WindowInsets, cote: Int): Int = when (cote) {
        0 -> insets.systemWindowInsetLeft
        1 -> insets.systemWindowInsetTop
        2 -> insets.systemWindowInsetRight
        else -> insets.systemWindowInsetBottom
    }
}
