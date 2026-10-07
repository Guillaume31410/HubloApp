package fr.perso.hublot.calcul

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Données d'entrée d'un calcul. */
data class Entree(
    /** Heure du téléphone ; arrondie à la minute supérieure par le calcul. */
    val maintenant: Instant,
    val zone: ZoneId,
    val finSouhaitee: LocalTime,
    val cycle: Duration,
    val delaiMax: Duration = Duration.ofHours(24),
    val avanceMaxHC: Duration = Duration.ofHours(1),
    val plages: List<PlageHC> = emptyList(),
)

/** Un réglage possible de la machine. */
data class Option(
    /** Départ différé à saisir sur la machine (multiple de 30 min, au moins 30 min). */
    val delai: Duration,
    val depart: Instant,
    val fin: Instant,
    val dureeHC: Duration,
    val cycle: Duration,
    /** Un changement d'heure a lieu entre maintenant et la fin du cycle. */
    val changementHeure: Boolean,
) {
    val pourcentHC: Int
        get() = if (cycle.isZero) 0 else Math.round(dureeHC.seconds * 100.0 / cycle.seconds).toInt()
}

sealed interface Resultat {

    data class Ok(
        /** « Maintenant » arrondi à la minute supérieure : point de départ du délai. */
        val maintenant: Instant,
        /** Heure de fin souhaitée retenue (prochaine occurrence atteignable). */
        val cible: Instant,
        /** Calcul « au plus juste » : fin au plus tard à la cible, au plus tôt 30 min avant. */
        val standard: Option,
        /** Meilleure option heures creuses ; égale à [standard] s'il n'y a aucun gain. */
        val heuresCreuses: Option,
        /** Au moins une plage HC est définie. */
        val hcDefinies: Boolean,
    ) : Resultat {
        val gainHC: Duration get() = heuresCreuses.dureeHC.minus(standard.dureeHC)
        val aGainHC: Boolean get() = gainHC > Duration.ZERO
        /** Aucune option possible ne recouvre la moindre minute d'heures creuses. */
        val aucunRecouvrementHC: Boolean get() = hcDefinies && heuresCreuses.dureeHC.isZero
    }

    /** Il faudrait un délai supérieur au maximum de la machine. */
    data class Inatteignable(
        val cible: Instant,
        val delaiNecessaire: Duration,
        val delaiMax: Duration,
    ) : Resultat

    data class EntreeInvalide(val raison: String) : Resultat
}

/**
 * Logique de calcul du départ différé, sans aucune dépendance Android.
 *
 * Règles :
 * - « maintenant » est arrondi à la minute supérieure ;
 * - le délai est un multiple de [PAS], d'au moins [PAS] ;
 * - si l'heure de fin n'est pas atteignable aujourd'hui (délai < [PAS]), on prend
 *   l'occurrence suivante (le lendemain) ;
 * - toutes les durées sont réelles : un changement d'heure dans la nuit est pris
 *   en compte, puisque la machine compte un temps écoulé.
 */
object CalculDepart {

    val PAS: Duration = Duration.ofMinutes(30)

    fun arrondiMinuteSuperieure(t: Instant): Instant {
        val tronque = t.truncatedTo(ChronoUnit.MINUTES)
        return if (tronque == t) t else tronque.plus(1, ChronoUnit.MINUTES)
    }

    fun calculer(e: Entree): Resultat {
        if (e.cycle <= Duration.ZERO) return Resultat.EntreeInvalide("La durée du cycle doit être positive.")
        if (e.cycle >= Duration.ofHours(24)) return Resultat.EntreeInvalide("La durée du cycle doit être inférieure à 24 h.")
        if (e.delaiMax < PAS) return Resultat.EntreeInvalide("Le délai maximum de la machine doit être d'au moins 30 min.")
        if (e.avanceMaxHC < PAS) return Resultat.EntreeInvalide("L'avance maximale doit être d'au moins 30 min.")

        val t0 = arrondiMinuteSuperieure(e.maintenant)
        val jour0 = t0.atZone(e.zone).toLocalDate()

        // Première occurrence de l'heure souhaitée qui laisse au moins PAS de délai.
        var cible: Instant? = null
        var marge: Duration = Duration.ZERO
        for (j in 0L..3L) {
            val c = jour0.plusDays(j).atTime(e.finSouhaitee).atZone(e.zone).toInstant()
            if (!c.isAfter(t0)) continue
            val m = Duration.between(t0, c).minus(e.cycle)
            if (m >= PAS) {
                cible = c
                marge = m
                break
            }
        }
        if (cible == null) return Resultat.EntreeInvalide("Aucune heure de fin atteignable.")

        val delaiStandard = arrondiInferieurAuPas(marge)
        if (delaiStandard > e.delaiMax) {
            return Resultat.Inatteignable(cible, delaiStandard, e.delaiMax)
        }

        val standard = option(e, t0, delaiStandard)

        // Option heures creuses : on parcourt les départs plus précoces, sans finir
        // avant (cible - avance max). À égalité, la première trouvée finit le plus tard.
        var meilleure = standard
        if (e.plages.isNotEmpty()) {
            val finAuPlusTot = cible.minus(e.avanceMaxHC)
            var d = delaiStandard.minus(PAS)
            while (d >= PAS) {
                val o = option(e, t0, d)
                if (o.fin.isBefore(finAuPlusTot)) break
                if (o.dureeHC > meilleure.dureeHC) meilleure = o
                d = d.minus(PAS)
            }
        }

        return Resultat.Ok(
            maintenant = t0,
            cible = cible,
            standard = standard,
            heuresCreuses = meilleure,
            hcDefinies = e.plages.isNotEmpty(),
        )
    }

    /** Vrai si un changement d'heure (été/hiver) a lieu dans ]de, a]. */
    fun changementHeureEntre(zone: ZoneId, de: Instant, a: Instant): Boolean {
        val transition = zone.rules.nextTransition(de) ?: return false
        return !transition.instant.isAfter(a)
    }

    private fun option(e: Entree, t0: Instant, delai: Duration): Option {
        val depart = t0.plus(delai)
        val fin = depart.plus(e.cycle)
        return Option(
            delai = delai,
            depart = depart,
            fin = fin,
            dureeHC = PlagesHC.dureeEnHC(e.plages, e.zone, depart, fin),
            cycle = e.cycle,
            changementHeure = changementHeureEntre(e.zone, t0, fin),
        )
    }

    private fun arrondiInferieurAuPas(d: Duration): Duration {
        val pas = PAS.toMinutes()
        return Duration.ofMinutes((d.toMinutes() / pas) * pas)
    }
}
