package fr.perso.hublot.calcul

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Plage d'heures creuses exprimée en heure locale (heure murale).
 *
 * - debut < fin : plage dans la journée (ex. 12:00–14:00).
 * - debut > fin : plage qui passe minuit (ex. 22:00–06:00).
 * - debut == fin : journée entière (24 h). L'interface refuse cette saisie,
 *   mais la fusion de plages peut produire ce cas.
 */
data class PlageHC(val debut: LocalTime, val fin: LocalTime) {

    init {
        require(debut.second == 0 && debut.nano == 0 && fin.second == 0 && fin.nano == 0) {
            "Les plages se règlent à la minute"
        }
    }

    val passeMinuit: Boolean get() = !fin.isAfter(debut)

    internal val debutMin: Int get() = debut.hour * 60 + debut.minute
    internal val finMin: Int get() = fin.hour * 60 + fin.minute

    /** Format de persistance et d'affichage : "22:00-06:00". */
    fun encoder(): String = "${hhmm(debut)}-${hhmm(fin)}"

    override fun toString(): String = "${hhmm(debut)}–${hhmm(fin)}"

    companion object {
        private fun hhmm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

        /** Décode "22:00-06:00" ; renvoie null si le texte est invalide. */
        fun decoder(texte: String): PlageHC? {
            val morceaux = texte.trim().split("-")
            if (morceaux.size != 2) return null
            val d = lireHeure(morceaux[0]) ?: return null
            val f = lireHeure(morceaux[1]) ?: return null
            return PlageHC(d, f)
        }

        private fun lireHeure(t: String): LocalTime? {
            val hm = t.trim().split(":")
            if (hm.size != 2) return null
            val h = hm[0].toIntOrNull() ?: return null
            val m = hm[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return LocalTime.of(h, m)
        }

        fun encoderListe(plages: List<PlageHC>): String = plages.joinToString(";") { it.encoder() }

        fun decoderListe(texte: String?): List<PlageHC> =
            texte.orEmpty().split(";").filter { it.isNotBlank() }.mapNotNull { decoder(it) }
    }
}

/** Intervalle de temps réel [debut, fin). */
data class Intervalle(val debut: Instant, val fin: Instant) {
    val duree: Duration get() = Duration.between(debut, fin)
}

/**
 * Opérations sur les plages d'heures creuses. Tous les calculs se font sur des
 * instants réels : les plages, définies en heure murale, sont projetées jour par
 * jour dans le fuseau, ce qui gère correctement les nuits de 23 h ou 25 h.
 */
object PlagesHC {

    private const val MINUTES_JOUR = 24 * 60

    /**
     * Intervalles réels d'heures creuses compris dans [de, a], fusionnés (une même
     * minute n'est jamais comptée deux fois, même si des plages se chevauchent).
     */
    fun projeter(plages: List<PlageHC>, zone: ZoneId, de: Instant, a: Instant): List<Intervalle> {
        if (plages.isEmpty() || !a.isAfter(de)) return emptyList()
        return union(projectionsBrutes(plages, zone, de, a))
            .mapNotNull { iv ->
                val d = maxOf(iv.debut, de)
                val f = minOf(iv.fin, a)
                if (f.isAfter(d)) Intervalle(d, f) else null
            }
    }

    /** Durée passée en heures creuses entre [de, a]. */
    fun dureeEnHC(plages: List<PlageHC>, zone: ZoneId, de: Instant, a: Instant): Duration =
        projeter(plages, zone, de, a).fold(Duration.ZERO) { acc, iv -> acc.plus(iv.duree) }

    /** L'instant donné est-il en heures creuses ? */
    fun estEnHC(plages: List<PlageHC>, zone: ZoneId, instant: Instant): Boolean =
        union(projectionsBrutes(plages, zone, instant, instant.plusSeconds(1)))
            .any { !instant.isBefore(it.debut) && instant.isBefore(it.fin) }

    /**
     * Prochain instant strictement après [apres] où l'on entre ou sort des heures
     * creuses. Null s'il n'y a aucune plage ou si les heures creuses couvrent tout.
     */
    fun prochaineBascule(plages: List<PlageHC>, zone: ZoneId, apres: Instant): Instant? {
        if (plages.isEmpty()) return null
        val horizon = apres.plus(Duration.ofDays(3))
        return union(projectionsBrutes(plages, zone, apres, horizon))
            .flatMap { listOf(it.debut, it.fin) }
            .filter { it.isAfter(apres) && it.isBefore(horizon) }
            .minOrNull()
    }

