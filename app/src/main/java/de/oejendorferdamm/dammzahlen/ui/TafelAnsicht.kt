/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.oejendorferdamm.dammzahlen.R
import de.oejendorferdamm.dammzahlen.model.Figur
import de.oejendorferdamm.dammzahlen.model.LernGeometrie
import de.oejendorferdamm.dammzahlen.model.Stange
import de.oejendorferdamm.dammzahlen.model.Stellentafel
import de.oejendorferdamm.dammzahlen.model.Wuerfel
import de.oejendorferdamm.dammzahlen.model.Zahlenhaus
import de.oejendorferdamm.dammzahlen.model.Zahlenmauer
import de.oejendorferdamm.dammzahlen.model.Aufgabe
import de.oejendorferdamm.dammzahlen.model.Geometrie
import de.oejendorferdamm.dammzahlen.model.Karte
import de.oejendorferdamm.dammzahlen.model.Linie
import de.oejendorferdamm.dammzahlen.model.Plaettchen
import de.oejendorferdamm.dammzahlen.model.Punkt
import de.oejendorferdamm.dammzahlen.model.Rahmen
import de.oejendorferdamm.dammzahlen.model.Seite
import de.oejendorferdamm.dammzahlen.model.Strahl
import de.oejendorferdamm.dammzahlen.model.Teil
import de.oejendorferdamm.dammzahlen.model.VORBILD_BREITE_W
import de.oejendorferdamm.dammzahlen.model.VORBILD_HOEHE_W
import kotlin.math.abs
import kotlin.math.hypot

/** Was ein Finger gerade tut. */
private class Zug(
    val art: Art,
    val id: Long = -1,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val start: Punkt,
    val vorher: Seite,
    val neu: Boolean = false,
    /** Beim Verschieben eines Behälters wandern seine Plättchen/Figuren mit (Id → Versatz). */
    val mit: Map<Long, Punkt> = emptyMap(),
    val kugel: Pair<Int, Int>? = null,
    /** Im Kindermodus: Behälter dürfen angetippt, aber nicht verschoben werden. */
    val nurTippen: Boolean = false
) {
    enum class Art { ZIEHEN, MARKE, MALEN, KUGEL }
    var bewegt = false
    val punkte = ArrayList<Punkt>()
}

/** Was beim Antippen außerhalb der Tafel passieren soll (Fenster öffnen). */
class TafelRueckrufe(
    val karte: (Karte) -> Unit,
    /** Ziffernblock: Teil, Zelle, bisheriger Wert, Lehrkraft (darf „fest“ setzen). */
    val zahl: (Long, Int, Int?, Boolean) -> Unit,
    /** Menü eines Behälters (nur Lehrkraft). */
    val menue: (Teil) -> Unit
)

/**
 * Die Tafel: zeichnet die aktive Seite und setzt Berührungen um – mehrere Finger gleichzeitig,
 * damit auch zwei Kinder nebeneinander schieben können.
 */
