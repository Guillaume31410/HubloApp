package fr.perso.hublot.calcul

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Mises en forme françaises, sans dépendance Android. */
object Formats {

    /** Valeur à saisir sur la machine : « 6 h 00 ». */
    fun delai(d: Duration): String = "%d h %02d".format(d.toHours(), (d.toMinutes() % 60).toInt())

    /** Durée lisible : « 2 h 13 », « 2 h », « 43 min ». */
    fun duree(d: Duration): String {
        val h = d.toHours()
        val m = (d.toMinutes() % 60).toInt()
        return when {
            h == 0L -> "$m min"
            m == 0 -> "$h h"
            else -> "%d h %02d".format(h, m)
        }
    }

    /** Durée d'un cycle : « 2:15 ». */
    fun cycle(d: Duration): String = "%d:%02d".format(d.toHours(), (d.toMinutes() % 60).toInt())

    fun heure(t: Instant, zone: ZoneId): String {
        val z = t.atZone(zone)
        return "%02d:%02d".format(z.hour, z.minute)
    }

    /** « aujourd'hui », « demain », « après-demain » ou la date. */
    fun jourRelatif(t: Instant, reference: Instant, zone: ZoneId): String {
        val jour = t.atZone(zone).toLocalDate()
        val ref: LocalDate = reference.atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(ref, jour)) {
            0L -> "aujourd'hui"
            1L -> "demain"
            2L -> "après-demain"
            else -> "le %02d/%02d".format(jour.dayOfMonth, jour.monthValue)
        }
    }
}
