package fr.perso.hublot

import android.content.Context
import android.content.SharedPreferences
import fr.perso.hublot.calcul.PlageHC
import java.time.Duration
import java.time.LocalTime

/** Décompte en cours, persisté pour survivre au redémarrage du téléphone. */
data class Decompte(
    /** Instant (ms) où l'utilisateur a validé : point de départ du délai. */
    val lancement: Long,
    val depart: Long,
    val fin: Long,
    val cible: Long,
    val delaiMin: Long,
    val cycleMin: Long,
    val modeHC: Boolean,
    val minutesHC: Long,
    val pourcentHC: Int,
    val changementHeure: Boolean,
)

/** Paramètres et état, stockés en SharedPreferences. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("hublot", Context.MODE_PRIVATE)

    var plages: List<PlageHC>
        get() = PlageHC.decoderListe(sp.getString(K_PLAGES, PLAGES_DEFAUT))
        set(v) = sp.edit().putString(K_PLAGES, PlageHC.encoderListe(v)).apply()

    var avanceMaxHC: Duration
        get() = Duration.ofMinutes(sp.getLong(K_AVANCE, 60))
        set(v) = sp.edit().putLong(K_AVANCE, v.toMinutes()).apply()

    var delaiMax: Duration
        get() = Duration.ofMinutes(sp.getLong(K_DELAI_MAX, 24 * 60))
        set(v) = sp.edit().putLong(K_DELAI_MAX, v.toMinutes()).apply()

    var rappel60: Boolean
        get() = sp.getBoolean(K_R60, false)
        set(v) = sp.edit().putBoolean(K_R60, v).apply()

    var rappel30: Boolean
        get() = sp.getBoolean(K_R30, false)
        set(v) = sp.edit().putBoolean(K_R30, v).apply()

    var rappel10: Boolean
        get() = sp.getBoolean(K_R10, true)
        set(v) = sp.edit().putBoolean(K_R10, v).apply()

    var notifFin: Boolean
        get() = sp.getBoolean(K_FIN, true)
        set(v) = sp.edit().putBoolean(K_FIN, v).apply()

    var iconeDynamique: Boolean
        get() = sp.getBoolean(K_ICONE, false)
        set(v) = sp.edit().putBoolean(K_ICONE, v).apply()

    /** Dernière heure de fin saisie. */
    var finSouhaitee: LocalTime
        get() = LocalTime.ofSecondOfDay(sp.getInt(K_FIN_SOUHAITEE, 7 * 3600).toLong())
        set(v) = sp.edit().putInt(K_FIN_SOUHAITEE, v.toSecondOfDay()).apply()

    /** Dernière durée de cycle saisie. */
    var cycle: Duration
        get() = Duration.ofMinutes(sp.getLong(K_CYCLE, 120))
        set(v) = sp.edit().putLong(K_CYCLE, v.toMinutes()).apply()

    /** Dernier onglet choisi : heures creuses ou au plus juste. */
    var modeHC: Boolean
        get() = sp.getBoolean(K_MODE_HC, true)
        set(v) = sp.edit().putBoolean(K_MODE_HC, v).apply()

    /** Rappels avant la fin actuellement cochés, en minutes. */
    fun rappelsActifs(): List<Long> = buildList {
        if (rappel60) add(60L)
        if (rappel30) add(30L)
        if (rappel10) add(10L)
    }

    fun uneNotificationActive(): Boolean = notifFin || rappelsActifs().isNotEmpty()

    var decompte: Decompte?
        get() {
            if (!sp.getBoolean(D_ACTIF, false)) return null
            return Decompte(
                lancement = sp.getLong(D_LANCEMENT, 0),
                depart = sp.getLong(D_DEPART, 0),
                fin = sp.getLong(D_FIN, 0),
                cible = sp.getLong(D_CIBLE, 0),
                delaiMin = sp.getLong(D_DELAI, 0),
                cycleMin = sp.getLong(D_CYCLE, 0),
                modeHC = sp.getBoolean(D_MODE_HC, false),
                minutesHC = sp.getLong(D_MIN_HC, 0),
                pourcentHC = sp.getInt(D_POURCENT, 0),
                changementHeure = sp.getBoolean(D_CHGT, false),
            )
        }
        set(d) {
            val e = sp.edit()
            if (d == null) {
                e.putBoolean(D_ACTIF, false)
            } else {
                e.putBoolean(D_ACTIF, true)
                    .putLong(D_LANCEMENT, d.lancement)
                    .putLong(D_DEPART, d.depart)
                    .putLong(D_FIN, d.fin)
                    .putLong(D_CIBLE, d.cible)
                    .putLong(D_DELAI, d.delaiMin)
                    .putLong(D_CYCLE, d.cycleMin)
                    .putBoolean(D_MODE_HC, d.modeHC)
                    .putLong(D_MIN_HC, d.minutesHC)
                    .putInt(D_POURCENT, d.pourcentHC)
                    .putBoolean(D_CHGT, d.changementHeure)
            }
            // commit() : l'état doit être écrit avant qu'un récepteur ne le relise.
            e.commit()
        }

    private companion object {
        const val PLAGES_DEFAUT = "22:00-06:00"
        const val K_PLAGES = "plages"
        const val K_AVANCE = "avance_max_hc_min"
        const val K_DELAI_MAX = "delai_max_min"
        const val K_R60 = "rappel_60"
        const val K_R30 = "rappel_30"
        const val K_R10 = "rappel_10"
        const val K_FIN = "notif_fin"
        const val K_ICONE = "icone_dynamique"
        const val K_FIN_SOUHAITEE = "fin_souhaitee_s"
        const val K_CYCLE = "cycle_min"
        const val K_MODE_HC = "mode_hc"
        const val D_ACTIF = "d_actif"
        const val D_LANCEMENT = "d_lancement"
        const val D_DEPART = "d_depart"
        const val D_FIN = "d_fin"
        const val D_CIBLE = "d_cible"
        const val D_DELAI = "d_delai"
        const val D_CYCLE = "d_cycle"
        const val D_MODE_HC = "d_mode_hc"
        const val D_MIN_HC = "d_min_hc"
        const val D_POURCENT = "d_pourcent"
        const val D_CHGT = "d_chgt"
    }
}