    /** Vrai si au moins deux plages se recouvrent (un simple contact ne compte pas). */
    fun seChevauchent(plages: List<PlageHC>): Boolean {
        val segmentsParPlage = plages.map { segments(it) }
        for (i in segmentsParPlage.indices) {
            for (j in i + 1 until segmentsParPlage.size) {
                for (a in segmentsParPlage[i]) for (b in segmentsParPlage[j]) {
                    if (minOf(a.last, b.last) > maxOf(a.first, b.first)) return true
                }
            }
        }
        return false
    }

    /**
     * Fusionne les plages qui se recouvrent ou se touchent. Le résultat est trié
     * par heure de début ; une plage peut passer minuit.
     */
    fun fusionner(plages: List<PlageHC>): List<PlageHC> {
        if (plages.isEmpty()) return emptyList()
        val tries = plages.flatMap { segments(it) }.sortedBy { it.first }
        val fusionnes = mutableListOf<IntArray>()
        for (s in tries) {
            val dernier = fusionnes.lastOrNull()
            if (dernier != null && s.first <= dernier[1]) {
                dernier[1] = maxOf(dernier[1], s.last)
            } else {
                fusionnes += intArrayOf(s.first, s.last)
            }
        }
        if (fusionnes.size == 1 && fusionnes[0][0] == 0 && fusionnes[0][1] == MINUTES_JOUR) {
            return listOf(PlageHC(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT))
        }
        // Raccorde un segment finissant à minuit avec celui qui commence à minuit.
        if (fusionnes.size > 1 && fusionnes.first()[0] == 0 && fusionnes.last()[1] == MINUTES_JOUR) {
            val premier = fusionnes.removeAt(0)
            fusionnes.last()[1] = MINUTES_JOUR + premier[1]
        }
        return fusionnes
            .map { PlageHC(heure(it[0]), heure(it[1] % MINUTES_JOUR)) }
            .sortedBy { it.debut }
    }

    // --- interne ---

    /** Segments [debut, fin] en minutes dans [0, 1440] ; une plage passant minuit en donne deux. */
    private fun segments(p: PlageHC): List<IntRange> = when {
        p.debutMin == p.finMin -> listOf(0..MINUTES_JOUR)
        p.debutMin < p.finMin -> listOf(p.debutMin..p.finMin)
        else -> listOfNotNull(
            p.debutMin..MINUTES_JOUR,
            if (p.finMin > 0) 0..p.finMin else null,
        )
    }

    private fun heure(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)

    /** Projette chaque plage sur chaque jour qui peut toucher [de, a]. */
    private fun projectionsBrutes(plages: List<PlageHC>, zone: ZoneId, de: Instant, a: Instant): List<Intervalle> {
        val premierJour: LocalDate = de.atZone(zone).toLocalDate().minusDays(1)
        val dernierJour: LocalDate = a.atZone(zone).toLocalDate().plusDays(1)
        val res = mutableListOf<Intervalle>()
        var jour = premierJour
        while (!jour.isAfter(dernierJour)) {
            for (p in plages) {
                val jourFin = if (p.finMin > p.debutMin) jour else jour.plusDays(1)
                val d = jour.atTime(p.debut).atZone(zone).toInstant()
                val f = jourFin.atTime(p.fin).atZone(zone).toInstant()
                if (f.isAfter(d)) res += Intervalle(d, f)
            }
            jour = jour.plusDays(1)
        }
        return res
    }

    private fun union(intervalles: List<Intervalle>): List<Intervalle> {
        val tries = intervalles.sortedBy { it.debut }
        val res = mutableListOf<Intervalle>()
        for (iv in tries) {
            val dernier = res.lastOrNull()
            if (dernier != null && !iv.debut.isAfter(dernier.fin)) {
                if (iv.fin.isAfter(dernier.fin)) res[res.size - 1] = Intervalle(dernier.debut, iv.fin)
            } else {
                res += iv
            }
        }
        return res
    }
}
