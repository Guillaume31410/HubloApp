package fr.perso.hublot.calcul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class PlagesHCTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun t(texte: String): Instant = LocalDateTime.parse(texte).atZone(paris).toInstant()
    private fun hc(texte: String): PlageHC = PlageHC.decoder(texte)!!
    private fun liste(vararg p: String) = p.map { hc(it) }

    @Test
    fun encodageEtDecodage() {
        val plages = liste("22:00-06:00", "12:30-14:00")
        assertEquals(plages, PlageHC.decoderListe(PlageHC.encoderListe(plages)))
        assertNull(PlageHC.decoder("25:00-06:00"))
        assertNull(PlageHC.decoder("n'importe quoi"))
        assertTrue(PlageHC.decoderListe(null).isEmpty())
    }

    @Test
    fun plageQuiPasseMinuit() {
        val p = hc("22:00-06:00")
        assertTrue(p.passeMinuit)
        assertEquals(
            Duration.ofHours(8),
            PlagesHC.dureeEnHC(listOf(p), paris, t("2026-10-07T20:00"), t("2026-10-08T08:00")),
        )
    }

    @Test
    fun dureeDecoupeeAuxBornes() {
        assertEquals(
            Duration.ofMinutes(103),
            PlagesHC.dureeEnHC(liste("22:00-06:00"), paris, t("2026-10-08T04:17"), t("2026-10-08T06:47")),
        )
    }

    @Test
    fun detectionDesChevauchements() {
        assertTrue(PlagesHC.seChevauchent(liste("22:00-06:00", "05:00-07:00")))
        assertTrue(PlagesHC.seChevauchent(liste("22:00-06:00", "23:00-01:00")))
        assertFalse(PlagesHC.seChevauchent(liste("22:00-06:00", "12:00-14:00")))
        // Plages qui se touchent sans se recouvrir.
        assertFalse(PlagesHC.seChevauchent(liste("22:00-02:00", "02:00-06:00")))
        assertFalse(PlagesHC.seChevauchent(liste("22:00-06:00")))
    }

    @Test
    fun fusionDePlagesQuiSeChevauchent() {
        assertEquals(liste("22:00-07:00"), PlagesHC.fusionner(liste("22:00-06:00", "05:00-07:00")))
        assertEquals(liste("21:00-06:00"), PlagesHC.fusionner(liste("22:00-06:00", "21:00-23:00")))
        assertEquals(liste("22:00-06:00"), PlagesHC.fusionner(liste("22:00-06:00", "23:00-01:00")))
    }

    @Test
    fun fusionConserveLesPlagesDisjointes() {
        assertEquals(
            liste("12:00-14:00", "22:00-06:00"),
            PlagesHC.fusionner(liste("22:00-06:00", "12:00-14:00")),
        )
    }

    @Test
    fun fusionRaccordeAMinuit() {
        assertEquals(liste("22:00-06:00"), PlagesHC.fusionner(liste("22:00-00:00", "00:00-06:00")))
    }

    @Test
    fun fusionJourneeEntiere() {
        val res = PlagesHC.fusionner(liste("00:00-12:00", "12:00-00:00"))
        assertEquals(1, res.size)
        assertEquals(res[0].debut, res[0].fin)
        assertEquals(
            Duration.ofHours(24),
            PlagesHC.dureeEnHC(res, paris, t("2026-10-07T00:00"), t("2026-10-08T00:00")),
        )
    }

    @Test
    fun estEnHC() {
        val p = liste("22:00-06:00")
        assertTrue(PlagesHC.estEnHC(p, paris, t("2026-10-07T23:30")))
        assertTrue(PlagesHC.estEnHC(p, paris, t("2026-10-07T22:00")))
        assertFalse(PlagesHC.estEnHC(p, paris, t("2026-10-07T06:00")))
        assertFalse(PlagesHC.estEnHC(p, paris, t("2026-10-07T13:00")))
        assertFalse(PlagesHC.estEnHC(emptyList(), paris, t("2026-10-07T23:30")))
    }

    @Test
    fun prochaineBascule() {
        val p = liste("22:00-06:00", "12:00-14:00")
        assertEquals(t("2026-10-07T12:00"), PlagesHC.prochaineBascule(p, paris, t("2026-10-07T09:00")))
        assertEquals(t("2026-10-07T14:00"), PlagesHC.prochaineBascule(p, paris, t("2026-10-07T12:00")))
        assertEquals(t("2026-10-08T06:00"), PlagesHC.prochaineBascule(p, paris, t("2026-10-07T23:00")))
        assertNull(PlagesHC.prochaineBascule(emptyList(), paris, t("2026-10-07T23:00")))
    }

    @Test
    fun aucuneBasculeSiLesHCCouvrentTout() {
        assertNull(PlagesHC.prochaineBascule(liste("06:00-06:00"), paris, t("2026-10-07T09:00")))
    }
}
