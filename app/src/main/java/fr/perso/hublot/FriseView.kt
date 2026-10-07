package fr.perso.hublot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import fr.perso.hublot.calcul.Formats
import fr.perso.hublot.calcul.Intervalle
import java.time.Instant
import java.time.ZoneId

/**
 * Frise « maintenant → heure souhaitée » : heures creuses en clair, cycle de lavage
 * en foncé, repère de l'heure souhaitée en orange.
 */
class FriseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var debut = 0L
    private var fin = 0L
    private var hc: List<Intervalle> = emptyList()
    private var cycleDebut = 0L
    private var cycleFin = 0L
    private var zone: ZoneId = ZoneId.systemDefault()
    private var vide = true

    private val dp = resources.displayMetrics.density
    private val taille12sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)

    private val pPiste = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.frise_piste) }
    private val pHC = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.frise_hc) }
    private val pCycle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent) }
    private val pMaintenant = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.texte)
        strokeWidth = 1.5f * dp
    }
    private val pCible = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.cible)
        strokeWidth = 2f * dp
    }
    private val pTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.texte_secondaire)
        textSize = taille12sp
    }
    private val pTexteFort = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.texte)
        textSize = taille12sp
        typeface = Typeface.DEFAULT_BOLD
    }

    private val rect = RectF()
    private val clip = Path()

    fun definir(debut: Instant, fin: Instant, hc: List<Intervalle>, cycleDebut: Instant, cycleFin: Instant, zone: ZoneId) {
        this.debut = debut.toEpochMilli()
        this.fin = fin.toEpochMilli()
        this.hc = hc
        this.cycleDebut = cycleDebut.toEpochMilli()
        this.cycleFin = cycleFin.toEpochMilli()
        this.zone = zone
        vide = this.fin <= this.debut
        contentDescription = "Cycle de ${Formats.heure(cycleDebut, zone)} à ${Formats.heure(cycleFin, zone)}, " +
            "heure souhaitée ${Formats.heure(fin, zone)}"
        invalidate()
    }

    fun vider() {
        vide = true
        contentDescription = null
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hauteur = (64 * dp).toInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(hauteur, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (vide) return
        val gauche = paddingLeft + 1f * dp
        val droite = width - paddingRight - 1f * dp
        val haut = 22f * dp
        val bas = haut + 14f * dp
        val rayon = 7f * dp

        fun x(t: Long): Float {
            val r = ((t - debut).toDouble() / (fin - debut)).coerceIn(0.0, 1.0)
            return (gauche + r * (droite - gauche)).toFloat()
        }

        // Piste et plages HC, découpées à la forme arrondie de la piste.
        rect.set(gauche, haut, droite, bas)
        canvas.drawRoundRect(rect, rayon, rayon, pPiste)
        canvas.save()
        clip.reset()
        clip.addRoundRect(rect, rayon, rayon, Path.Direction.CW)
        canvas.clipPath(clip)
        for (iv in hc) {
            canvas.drawRect(x(iv.debut.toEpochMilli()), haut, x(iv.fin.toEpochMilli()), bas, pHC)
        }
        canvas.restore()

        // Cycle de lavage.
        val cx1 = x(cycleDebut)
        val cx2 = maxOf(x(cycleFin), cx1 + 4 * dp)
        rect.set(cx1, haut + 3 * dp, cx2, bas - 3 * dp)
        canvas.drawRoundRect(rect, 4 * dp, 4 * dp, pCycle)

        // Repères : maintenant (à gauche) et heure souhaitée (à droite).
        canvas.drawLine(gauche, 4 * dp, gauche, bas + 2 * dp, pMaintenant)
        pTexteFort.textAlign = Paint.Align.LEFT
        canvas.drawText("maintenant", gauche + 4 * dp, 14 * dp, pTexteFort)
        val xCible = x(fin)
        canvas.drawLine(xCible, 4 * dp, xCible, bas + 2 * dp, pCible)
        pTexteFort.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formats.heure(Instant.ofEpochMilli(fin), zone), xCible - 4 * dp, 14 * dp, pTexteFort)

        // Bornes des heures creuses sous la piste, sans chevauchement de libellés.
        pTexte.textAlign = Paint.Align.CENTER
        val yBas = bas + 18 * dp
        var dernierX = -1e9f
        val bornes = hc.flatMap { listOf(it.debut.toEpochMilli(), it.fin.toEpochMilli()) }
            .filter { it > debut && it < fin }
            .sorted()
        for (t in bornes) {
            val bx = x(t)
            val libelle = Formats.heure(Instant.ofEpochMilli(t), zone)
            val demi = pTexte.measureText(libelle) / 2
            if (bx - demi < gauche || bx + demi > droite) continue
            if (bx - demi < dernierX + 8 * dp) continue
            canvas.drawText(libelle, bx, yBas, pTexte)
            dernierX = bx + demi
        }
    }
}
