package fr.perso.hublot

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import fr.perso.hublot.calcul.Formats
import java.time.Instant
import java.time.ZoneId

/** Notifications locales : rappels avant la fin et notification de fin. */
object Notifs {

    private const val CANAL_RAPPELS = "rappels"
    private const val CANAL_FIN = "fin"
    private const val ID_RAPPEL = 100
    private const val ID_FIN = 200

    fun creerCanaux(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_RAPPELS, "Rappels avant la fin", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rappels 1 h, 30 min et 10 min avant la fin du lavage"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_FIN, "Fin du lavage", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Prévient quand le linge est prêt"
            },
        )
    }

    /** Les notifications peuvent-elles s'afficher (permission et réglage système) ? */
    fun autorisees(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun rappel(ctx: Context, minutesAvant: Long, fin: Long) {
        val heure = Formats.heure(Instant.ofEpochMilli(fin), ZoneId.systemDefault())
        val titre = if (minutesAvant >= 60) "Fin du lavage dans ${minutesAvant / 60} h" else "Fin du lavage dans $minutesAvant min"
        publier(ctx, CANAL_RAPPELS, ID_RAPPEL, titre, "Le cycle se termine à $heure.")
    }

    fun fin(ctx: Context) {
        publier(ctx, CANAL_FIN, ID_FIN, "Le linge est prêt", "Le cycle est terminé : pensez à vider la machine.")
    }

    fun annulerRappels(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_RAPPEL)
    }

    private fun publier(ctx: Context, canal: String, id: Int, titre: String, texte: String) {
        if (!autorisees(ctx)) return
        creerCanaux(ctx)
        val ouvrir = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, canal)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ctx.getColor(R.color.accent))
            .setContentTitle(titre)
            .setContentText(texte)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) {
            // Permission retirée entre-temps : rien à faire.
        }
    }
}
