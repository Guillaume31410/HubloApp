package fr.perso.hublot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Programme les rappels et la fin du décompte avec AlarmManager.
 *
 * Alarmes exactes : USE_EXACT_ALARM (API 33+, accordée d'office) et
 * SCHEDULE_EXACT_ALARM (API 31–32). Si elles sont refusées, repli sur une alarme
 * inexacte, qui peut arriver avec quelques minutes de retard.
 */
object Planificateur {

    const val ACTION_RAPPEL = "fr.perso.hublot.RAPPEL"
    const val ACTION_FIN = "fr.perso.hublot.FIN"
    const val ACTION_ICONE = "fr.perso.hublot.ICONE"
    const val EXTRA_FIN = "fin"
    const val EXTRA_MINUTES = "minutes"

    /** Codes de requête : un par rappel possible, plus la fin et l'icône. */
    private val CODES_RAPPELS = mapOf(60L to 1, 30L to 2, 10L to 3)
    private const val CODE_FIN = 10
    private const val CODE_ICONE = 20

    fun alarmesExactesPermises(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * (Re)programme tout ce qui concerne le décompte en cours, après avoir annulé
     * l'existant. Les rappels dont l'heure est passée sont ignorés.
     */
    fun programmerDecompte(ctx: Context) {
        annulerDecompte(ctx)
        val prefs = Prefs(ctx)
        val d = prefs.decompte ?: return
        val maintenant = System.currentTimeMillis()

        for (minutes in prefs.rappelsActifs()) {
            val t = d.fin - minutes * 60_000
            if (t <= maintenant) continue
            val intent = Intent(ctx, AlarmeReceiver::class.java)
                .setAction(ACTION_RAPPEL)
                .putExtra(EXTRA_FIN, d.fin)
                .putExtra(EXTRA_MINUTES, minutes)
            programmer(ctx, t, pending(ctx, CODES_RAPPELS.getValue(minutes), intent))
        }

        // La fin est toujours programmée : elle déverrouille l'accueil et, si la
        // case est cochée, envoie la notification de fin.
        val fin = Intent(ctx, AlarmeReceiver::class.java).setAction(ACTION_FIN).putExtra(EXTRA_FIN, d.fin)
        programmer(ctx, maxOf(d.fin, maintenant + 1000), pending(ctx, CODE_FIN, fin))
    }

    /** Annule les rappels et la fin programmés, ainsi qu'un rappel déjà affiché. */
    fun annulerDecompte(ctx: Context) {
        for (code in CODES_RAPPELS.values) annuler(ctx, code, Intent(ctx, AlarmeReceiver::class.java).setAction(ACTION_RAPPEL))
        annuler(ctx, CODE_FIN, Intent(ctx, AlarmeReceiver::class.java).setAction(ACTION_FIN))
        Notifs.annulerRappels(ctx)
    }

    fun programmerIcone(ctx: Context, instant: Long) {
        val intent = Intent(ctx, AlarmeReceiver::class.java).setAction(ACTION_ICONE)
        programmer(ctx, instant, pending(ctx, CODE_ICONE, intent))
    }

    fun annulerIcone(ctx: Context) {
        annuler(ctx, CODE_ICONE, Intent(ctx, AlarmeReceiver::class.java).setAction(ACTION_ICONE))
    }

    // --- interne ---

    private fun pending(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun programmer(ctx: Context, instant: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (alarmesExactesPermises(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
        }
    }

    private fun annuler(ctx: Context, code: Int, intent: Intent) {
        val pi = PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }
}