@Composable
fun TafelAnsicht(zustand: TafelZustand, hintergrund: Int, rueckrufe: TafelRueckrufe, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val zeichner = remember {
        Zeichner(context.resources.getFont(R.font.grundschrift_regular), context.resources.getFont(R.font.grundschrift_bold))
    }
    val rufe by rememberUpdatedState(rueckrufe)
    var ziehtGerade by remember { mutableStateOf(false) }
    var malen by remember { mutableStateOf<Linie?>(null) }

    // Einblend-Animationen: solange etwas „erscheint“, jedes Bild neu zeichnen
    var uhr by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(zustand.seiten) {
        while (zustand.erschienen.values.any { System.currentTimeMillis() - it < 450 }) {
            withFrameMillis { uhr = System.currentTimeMillis() }
        }
        val jetzt = System.currentTimeMillis()
        zustand.erschienen.entries.removeAll { jetzt - it.value > 1000 }
        uhr = jetzt
    }

    Canvas(
        modifier
            .semantics { contentDescription = "Tafel" }
            .pointerInput(zustand) {
                val zuege = HashMap<PointerId, Zug>()
                fun einheit() = minOf(size.width / VORBILD_BREITE_W, size.height / VORBILD_HOEHE_W)
                fun welt(x: Float, y: Float): Punkt {
                    val e = einheit()
                    return Punkt((x - (size.width - VORBILD_BREITE_W * e) / 2f) / e, (y - (size.height - VORBILD_HOEHE_W * e) / 2f) / e)
                }
                awaitPointerEventScope {
                    while (true) {
                        val ereignis = awaitPointerEvent()
                        for (c in ereignis.changes) {
                            val p = welt(c.position.x, c.position.y)
                            when {
                                c.pressed && !c.previousPressed -> beginne(zustand, p, zuege.values.map { it.id }.toSet())?.let { zuege[c.id] = it }
                                c.pressed -> zuege[c.id]?.let { bewege(zustand, it, p) }
                                !c.pressed && c.previousPressed -> zuege.remove(c.id)?.let { beende(zustand, it, p, rufe) }
                            }
                            c.consume()
                        }
                        ziehtGerade = zuege.values.any { it.art == Zug.Art.ZIEHEN && it.bewegt && !it.neu }
                        malen = zuege.values.firstOrNull { it.art == Zug.Art.MALEN }?.let { Linie(zustand.stiftFarbe, 7f, it.punkte.toList()) }
                    }
                }
            }
    ) {
        val e = minOf(size.width / VORBILD_BREITE_W, size.height / VORBILD_HOEHE_W)
        val jetzt = uhr
        drawRect(androidx.compose.ui.graphics.Color(hintergrund))
        drawIntoCanvas { leinwand ->
            val n = leinwand.nativeCanvas
            n.save()
            n.translate((size.width - VORBILD_BREITE_W * e) / 2f, (size.height - VORBILD_HOEHE_W * e) / 2f)
            n.scale(e, e)
            zeichner.zeichne(
                n, zustand.seite, zustand.terme, zustand.vorhang,
                mitVorrat = zustand.ablauf == null, papierkorb = ziehtGerade && !zustand.kindermodus, marken = zustand.marken
            ) { id ->
                val t = zustand.erschienen[id] ?: return@zeichne 1f
                val x = ((jetzt - t) / 380f).coerceIn(0f, 1f)
                // kleiner Überschwinger: wirkt lebendig
                if (x >= 1f) 1f else (1f - (1f - x) * (1f - x)) * (1f + 0.15f * kotlin.math.sin(x * Math.PI).toFloat())
            }
            malen?.let { zeichner.linie(n, it) }
            n.restore()
        }
    }
}

// ---------------------------------------------------------------- Gesten

private fun oberstesStueck(teile: List<Teil>, p: Punkt, belegt: Set<Long>): Teil? =
    teile.lastOrNull { Geometrie.istStueck(it) && it.id !in belegt && Geometrie.trifft(it, p.x, p.y) }

private fun vorratFlaeche(seite: Seite) = if (seite.teile.any { it is Stellentafel }) Zeichner.VORRAT_GROSS else Zeichner.VORRAT_KLEIN

