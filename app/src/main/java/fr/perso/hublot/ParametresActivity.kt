package fr.perso.hublot

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import fr.perso.hublot.calcul.CalculDepart
import fr.perso.hublot.calcul.Formats
import fr.perso.hublot.calcul.PlageHC
import fr.perso.hublot.calcul.PlagesHC
import java.time.Duration
import java.time.LocalTime

class ParametresActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var listePlages: LinearLayout
    private lateinit var txtAucunePlage: TextView
    private lateinit var txtAvance: TextView
    private lateinit var txtDelaiMax: TextView
    private lateinit var txtEtatNotifs: TextView
    private lateinit var btnReglagesNotifs: Button
    private lateinit var txtEtatAlarmes: TextView
    private lateinit var btnReglagesAlarmes: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parametres)
        prefs = Prefs(this)
        BordABord.activer(this, findViewById(R.id.racine))

        listePlages = findViewById(R.id.listePlages)
        txtAucunePlage = findViewById(R.id.txtAucunePlage)
        txtAvance = findViewById(R.id.txtAvance)
        txtDelaiMax = findViewById(R.id.txtDelaiMax)
        txtEtatNotifs = findViewById(R.id.txtEtatNotifs)
        btnReglagesNotifs = findViewById(R.id.btnReglagesNotifs)
        txtEtatAlarmes = findViewById(R.id.txtEtatAlarmes)
        btnReglagesAlarmes = findViewById(R.id.btnReglagesAlarmes)

        findViewById<View>(R.id.btnRetour).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAjouterPlage).setOnClickListener { ajouterPlage() }

        // Avance max HC : pas de 30 min, minimum 30 min.
        findViewById<View>(R.id.btnAvanceMoins).setOnClickListener { modifierAvance(-1) }
        findViewById<View>(R.id.btnAvancePlus).setOnClickListener { modifierAvance(+1) }
        // Délai max de la machine : pas de 30 min, de 30 min à 48 h.
        findViewById<View>(R.id.btnDelaiMoins).setOnClickListener { modifierDelaiMax(-1) }
        findViewById<View>(R.id.btnDelaiPlus).setOnClickListener { modifierDelaiMax(+1) }

        lierCase(R.id.caseRappel60, prefs.rappel60) { prefs.rappel60 = it }
        lierCase(R.id.caseRappel30, prefs.rappel30) { prefs.rappel30 = it }
        lierCase(R.id.caseRappel10, prefs.rappel10) { prefs.rappel10 = it }
        lierCase(R.id.caseFin, prefs.notifFin) { prefs.notifFin = it }

        val icone = findViewById<Switch>(R.id.switchIcone)
        icone.isChecked = prefs.iconeDynamique
        icone.setOnCheckedChangeListener { _, actif ->
            prefs.iconeDynamique = actif
            IconeDynamique.planifier(this)
            Toast.makeText(this, R.string.icone_appliquee_en_sortant, Toast.LENGTH_SHORT).show()
        }

        btnReglagesNotifs.setOnClickListener { Autorisations.ouvrirReglagesNotifications(this) }
        btnReglagesAlarmes.setOnClickListener { Autorisations.ouvrirReglagesAlarmesExactes(this) }

        afficherPlages()
        afficherDurees()
    }

    override fun onStart() {
        super.onStart()
        IconeDynamique.ecranVisible()
    }

    override fun onResume() {
        super.onResume()
        afficherAutorisations()
    }

    override fun onStop() {
        super.onStop()
        IconeDynamique.ecranMasque(this)
    }

    // --- Plages d'heures creuses ---

    private fun afficherPlages() {
        listePlages.removeAllViews()
        val plages = prefs.plages
        txtAucunePlage.visibility = if (plages.isEmpty()) View.VISIBLE else View.GONE
        for ((index, p) in plages.withIndex()) {
            val ligne = layoutInflater.inflate(R.layout.item_plage, listePlages, false)
            ligne.findViewById<TextView>(R.id.txtPlage).text = p.toString()
            ligne.findViewById<View>(R.id.btnSupprimer).apply {
                contentDescription = getString(R.string.supprimer_plage, p.toString())
                setOnClickListener { supprimerPlage(index) }
            }
            listePlages.addView(ligne)
        }
    }

    private fun ajouterPlage() {
        demanderHeure(getString(R.string.debut_hc), LocalTime.of(22, 0)) { debut ->
            demanderHeure(getString(R.string.fin_hc), LocalTime.of(6, 0)) { fin ->
                if (debut == fin) {
                    Toast.makeText(this, R.string.plage_vide, Toast.LENGTH_LONG).show()
                } else {
                    enregistrerPlages(prefs.plages + PlageHC(debut, fin))
                    proposerFusion()
                }
            }
        }
    }

    private fun supprimerPlage(index: Int) {
        val plages = prefs.plages.toMutableList()
        if (index !in plages.indices) return
        val p = plages.removeAt(index)
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirmer_suppression, p.toString()))
            .setPositiveButton(R.string.supprimer) { _, _ -> enregistrerPlages(plages) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun proposerFusion() {
        if (!PlagesHC.seChevauchent(prefs.plages)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.fusion_titre)
            .setMessage(R.string.fusion_question)
            .setPositiveButton(R.string.fusionner) { _, _ -> enregistrerPlages(PlagesHC.fusionner(prefs.plages)) }
            .setNegativeButton(R.string.garder_separees) { _, _ ->
                Toast.makeText(this, R.string.fusion_refusee, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun enregistrerPlages(plages: List<PlageHC>) {
        prefs.plages = plages.sortedBy { it.debut }
        afficherPlages()
        IconeDynamique.planifier(this)
    }

    private fun demanderHeure(titre: String, initiale: LocalTime, suite: (LocalTime) -> Unit) {
        val dlg = TimePickerDialog(this, { _, h, m -> suite(LocalTime.of(h, m)) }, initiale.hour, initiale.minute, true)
        dlg.setTitle(titre)
        dlg.show()
    }

    // --- Durées ---

    private fun modifierAvance(sens: Int) {
        val nouvelle = prefs.avanceMaxHC.plus(CalculDepart.PAS.multipliedBy(sens.toLong()))
        if (nouvelle < CalculDepart.PAS || nouvelle > Duration.ofHours(12)) return
        prefs.avanceMaxHC = nouvelle
        afficherDurees()
    }

    private fun modifierDelaiMax(sens: Int) {
        val nouveau = prefs.delaiMax.plus(CalculDepart.PAS.multipliedBy(sens.toLong()))
        if (nouveau < CalculDepart.PAS || nouveau > Duration.ofHours(48)) return
        prefs.delaiMax = nouveau
        afficherDurees()
    }

    private fun afficherDurees() {
        txtAvance.text = Formats.delai(prefs.avanceMaxHC)
        txtDelaiMax.text = Formats.delai(prefs.delaiMax)
    }

    // --- Notifications ---

    private fun lierCase(id: Int, valeur: Boolean, ecrire: (Boolean) -> Unit) {
        val c = findViewById<CheckBox>(id)
        c.isChecked = valeur
        c.setOnCheckedChangeListener { _: CompoundButton, coche: Boolean ->
            ecrire(coche)
            // Les rappels d'un décompte en cours suivent les nouvelles cases.
            if (prefs.decompte != null) Planificateur.programmerDecompte(this)
            if (coche) demanderPermissionNotifications()
        }
    }

    private fun demanderPermissionNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        afficherAutorisations()
    }

    private fun afficherAutorisations() {
        val notifsOk = Notifs.autorisees(this)
        txtEtatNotifs.text = getString(if (notifsOk) R.string.notifs_ok else R.string.notifs_bloquees)
        btnReglagesNotifs.visibility = if (notifsOk) View.GONE else View.VISIBLE

        val alarmesOk = Planificateur.alarmesExactesPermises(this)
        txtEtatAlarmes.visibility = if (alarmesOk) View.GONE else View.VISIBLE
        btnReglagesAlarmes.visibility = if (alarmesOk) View.GONE else View.VISIBLE
    }

    private companion object {
        const val REQ_NOTIFS = 2
    }
}
