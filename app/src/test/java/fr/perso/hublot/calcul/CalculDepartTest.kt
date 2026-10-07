package fr.perso.hublot.calcul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CalculDepartTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun t(texte: String): Instant = LocalDateTime.parse(texte).atZone(paris).toInstant()
    private fun h(texte: String): LocalTime = LocalTime.parse(texte)
    private fun dur(hh: Long, mm: Long): Duration = Duration.ofHours(hh).plusMinutes(mm)
    private fun hc(texte: String): PlageHC = PlageHC.decoder(texte)!!

    private fun ok(e: Entree): Resultat.Ok {
        val r = CalculDepart.calculer(e)
        assertTrue("Résultat inattendu : $r", r is Resultat.Ok)
        return r as Resultat.Ok
    }

    private fun entree(
        maintenant: String,
        fin: String,
        cycle: Duration,
        plages: List<PlageHC> = emptyList(),
        delaiMax: Duration = Duration.ofHours(24),
        avance: Duration = Duration.ofHours(1),
    ) = Entree(t(maintenant), paris, h(fin), cycle, delaiMax, avance, plages)

    // --- Calcul standard ---

    @Test
    fun passageDeMinuit() {
        val r = ok(entree("2026-10-07T22:48", "07:00", dur(2, 15)))
        assertEquals(dur(5, 30), r.standard.delai)
        assertEquals(t("2026-10-08T04:18"), r.standard.depart)
        assertEquals(t("2026-10-08T06:33"), r.standard.fin)
        assertEquals(t("2026-10-08T07:00"), r.cible)
    }

    @Test
    fun finStandardDansLaFenetreDe30MinutesAvantLaCible() {
        for (minute in 0 until 60) {
            val maintenant = "2026-10-07T20:%02d".format(minute)
            val r = ok(entree(maintenant, "07:00", dur(1, 47)))
            assertFalse(r.standard.fin.isAfter(r.cible))
            assertTrue(r.standard.fin.isAfter(r.cible.minus(CalculDepart.PAS)))
            assertEquals(0L, r.standard.delai.toMinutes() % 30)
        }
    }

    @Test
    fun maintenantArrondiALaMinuteSuperieure() {
        val e = Entree(t("2026-10-07T22:47").plusSeconds(10), paris, h("07:00"), dur(2, 15))
        val r = ok(e)
        assertEquals(t("2026-10-07T22:48"), r.maintenant)
        assertEquals(t("2026-10-08T04:18"), r.standard.depart)
    }

    @Test
    fun minuteExacteNonArrondie() {
        assertEquals(t("2026-10-07T22:48"), CalculDepart.arrondiMinuteSuperieure(t("2026-10-07T22:48")))
    }

    @Test
    fun delaiNegatifPrendLaProchaineOccurrence() {
        // 22:00, fin souhaitée 23:00 avec un cycle de 2 h : impossible aujourd'hui.
        val r = ok(entree("2026-10-07T22:00", "23:00", dur(2, 0)))
        assertEquals(t("2026-10-08T23:00"), r.cible)
        assertEquals(dur(23, 0), r.standard.delai)
        assertEquals(t("2026-10-08T23:00"), r.standard.fin)
    }

    @Test
    fun heureSouhaiteeDejaPasseePrendLeLendemain() {
        val r = ok(entree("2026-10-07T10:00", "07:00", dur(1, 0)))
        assertEquals(t("2026-10-08T07:00"), r.cible)
        assertEquals(dur(20, 0), r.standard.delai)
    }

    @Test
    fun delaiMinimum30MinSinonLendemain() {
        // Délai brut de 15 min aujourd'hui (< 30 min) : on passe au lendemain.
        val r = ok(entree("2026-10-07T22:00", "22:45", dur(0, 30)))
        assertEquals(t("2026-10-08T22:45"), r.cible)
        assertEquals(dur(24, 0), r.standard.delai)
    }

    @Test
    fun delaiExactement30MinAccepte() {
        val r = ok(entree("2026-10-07T22:00", "23:00", dur(0, 30)))
        assertEquals(t("2026-10-07T23:00"), r.cible)
        assertEquals(dur(0, 30), r.standard.delai)
    }

    @Test
    fun delaiSuperieurAuMaxDeLaMachine() {
        val r = CalculDepart.calculer(entree("2026-10-07T13:00", "07:00", dur(2, 0), delaiMax = Duration.ofHours(8)))
        assertTrue(r is Resultat.Inatteignable)
        r as Resultat.Inatteignable
        assertEquals(dur(16, 0), r.delaiNecessaire)
        assertEquals(Duration.ofHours(8), r.delaiMax)
    }

    @Test
    fun delaiEgalAuMaxAccepte() {
        val r = ok(entree("2026-10-07T13:00", "07:00", dur(2, 0), delaiMax = Duration.ofHours(16)))
        assertEquals(dur(16, 0), r.standard.delai)
    }

    @Test
    fun lendemainAuDelaDuMaxEstInatteignable() {
        // Aujourd'hui : délai < 30 min ; demain : 23 h 30 > max 12 h.
        val r = CalculDepart.calculer(entree("2026-10-07T22:00", "23:00", dur(0, 45), delaiMax = Duration.ofHours(12)))
        assertTrue(r is Resultat.Inatteignable)
    }

    @Test
    fun entreesInvalides() {
        assertTrue(CalculDepart.calculer(entree("2026-10-07T22:00", "07:00", Duration.ZERO)) is Resultat.EntreeInvalide)
        assertTrue(CalculDepart.calculer(entree("2026-10-07T22:00", "07:00", Duration.ofHours(24))) is Resultat.EntreeInvalide)
        assertTrue(
            CalculDepart.calculer(entree("2026-10-07T22:00", "07:00", dur(2, 0), avance = Duration.ofMinutes(15)))
                is Resultat.EntreeInvalide,
        )
    }

    // --- Changement d'heure ---

    @Test
    fun passageHeureHiverDureeReelle() {
        // Nuit du 24 au 25/10/2026 : 03:00 CEST -> 02:00 CET, la nuit dure 25 h.
        val r = ok(entree("2026-10-24T22:48", "07:00", dur(2, 15)))
        // 9 h 12 réelles jusqu'à 07:00, moins 2 h 15 = 6 h 57 -> 6 h 30.
        assertEquals(dur(6, 30), r.standard.delai)
        assertEquals(t("2026-10-25T06:33"), r.standard.fin)
        assertTrue(r.standard.changementHeure)
    }

    @Test
    fun passageHeureEteDureeReelle() {
        // Nuit du 27 au 28/03/2027 : 02:00 CET -> 03:00 CEST, la nuit dure 23 h.
        val r = ok(entree("2027-03-27T22:48", "07:00", dur(2, 15)))
        // 7 h 12 réelles, moins 2 h 15 = 4 h 57 -> 4 h 30 (un calcul naïf donnerait
        // 5 h 30 et finirait à 07:33, trop tard).
        assertEquals(dur(4, 30), r.standard.delai)
        assertEquals(t("2027-03-28T06:33"), r.standard.fin)
        assertFalse(r.standard.fin.isAfter(r.cible))
        assertTrue(r.standard.changementHeure)
    }

    @Test
    fun pasDeChangementHeureUneNuitOrdinaire() {
        val r = ok(entree("2026-10-07T22:48", "07:00", dur(2, 15)))
        assertFalse(r.standard.changementHeure)
    }

    @Test
    fun plagesHCProjeteesEnHeureMuraleLaNuitDuChangement() {
        // HC 22:00–06:00 la nuit du passage à l'heure d'hiver : 9 h réelles.
        val total = PlagesHC.dureeEnHC(
            listOf(hc("22:00-06:00")), paris, t("2026-10-24T21:00"), t("2026-10-25T07:00"),
        )
        assertEquals(Duration.ofHours(9), total)
    }

    // --- Option heures creuses ---

    @Test
    fun exempleDeLaMaquette() {
        // 21:47, fin 07:00, cycle 2:30, HC 22:00–06:00, avance max 1 h.
        val r = ok(entree("2026-10-07T21:47", "07:00", dur(2, 30), listOf(hc("22:00-06:00"))))
        assertEquals(dur(6, 30), r.standard.delai)
        assertEquals(dur(1, 43), r.standard.dureeHC)
        assertEquals(69, r.standard.pourcentHC)
        assertEquals(dur(6, 0), r.heuresCreuses.delai)
        assertEquals(t("2026-10-08T06:17"), r.heuresCreuses.fin)
        assertEquals(dur(2, 13), r.heuresCreuses.dureeHC)
        assertEquals(89, r.heuresCreuses.pourcentHC)
        assertEquals(Duration.ofMinutes(30), r.gainHC)
    }

    @Test
    fun avanceMaxJamaisDepassee() {
        for (avance in listOf(30L, 60L, 90L, 180L)) {
            val r = ok(
                entree(
                    "2026-10-07T21:47", "07:00", dur(2, 30), listOf(hc("22:00-04:00")),
                    avance = Duration.ofMinutes(avance),
                ),
            )
            assertFalse(r.heuresCreuses.fin.isBefore(r.cible.minus(Duration.ofMinutes(avance))))
            assertFalse(r.heuresCreuses.fin.isAfter(r.cible))
        }
    }

    @Test
    fun plageHCPlusCourteQueLeCycle() {
        // HC 04:00–05:00 (1 h) pour un cycle de 2 h : couverture partielle au mieux.
        val r = ok(
            entree("2026-10-07T22:00", "07:00", dur(2, 0), listOf(hc("04:00-05:00")), avance = Duration.ofHours(3)),
        )
        assertEquals(Duration.ofHours(1), r.heuresCreuses.dureeHC)
        assertEquals(50, r.heuresCreuses.pourcentHC)
        // Standard : 05:00–07:00, aucune minute en HC. Parmi les options couvrant
        // toute l'heure creuse, celle qui finit le plus tard : 04:00–06:00.
        assertEquals(Duration.ZERO, r.standard.dureeHC)
        assertEquals(t("2026-10-08T06:00"), r.heuresCreuses.fin)
        assertTrue(r.aGainHC)
    }

    @Test
    fun plusieursPlagesHC() {
        // Deux plages séparées par 1 h d'heures pleines.
        val plages = listOf(hc("01:00-03:00"), hc("04:00-05:30"))
        val r = ok(entree("2026-10-07T22:00", "06:00", dur(3, 0), plages, avance = Duration.ofHours(3)))
        // Standard : 03:00–06:00 -> 1 h 30 (04:00–05:30).
        assertEquals(dur(1, 30), r.standard.dureeHC)
        // Meilleur : 01:30–04:30 -> 1 h 30 + 30 min = 2 h ; 02:00–05:00 -> 1 h + 1 h = 2 h ;
        // 02:30–05:30 -> 30 + 1 h 30 = 2 h. À égalité, on garde celle qui finit le plus tard.
        assertEquals(dur(2, 0), r.heuresCreuses.dureeHC)
        assertEquals(t("2026-10-08T05:30"), r.heuresCreuses.fin)
    }

    @Test
    fun aucunRecouvrementHCPossible() {
        val r = ok(entree("2026-10-07T22:00", "07:00", dur(2, 0), listOf(hc("12:00-14:00"))))
        assertTrue(r.aucunRecouvrementHC)
        assertFalse(r.aGainHC)
        assertEquals(r.standard, r.heuresCreuses)
    }

    @Test
    fun aucunGainHCParRapportAuStandard() {
        // Les HC couvrent toute la fenêtre : le standard est déjà à 100 %.
        val r = ok(entree("2026-10-07T22:00", "07:00", dur(2, 0), listOf(hc("22:00-08:00"))))
        assertEquals(100, r.standard.pourcentHC)
        assertFalse(r.aGainHC)
        assertFalse(r.aucunRecouvrementHC)
        assertEquals(r.standard, r.heuresCreuses)
    }

    @Test
    fun sansPlageHCLOptionHCEstLeStandard() {
        val r = ok(entree("2026-10-07T22:00", "07:00", dur(2, 0)))
        assertFalse(r.hcDefinies)
        assertFalse(r.aucunRecouvrementHC)
        assertEquals(r.standard, r.heuresCreuses)
    }

    @Test
    fun plagesQuiSeChevauchentNeComptentQuUneFois() {
        val simple = ok(entree("2026-10-07T21:47", "07:00", dur(2, 30), listOf(hc("22:00-06:00"))))
        val doublee = ok(
            entree("2026-10-07T21:47", "07:00", dur(2, 30), listOf(hc("22:00-06:00"), hc("03:00-05:00"))),
        )
        assertEquals(simple.heuresCreuses, doublee.heuresCreuses)
        assertEquals(simple.standard, doublee.standard)
    }
}
