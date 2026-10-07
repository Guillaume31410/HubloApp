package fr.perso.hublot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reçoit les alarmes programmées : rappels, fin du décompte, bascule de l'icône. */
class AlarmeReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        when (intent.action) {
            Planificateur.ACTION_RAPPEL -> {
                val d = prefs.decompte ?: return
                // Ignore un rappel resté d'un décompte précédent.
                if (d.fin != intent.getLongExtra(Planificateur.EXTRA_FIN, -1)) return
                Notifs.rappel(ctx, intent.getLongExtra(Planificateur.EXTRA_MINUTES, 0), d.fin)
            }
            Planificateur.ACTION_FIN -> {
                val d = prefs.decompte ?: return
                if (d.fin != intent.getLongExtra(Planificateur.EXTRA_FIN, -1)) return
                prefs.decompte = null
                Notifs.annulerRappels(ctx)
                if (prefs.notifFin) Notifs.fin(ctx)
            }
            Planificateur.ACTION_ICONE -> IconeDynamique.mettreAJour(ctx)
        }
    }
}

/**
 * Redémarrage du téléphone, mise à jour de l'APK, changement d'heure ou de fuseau,
 * changement d'autorisation des alarmes exactes : on reprogramme tout.
 * Les rappels manqués pendant que le téléphone était éteint sont perdus.
 */
class DemarrageReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        val d = prefs.decompte
        if (d != null && d.fin <= System.currentTimeMillis()) {
            prefs.decompte = null
        }
        Notifs.creerCanaux(ctx)
        Planificateur.programmerDecompte(ctx)
        IconeDynamique.mettreAJour(ctx)
    }
}
