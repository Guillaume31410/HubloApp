package fr.perso.hublot

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import fr.perso.hublot.calcul.CalculDepart
import fr.perso.hublot.calcul.Entree
import fr.perso.hublot.calcul.Formats
import fr.perso.hublot.calcul.Option
import fr.perso.hublot.calcul.PlagesHC
import fr.perso.hublot.calcul.Resultat
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class MainActivity : Activity() {

    private lateinit var prefs: Prefs

    private lateinit var txtHeure: TextView
    private lateinit var carteFin: LinearLayout
    private lateinit var carteCycle: LinearLayout
    private lateinit var lblFin: TextView
    private lateinit var lblCycle: TextView
    private lateinit var txtFin: TextView
    private lateinit var txtCycle: TextView
    private lateinit var segment: View
    private lateinit var btnStandard: TextView
    private lateinit var btnHC: TextView
    private lateinit var txtLibelleResultat: TextView
    private lateinit var txtDelai: TextView
    private lateinit var txtDetail: TextView
    private lateinit var frise: FriseView
    private lateinit var txtComparaison: TextView
    private lateinit var txtChangementHeure: TextView
    private lateinit var puceFin: TextView
    private lateinit var puceRappels: TextView
    private lateinit var btnAction: Button

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Dernier calcul affiché : c'est lui qui est validé par « J'ai lancé la machine ». */
    private var dernierResultat: Resultat? = null

    private val handler = Handler(Looper.getMainLooper())
    private val tic = object : Runnable {
        override fun run() {
            rafraichir()
            programmerTic()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        txtHeure = findViewById(R.id.txtHeure)
        carteFin = findViewById(R.id.carteFin)
        carteCycle = findViewById(R.id.carteCycle)
        lblFin = findViewById(R.id.lblFin)
        lblCycle = findViewById(R.id.lblCycle)
        txtFin = findViewById(R.id.txtFin)
        txtCycle = findViewById(R.id.txtCycle)
        segment = findViewById(R.id.segment)
        btnStandard = findViewById(R.id.btnStandard)
        btnHC = findViewById(R.id.btnHC)
        txtLibelleResultat = findViewById(R.id.txtLibelleResultat)
        txtDelai = findViewById(R.id.txtDelai)
        txtDetail = findViewById(R.id.txtDetail)
        frise = findViewById(R.id.frise)
        txtComparaison = findViewById(R.id.txtComparaison)
        txtChangementHeure = findViewById(R.id.txtChangementHeure)
        puceFin = findViewById(R.id.puceFin)
        puceRappels = findViewById(R.id.puceRappels)
        btnAction = findViewById(R.id.btnAction)

        BordABord.activer(this, findViewById(R.id.racine))
        Notifs.creerCanaux(this)

        findViewById<View>(R.id.btnParametres).setOnClickListener {
            startActivity(Intent(this, ParametresActivity::class.java))
        }
        carteFin.setOnClickListener { choisirFin() }
        carteCycle.setOnClickListener { choisirCycle() }
        btnStandard.setOnClickListener { choisirMode(false) }
        btnHC.setOnClickListener { choisirMode(true) }
        btnAction.setOnClickListener {
            if (prefs.decompte != null) confirmerArret() else lancer()
        }
    }

    override fun onStart() {
        super.onStart()
        IconeDynamique.ecranVisible()
    }

    override fun onResume() {
        super.onResume()
        // Reprogramme au cas où une autorisation aurait changé entre-temps.
        if (prefs.decompte != null) Planificateur.programmerDecompte(this)
        IconeDynamique.planifier(this)
        rafraichir()
        programmerTic()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tic)
    }

    override fun onStop() {
        super.onStop()
        IconeDynamique.ecranMasque(this)
    }

    /** Rafraîchit l'écran au début de chaque minute. */
    private fun programmerTic() {
        handler.removeCallbacks(tic)
        val delai = 60_000 - (System.currentTimeMillis() % 60_000) + 50
        handler.postDelayed(tic, delai)
    }

    // --- Affichage ---

    private fun rafraichir() {
        val maintenant = Instant.now()
        var d = prefs.decompte
        if (d != null && d.fin <= maintenant.toEpochMilli()) {
            // L'heure de fin est passée : l'accueil se déverrouille tout seul.
            Planificateur.annulerDecompte(this)
            prefs.decompte = null
            d = null
        }
        if (d != null) afficherDecompte(d, maintenant) else afficherCalcul(maintenant)
    }

    private fun afficherCalcul(maintenant: Instant) {
        val finSouhaitee = prefs.finSouhaitee
        val cycle = prefs.cycle
        val modeHC = prefs.modeHC
        txtHeure.text = getString(R.string.il_est, Formats.heure(maintenant, zone))
        afficherSaisies(verrouille = false, finSouhaitee, cycle, modeHC)

        val r = CalculDepart.calculer(
            Entree(maintenant, zone, finSouhaitee, cycle, prefs.delaiMax, prefs.avanceMaxHC, prefs.plages),
        )
        dernierResultat = r
        btnAction.text = getString(R.string.j_ai_lance)
        btnAction.setBackgroundResource(R.drawable.bg_bouton_principal)
        btnAction.setTextColor(getColor(R.color.blanc))

        when (r) {
            is Resultat.Ok -> {
                val o = if (modeHC) r.heuresCreuses else r.standard
                txtLibelleResultat.text = getString(R.string.regler_sur)
                txtDelai.text = Formats.delai(o.delai)
                txtDetail.text = detailFin(o.fin, r.cible, r.maintenant)
                frise.visibility = View.VISIBLE
                frise.definir(r.maintenant, r.cible, PlagesHC.projeter(prefs.plages, zone, r.maintenant, r.cible), o.depart, o.fin, zone)
                txtComparaison.visibility = View.VISIBLE
                txtComparaison.text = comparaison(r, modeHC)
                txtChangementHeure.visibility = if (o.changementHeure) View.VISIBLE else View.GONE
                afficherPuces(o.fin)
                btnAction.isEnabled = true
            }
            is Resultat.Inatteignable -> {
                txtLibelleResultat.text = getString(R.string.inatteignable)
                txtDelai.text = "—"
                txtDetail.text = "Il faudrait un départ différé de ${Formats.delai(r.delaiNecessaire)} " +
                    "pour finir ${Formats.jourRelatif(r.cible, maintenant, zone)} à ${Formats.heure(r.cible, zone)}, " +
                    "mais la machine accepte au plus ${Formats.delai(r.delaiMax)}."
                masquerDetails()
            }
            is Resultat.EntreeInvalide -> {
                txtLibelleResultat.text = getString(R.string.calcul_impossible)
                txtDelai.text = "—"
                txtDetail.text = r.raison
                masquerDetails()
            }
        }
    }

    private fun masquerDetails() {
        frise.vider()
        frise.visibility = View.GONE
        txtComparaison.visibility = View.GONE
        txtChangementHeure.visibility = View.GONE
        puceFin.visibility = View.GONE
        puceRappels.visibility = View.GONE
        btnAction.isEnabled = false
    }

    private fun afficherDecompte(d: Decompte, maintenant: Instant) {
        val fin = Instant.ofEpochMilli(d.fin)
        val cible = Instant.ofEpochMilli(d.cible)
        val restant = Duration.ofMillis(d.fin - maintenant.toEpochMilli() + 59_999).toMinutes()

        txtHeure.text = getString(R.string.il_est_decompte, Formats.heure(maintenant, zone))
        afficherSaisies(
            verrouille = true,
            cible.atZone(zone).toLocalTime(),
            Duration.ofMinutes(d.cycleMin),
            d.modeHC,
        )
        dernierResultat = null

        txtLibelleResultat.text = getString(R.string.machine_reglee_sur)
        txtDelai.text = Formats.delai(Duration.ofMinutes(d.delaiMin))
        txtDetail.text = "Fin prévue ${Formats.jourRelatif(fin, maintenant, zone)} à ${Formats.heure(fin, zone)}, " +
            "dans ${Formats.duree(Duration.ofMinutes(restant))}."

        frise.visibility = View.VISIBLE
        frise.definir(
            maintenant,
            maxOf(cible, fin),
            PlagesHC.projeter(prefs.plages, zone, maintenant, maxOf(cible, fin)),
            Instant.ofEpochMilli(d.depart),
            fin,
            zone,
        )
        txtComparaison.visibility = View.VISIBLE
        txtComparaison.text = "${Formats.duree(Duration.ofMinutes(d.minutesHC))} en heures creuses (${d.pourcentHC} %)."
        txtChangementHeure.visibility = if (d.changementHeure) View.VISIBLE else View.GONE
        afficherPuces(fin)

        btnAction.isEnabled = true
        btnAction.text = getString(R.string.arreter_decompte)
        btnAction.setBackgroundResource(R.drawable.bg_bouton_danger)
        btnAction.setTextColor(getColor(R.color.danger))
    }

    private fun afficherSaisies(verrouille: Boolean, fin: LocalTime, cycle: Duration, modeHC: Boolean) {
        txtFin.text = "%02d:%02d".format(fin.hour, fin.minute)
        txtCycle.text = Formats.delai(cycle)
        val cadenas = if (verrouille) getDrawable(R.drawable.ic_cadenas) else null
        for (lbl in listOf(lblFin, lblCycle)) lbl.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, cadenas, null)
        for (v in listOf(carteFin, carteCycle, btnStandard, btnHC)) {
            v.isEnabled = !verrouille
            v.isClickable = !verrouille
        }
        val alpha = if (verrouille) 0.55f else 1f
        carteFin.alpha = alpha
        carteCycle.alpha = alpha
        segment.alpha = alpha

        btnStandard.isSelected = !modeHC
        btnHC.isSelected = modeHC
        btnStandard.setBackgroundResource(if (!modeHC) R.drawable.bg_segment_choisi else 0)
        btnHC.setBackgroundResource(if (modeHC) R.drawable.bg_segment_choisi else 0)
        btnStandard.setTextColor(getColor(if (!modeHC) R.color.accent else R.color.texte_secondaire))
        btnHC.setTextColor(getColor(if (modeHC) R.color.accent else R.color.texte_secondaire))
    }

    private fun afficherPuces(fin: Instant) {
        if (prefs.notifFin) {
            puceFin.visibility = View.VISIBLE
            puceFin.text = getString(R.string.puce_fin, Formats.heure(fin, zone))
        } else {
            puceFin.visibility = View.GONE
        }
        val rappels = prefs.rappelsActifs()
        if (rappels.isNotEmpty()) {
            puceRappels.visibility = View.VISIBLE
            puceRappels.text = "Rappels " + rappels.joinToString(" · ") { if (it >= 60) "${it / 60} h" else "$it min" }
        } else {
            puceRappels.visibility = View.GONE
        }
    }

    private fun detailFin(fin: Instant, cible: Instant, maintenant: Instant): String {
        val jour = Formats.jourRelatif(fin, maintenant, zone)
        val avance = Duration.between(fin, cible)
        val debut = "Fin prévue $jour à ${Formats.heure(fin, zone)}"
        return if (avance.isZero) "$debut, pile à l'heure souhaitée." else "$debut, soit ${Formats.duree(avance)} avant l'heure souhaitée."
    }

    private fun comparaison(r: Resultat.Ok, modeHC: Boolean): CharSequence {
        val std = r.standard
        val hc = r.heuresCreuses
        if (!r.hcDefinies) {
            return if (modeHC) {
                "Aucune plage d'heures creuses n'est définie : ajoutez-en dans les paramètres. Réglage au plus juste conservé."
            } else {
                "Aucune plage d'heures creuses n'est définie."
            }
        }
        if (!modeHC) {
            val base = "${Formats.duree(std.dureeHC)} en heures creuses (${std.pourcentHC} %)."
            return if (r.aGainHC) "$base L'option heures creuses en ajouterait ${Formats.duree(r.gainHC)}." else base
        }
        if (r.aucunRecouvrementHC) {
            return "Aucun départ possible ne tombe en heures creuses : réglage au plus juste conservé."
        }
        if (!r.aGainHC) {
            return "Aucun gain en heures creuses par rapport au réglage au plus juste " +
                "(${Formats.duree(std.dureeHC)}, ${std.pourcentHC} %) : on le conserve."
        }
        val fort = "${Formats.duree(hc.dureeHC)} en heures creuses (${hc.pourcentHC} %)"
        val texte = SpannableStringBuilder(fort)
            .append(", contre ${Formats.duree(std.dureeHC)} (${std.pourcentHC} %) en réglant au plus juste.")
        texte.setSpan(StyleSpan(Typeface.BOLD), 0, fort.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        texte.setSpan(ForegroundColorSpan(getColor(R.color.accent)), 0, fort.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return texte
    }

    // --- Saisies ---

    private fun choisirMode(hc: Boolean) {
        prefs.modeHC = hc
        rafraichir()
    }

    private fun choisirFin() {
        val actuelle = prefs.finSouhaitee
        TimePickerDialog(
            this,
            { _, h, m ->
                prefs.finSouhaitee = LocalTime.of(h, m)
                rafraichir()
            },
            actuelle.hour,
            actuelle.minute,
            true,
        ).show()
    }

    private fun choisirCycle() {
        val vue = layoutInflater.inflate(R.layout.dialog_duree, null)
        val heures = vue.findViewById<NumberPicker>(R.id.pickHeures)
        val minutes = vue.findViewById<NumberPicker>(R.id.pickMinutes)
        val actuel = prefs.cycle
        heures.minValue = 0
        heures.maxValue = 23
        heures.value = actuel.toHours().toInt()
        minutes.minValue = 0
        minutes.maxValue = 59
        minutes.setFormatter { "%02d".format(it) }
        minutes.value = (actuel.toMinutes() % 60).toInt()

        AlertDialog.Builder(this)
            .setTitle(R.string.duree_cycle)
            .setView(vue)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val duree = Duration.ofHours(heures.value.toLong()).plusMinutes(minutes.value.toLong())
                if (duree.isZero) {
                    Toast.makeText(this, R.string.duree_nulle, Toast.LENGTH_SHORT).show()
                } else {
                    prefs.cycle = duree
                    rafraichir()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- Lancement et arrêt ---

    private fun lancer() {
        val r = dernierResultat as? Resultat.Ok ?: return
        val modeHC = prefs.modeHC
        val o: Option = if (modeHC) r.heuresCreuses else r.standard

        // La machine est déjà réglée sur la valeur affichée : on garde ce délai et on
        // le compte à partir de maintenant (moment de la validation).
        val t0 = CalculDepart.arrondiMinuteSuperieure(Instant.now())
        val depart = t0.plus(o.delai)
        val fin = depart.plus(o.cycle)
        val dureeHC = PlagesHC.dureeEnHC(prefs.plages, zone, depart, fin)
        val changement = CalculDepart.changementHeureEntre(zone, t0, fin)

        prefs.decompte = Decompte(
            lancement = t0.toEpochMilli(),
            depart = depart.toEpochMilli(),
            fin = fin.toEpochMilli(),
            cible = r.cible.toEpochMilli(),
            delaiMin = o.delai.toMinutes(),
            cycleMin = o.cycle.toMinutes(),
            modeHC = modeHC,
            minutesHC = dureeHC.toMinutes(),
            pourcentHC = Math.round(dureeHC.seconds * 100.0 / o.cycle.seconds).toInt(),
            changementHeure = changement,
        )
        // Un nouveau lancement remplace les rappels précédents.
        Planificateur.programmerDecompte(this)
        rafraichir()

        if (changement) {
            AlertDialog.Builder(this)
                .setTitle(R.string.titre_changement_heure)
                .setMessage(
                    "L'heure change d'ici la fin du cycle. Le départ différé de ${Formats.delai(o.delai)} " +
                        "a été calculé en durée réelle : la fin aura bien lieu à ${Formats.heure(fin, zone)}, " +
                        "à la nouvelle heure affichée par le téléphone.",
                )
                .setPositiveButton(android.R.string.ok) { _, _ -> verifierNotifications() }
                .setCancelable(false)
                .show()
        } else {
            verifierNotifications()
        }
    }

    private fun confirmerArret() {
        AlertDialog.Builder(this)
            .setTitle(R.string.arreter_decompte_titre)
            .setMessage(R.string.arreter_decompte_message)
            .setPositiveButton(R.string.arreter) { _, _ ->
                Planificateur.annulerDecompte(this)
                prefs.decompte = null
                rafraichir()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- Permissions ---

    private fun verifierNotifications() {
        if (!prefs.uneNotificationActive()) {
            verifierAlarmesExactes()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFS)
            return
        }
        if (!Notifs.autorisees(this)) {
            expliquerNotificationsBloquees { verifierAlarmesExactes() }
            return
        }
        verifierAlarmesExactes()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_NOTIFS) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            verifierAlarmesExactes()
        } else {
            expliquerNotificationsBloquees { verifierAlarmesExactes() }
        }
    }

    private fun expliquerNotificationsBloquees(ensuite: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.notifs_refusees_titre)
            .setMessage(R.string.notifs_refusees_message)
            .setPositiveButton(R.string.ouvrir_reglages) { _, _ -> Autorisations.ouvrirReglagesNotifications(this) }
            .setNegativeButton(R.string.plus_tard) { _, _ -> ensuite() }
            .show()
    }

    private fun verifierAlarmesExactes() {
        if (Planificateur.alarmesExactesPermises(this)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.alarmes_exactes_titre)
            .setMessage(R.string.alarmes_exactes_message)
            .setPositiveButton(R.string.ouvrir_reglages) { _, _ -> Autorisations.ouvrirReglagesAlarmesExactes(this) }
            .setNegativeButton(R.string.plus_tard, null)
            .show()
    }

    private companion object {
        const val REQ_NOTIFS = 1
    }
}

/** Ouverture des écrans de réglages système concernés. */
object Autorisations {

    fun ouvrirReglagesNotifications(activity: Activity) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        lancerOuDetails(activity, intent)
    }

    fun ouvrirReglagesAlarmesExactes(activity: Activity) {
        if (Build.VERSION.SDK_INT < 31) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}"))
        lancerOuDetails(activity, intent)
    }

    private fun lancerOuDetails(activity: Activity, intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")),
            )
        }
    }
}