private fun beginne(z: TafelZustand, p: Punkt, belegt: Set<Long>): Zug? {
    val seite = z.seite
    // Blitzblick: Antippen deckt auf
    if (z.vorhang) { z.vorhang = false; return null }
    if (z.werkzeug == Werkzeug.STIFT) return Zug(Zug.Art.MALEN, start = p, vorher = seite).also { it.punkte.add(p) }

    oberstesStueck(seite.teile, p, belegt)?.let { t ->
        return Zug(Zug.Art.ZIEHEN, t.id, p.x - t.x, p.y - t.y, p, seite)
    }
    // Aus dem Vorrat ein neues Plättchen bzw. Kind nehmen (nicht während eines Ablaufs)
    if (z.ablauf == null) {
        val neu: Teil? = when {
            Zeichner.VORRAT_PLAETTCHEN.contains(p.x, p.y) -> Plaettchen(z.neueId(), p.x, p.y, rot = true)
            Zeichner.VORRAT_FIGUR.contains(p.x, p.y) -> Figur(z.neueId(), p.x, p.y, (0..31).random())
            seite.teile.any { it is Stellentafel } && Zeichner.VORRAT_STANGE.contains(p.x, p.y) -> Stange(z.neueId(), p.x, p.y)
            seite.teile.any { it is Stellentafel } && Zeichner.VORRAT_WUERFEL.contains(p.x, p.y) -> Wuerfel(z.neueId(), p.x, p.y)
            else -> null
        }
        if (neu != null) {
            z.aendereTeile(merken = false) { it + neu }
            return Zug(Zug.Art.ZIEHEN, neu.id, 0f, 0f, p, seite, neu = true)
        }
    }
    // Rechenrahmen: Kugel antippen schiebt
    for (t in seite.teile.asReversed()) if (t is Rahmen) {
        Geometrie.kugelBei(t, p.x, p.y)?.let { k -> return Zug(Zug.Art.KUGEL, t.id, start = p, vorher = seite, kugel = k) }
    }
    // Zahlenstrahl: Marke ziehen oder setzen
    for (t in seite.teile.asReversed()) if (t is Strahl) {
        val y = t.y + Geometrie.STRAHL_Y
        if (abs(p.y - y) < 70f && p.x >= t.x && p.x <= t.x + Geometrie.STRAHL_BREITE + 80f) {
            val alt = t.marke?.let { Geometrie.strahlX(t, it) }
            if (alt != null && abs(p.x - alt) < 34f || z.kindermodus || alt == null) {
                return Zug(Zug.Art.MARKE, t.id, start = p, vorher = seite).also { bewege(z, it, p) }
            }
        }
    }
    // Behälter und Hilfsmittel: die Lehrkraft verschiebt sie, Kinder dürfen nur antippen (Zahl eintragen, bündeln)
    seite.teile.lastOrNull { !Geometrie.istStueck(it) && Geometrie.trifft(it, p.x, p.y) && it.id !in belegt }?.let { t ->
        val mit = if (Geometrie.istBehaelter(t) || t is Stellentafel) z.inhaltVon(t).associate { it.id to Punkt(it.x - t.x, it.y - t.y) } else emptyMap()
        return Zug(Zug.Art.ZIEHEN, t.id, p.x - t.x, p.y - t.y, p, seite, mit = mit, nurTippen = z.kindermodus)
    }
    return null
}

private fun bewege(z: TafelZustand, zug: Zug, p: Punkt) {
    if (!zug.bewegt && hypot(p.x - zug.start.x, p.y - zug.start.y) > 12f) zug.bewegt = true
    when (zug.art) {
        Zug.Art.ZIEHEN -> if ((zug.bewegt || zug.neu) && !zug.nurTippen) {
            val nx = p.x - zug.dx
            val ny = p.y - zug.dy
            z.aendereTeile(merken = false) { liste ->
                liste.map { t ->
                    when {
                        t.id == zug.id -> t.verschoben(nx, ny)
                        t.id in zug.mit -> zug.mit.getValue(t.id).let { v -> t.verschoben(nx + v.x, ny + v.y) }
                        else -> t
                    }
                }
            }
        }
        Zug.Art.MARKE -> z.aendereTeile(merken = false) { liste ->
            liste.map { if (it.id == zug.id && it is Strahl) it.copy(marke = Geometrie.strahlZahl(it, p.x)) else it }
        }
        Zug.Art.MALEN -> if (zug.punkte.isEmpty() || zug.punkte.last().abstand(p) > 3f) zug.punkte.add(p)
        Zug.Art.KUGEL -> Unit
    }
}

