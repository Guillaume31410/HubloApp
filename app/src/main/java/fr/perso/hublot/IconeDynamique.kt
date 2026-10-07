package fr.perso.hublot

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import fr.perso.hublot.calcul.PlagesHC
import java.time.Instant
import java.time.ZoneId

/**
 * Icône jour / nuit : deux activity-alias portent chacune une icône ; on active
 * l'une et désactive l'autre au début et à la fin des heures creuses.
 *
 * La bascule est reportée tant qu'un écran de l'application est visible, car
 * certains appareils ferment l'application quand l'alias qui l'a lancée est désactivé.
 */
object IconeDynamique {

    private const val ALIAS_JOUR = "fr.perso.hublot.LanceurJour"
    private const val ALIAS_NUIT = "fr.perso.hublot.LanceurNuit"

    /** Nombre d'écrans de l'application actuellement visibles. */
    var ecransVisibles = 0
        private set

    fun ecranVisible() {
        ecransVisibles++
    }

    fun ecranMasque(ctx: Context) {
        ecransVisibles = maxOf(0, ecransVisibles - 1)
        if (ecransVisibles == 0) appliquer(ctx)
    }

    /** Programme la prochaine bascule et applique l'icône du moment. */
    fun mettreAJour(ctx: Context) {
        planifier(ctx)
        appliquer(ctx)
    }

    fun planifier(ctx: Context) {
        Planificateur.annulerIcone(ctx)
        val prefs = Prefs(ctx)
        if (!prefs.iconeDynamique) return
        val prochaine = PlagesHC.prochaineBascule(prefs.plages, ZoneId.systemDefault(), Instant.now()) ?: return
        // Une seconde après la bascule, pour être franchement du bon côté.
        Planificateur.programmerIcone(ctx, prochaine.toEpochMilli() + 1000)
    }

    fun appliquer(ctx: Context) {
        if (ecransVisibles > 0) return
        val prefs = Prefs(ctx)
        val nuit = prefs.iconeDynamique &&
            PlagesHC.estEnHC(prefs.plages, ZoneId.systemDefault(), Instant.now())
        val pm = ctx.packageManager
        val jour = ComponentName(ctx, ALIAS_JOUR)
        val n = ComponentName(ctx, ALIAS_NUIT)
        // On active d'abord la nouvelle icône pour ne jamais rester sans lanceur.
        if (nuit) {
            regler(pm, n, actif = true, defaut = false)
            regler(pm, jour, actif = false, defaut = true)
        } else {
            regler(pm, jour, actif = true, defaut = true)
            regler(pm, n, actif = false, defaut = false)
        }
    }

    /** Ne touche au composant que si son état effectif change, pour ménager le lanceur. */
    private fun regler(pm: PackageManager, cn: ComponentName, actif: Boolean, defaut: Boolean) {
        val etat = pm.getComponentEnabledSetting(cn)
        val actuel = when (etat) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> defaut
            else -> false
        }
        if (actuel == actif) return
        pm.setComponentEnabledSetting(
            cn,
            if (actif) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