private fun beende(z: TafelZustand, zug: Zug, p: Punkt, rufe: TafelRueckrufe) {
    when (zug.art) {
        Zug.Art.MALEN -> if (zug.punkte.isNotEmpty()) z.linieHinzufuegen(Linie(z.stiftFarbe, 7f, zug.punkte.toList()))
        Zug.Art.KUGEL -> if (!zug.bewegt) {
            val (reihe, kugel) = zug.kugel ?: return
            z.aendereTeile { liste -> liste.map { if (it.id == zug.id && it is Rahmen) Geometrie.kugelSchieben(it, reihe, kugel) else it } }
        }
        Zug.Art.MARKE -> z.setzeSeite(z.seite, merken = true, vorher = zug.vorher)
        Zug.Art.ZIEHEN -> {
            // Die letzte Position übernehmen – beim schnellen Ziehen liegt sie oft noch vor dem Ziel
            if (!zug.nurTippen && (zug.bewegt || hypot(p.x - zug.start.x, p.y - zug.start.y) > 12f)) bewege(z, zug, p)
            val teil = z.seite.teile.firstOrNull { it.id == zug.id } ?: return
            if (!zug.bewegt || zug.nurTippen) {
                antippen(z, zug, teil, p, rufe)
                return
            }
            val istStueck = Geometrie.istStueck(teil)
            when {
                // zurück in den Vorrat
                istStueck && vorratFlaeche(z.seite).contains(p.x, p.y) ->
                    z.setzeSeite(z.seite.copy(teile = z.seite.teile.filter { it.id != teil.id }), vorher = zug.vorher)
                // Papierkorb (nur Lehrkraft)
                !z.kindermodus && !zug.neu && Zeichner.PAPIERKORB.contains(p.x, p.y) ->
                    z.setzeSeite(z.seite.copy(teile = z.seite.teile.filter { it.id != teil.id }), vorher = zug.vorher)
                istStueck -> {
                    val ziel = Geometrie.einrasten(teil, z.seite.teile)
                    val neu = if (ziel != null) z.seite.teile.map { if (it.id == teil.id) it.verschoben(ziel.x, ziel.y) else it } else z.seite.teile
                    // Neu aus dem Vorrat: Rückgängig nimmt es wieder weg
                    z.setzeSeite(z.seite.copy(teile = neu), vorher = zug.vorher)
                }
                else -> z.setzeSeite(z.seite, vorher = zug.vorher)
            }
        }
    }
}

/** Antippen ohne Verschieben – je nach Teil wenden, Zahl eintragen, bündeln oder Menü öffnen. */
private fun antippen(z: TafelZustand, zug: Zug, teil: Teil, p: Punkt, rufe: TafelRueckrufe) {
    val lehrkraft = !z.kindermodus
    when {
        // aus dem Vorrat nur angetippt: nichts anlegen
        zug.neu -> z.aendereTeile(merken = false) { l -> l.filter { it.id != zug.id } }
        // Plättchen wenden (weggenommene kommen zurück)
        teil is Plaettchen -> z.aendereTeile { l -> l.map { if (it.id == teil.id) teil.copy(rot = if (teil.weg) teil.rot else !teil.rot, weg = false) else it } }
        // Zehnerstange in der Stellentafel antippen: in zehn Einer tauschen
        teil is Stange -> z.seite.teile.filterIsInstance<Stellentafel>()
            .firstOrNull { t -> LernGeometrie.stangenPlaetze(t).any { it.abstand(Punkt(teil.x, teil.y)) < 4f } }
            ?.let { z.entbuendeln(it.id, teil.id) }
        teil is Stellentafel -> {
            val k = LernGeometrie.buendelnKnopf(teil)
            if (p.x in k[0]..k[2] && p.y in k[1]..k[3] && LernGeometrie.stellen(teil, z.seite.teile).einer >= 10) z.buendeln(teil.id)
            else if (lehrkraft) rufe.menue(teil)
        }
        teil is Zahlenhaus -> LernGeometrie.hausZelleBei(teil, p.x, p.y)?.let { i ->
            val zelle = teil.zellen[i]
            if (lehrkraft || !zelle.fest) rufe.zahl(teil.id, i, zelle.wert, lehrkraft)
        } ?: run { if (lehrkraft) rufe.menue(teil) }
        teil is Zahlenmauer -> LernGeometrie.mauerSteinBei(teil, p.x, p.y)?.let { i ->
            val zelle = teil.zellen[i]
            if (lehrkraft || !zelle.fest) rufe.zahl(teil.id, i, zelle.wert, lehrkraft)
        } ?: run { if (lehrkraft) rufe.menue(teil) }
        teil is Aufgabe -> rufe.zahl(teil.id, 0, teil.eingabe, false)
        teil is Karte && lehrkraft -> rufe.karte(teil)
        lehrkraft && !Geometrie.istStueck(teil) -> rufe.menue(teil)
    }
}
