/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import de.oejendorferdamm.dammzahlen.model.Bus
import de.oejendorferdamm.dammzahlen.model.Feld
import de.oejendorferdamm.dammzahlen.model.Figur
import de.oejendorferdamm.dammzahlen.model.Geometrie
import de.oejendorferdamm.dammzahlen.model.Karte
import de.oejendorferdamm.dammzahlen.model.LUECKE
import de.oejendorferdamm.dammzahlen.model.Linie
import de.oejendorferdamm.dammzahlen.model.PLAETTCHEN_R
import de.oejendorferdamm.dammzahlen.model.RAND
import de.oejendorferdamm.dammzahlen.model.Plaettchen
import de.oejendorferdamm.dammzahlen.model.Rahmen
import de.oejendorferdamm.dammzahlen.model.Seite
import de.oejendorferdamm.dammzahlen.model.Strahl
import de.oejendorferdamm.dammzahlen.model.Strich
import de.oejendorferdamm.dammzahlen.model.Teil
import de.oejendorferdamm.dammzahlen.model.ZELLE
import de.oejendorferdamm.dammzahlen.model.Aufgabe
import de.oejendorferdamm.dammzahlen.model.LernGeometrie
import de.oejendorferdamm.dammzahlen.model.Stange
import de.oejendorferdamm.dammzahlen.model.Stellentafel
import de.oejendorferdamm.dammzahlen.model.Wuerfel
import de.oejendorferdamm.dammzahlen.model.Zahlenhaus
import de.oejendorferdamm.dammzahlen.model.Zahlenmauer
import de.oejendorferdamm.dammzahlen.model.Zelle

/**
 * Zeichnet eine Tafelseite mit android.graphics (schnell, auch mit vielen Teilen). Alle Angaben
 * in Vorbild-Pixeln – der Aufrufer skaliert die Leinwand vorher passend.
 */
class Zeichner(private val schrift: Typeface, private val schriftFett: Typeface) {
    private val f = Paint(Paint.ANTI_ALIAS_FLAG)
    private val l = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val pfad = Path()

    companion object {
        val ROT = Color.rgb(0xE5, 0x39, 0x35)
        val BLAU = Color.rgb(0x1E, 0x6F, 0xD9)
        /** Hauttöne und Kleidungsfarben der Figuren – bunt und ohne echte Personen. */
        val HAUT = intArrayOf(Color.rgb(0xF6, 0xD3, 0xB3), Color.rgb(0xE0, 0xAC, 0x82), Color.rgb(0xB9, 0x7A, 0x52), Color.rgb(0x7D, 0x4E, 0x33))
        val KLEIDUNG = intArrayOf(
            Color.rgb(0xF2, 0xB7, 0x05), Color.rgb(0x2E, 0xA0, 0x43), Color.rgb(0x7B, 0x3F, 0xE4), Color.rgb(0xF0, 0x6A, 0x1D),
            Color.rgb(0x0B, 0x9A, 0x8D), Color.rgb(0xE0, 0x40, 0x8F), Color.rgb(0x1E, 0x6F, 0xD9), Color.rgb(0x8B, 0x5A, 0x2B)
        )
        /** Vorratsablage links: hier liegen neue Plättchen und Figuren, hierher legt man sie zurück. */
        val VORRAT_PLAETTCHEN = RectF(14f, 170f, 118f, 320f)
        val VORRAT_FIGUR = RectF(14f, 330f, 118f, 480f)
        val VORRAT_STANGE = RectF(14f, 490f, 118f, 710f)
        val VORRAT_WUERFEL = RectF(14f, 720f, 118f, 850f)
        /** Vorrat (ohne bzw. mit Stellenwert-Material). */
        val VORRAT_KLEIN = RectF(14f, 160f, 118f, 490f)
        val VORRAT_GROSS = RectF(14f, 160f, 118f, 860f)
        val GRUEN_OK = Color.rgb(0x2E, 0xA0, 0x43)
        val ROT_FALSCH = Color.rgb(0xE0, 0x40, 0x2E)
        /** Papierkorb oben in der Mitte (nur Lehrkraft, nur während des Ziehens). */
        val PAPIERKORB = RectF(860f, 10f, 1060f, 110f)
    }

    /**
     * @param terme Rechnung unter Feldern und Bus zeigen
     * @param vorhang Blitzblick: Behälter verdeckt
     * @param groesse Einblend-Animation je Teil (1 = fertig)
     */
    fun zeichne(
        c: Canvas, seite: Seite, terme: Boolean, vorhang: Boolean, mitVorrat: Boolean, papierkorb: Boolean,
        marken: Map<Long, List<Boolean?>> = emptyMap(), groesse: (Long) -> Float
    ) {
        if (mitVorrat) vorrat(c, seite.teile.any { it is Stellentafel })
        if (papierkorb) papierkorb(c)
        // erst die Behälter und Hilfsmittel, dann die Zeichnung, dann die beweglichen Teile obenauf
        for (teil in seite.teile) when (teil) {
            is Feld -> feld(c, teil)
            is Bus -> bus(c, teil)
            is Rahmen -> rahmen(c, teil, zahl = !vorhang)
            is Strahl -> strahl(c, teil)
            is Strich -> strich(c, teil)
            is Karte -> karte(c, teil)
            is Stellentafel -> stellentafel(c, teil, seite.teile, terme)
            is Zahlenhaus -> zahlenhaus(c, teil, marken[teil.id])
            is Zahlenmauer -> zahlenmauer(c, teil, marken[teil.id])
            is Aufgabe -> aufgabe(c, teil, marken[teil.id]?.firstOrNull())
            else -> Unit
        }
        for (linie in seite.linien) linie(c, linie)
        for (teil in seite.teile) when (teil) {
            is Plaettchen -> plaettchen(c, teil.x, teil.y, teil.rot, teil.weg, groesse(teil.id))
            is Figur -> figur(c, teil.x, teil.y, teil.art, groesse(teil.id))
            is Stange -> stange(c, teil.x, teil.y, groesse(teil.id))
            is Wuerfel -> wuerfel(c, teil.x, teil.y, groesse(teil.id))
            else -> Unit
        }
        if (terme) for (teil in seite.teile) if (Geometrie.istBehaelter(teil)) Geometrie.term(teil, seite.teile)?.let { term(c, teil, it) }
        if (vorhang) for (teil in seite.teile) if (Geometrie.istBehaelter(teil) || teil is Rahmen) vorhang(c, teil)
    }

    // ------------------------------------------------------------ Behälter

    fun feld(c: Canvas, feld: Feld) {
        val m = Geometrie.masse(feld)
        r.set(feld.x, feld.y, feld.x + m.breite, feld.y + m.hoehe)
        schatten(c, r, 14f)
        f.shader = null
        f.color = Color.WHITE
        c.drawRoundRect(r, 14f, 14f, f)
        // Mulden für die Plättchen
        f.color = Color.rgb(0xE6, 0xE9, 0xED)
        for (p in Geometrie.plaetze(feld)) c.drawCircle(p.x, p.y, PLAETTCHEN_R + 3f, f)
        // Fünfer- und Zehnertrennung kräftiger, Zellen fein
        l.color = Color.rgb(0xC5, 0xCB, 0xD3)
        l.strokeWidth = 2f
        l.pathEffect = null
        val sp = feld.art.spalten
        val ze = feld.art.zeilen
        for (s in 1 until sp) if (s != 5) {
            val x = feld.x + RAND + s * ZELLE + (if (s > 5) LUECKE else 0f)
            c.drawLine(x, feld.y + RAND, x, feld.y + m.hoehe - RAND, l)
        }
        for (z in 1 until ze) if (z != 5) {
            val y = feld.y + RAND + z * ZELLE + (if (z > 5) LUECKE else 0f)
            c.drawLine(feld.x + RAND, y, feld.x + m.breite - RAND, y, l)
        }
        l.color = Color.rgb(0x5B, 0x63, 0x6E)
        l.strokeWidth = 5f
        if (sp > 5) { val x = feld.x + RAND + 5 * ZELLE + LUECKE / 2; c.drawLine(x, feld.y + 6f, x, feld.y + m.hoehe - 6f, l) }
        if (ze > 5) { val y = feld.y + RAND + 5 * ZELLE + LUECKE / 2; c.drawLine(feld.x + 6f, y, feld.x + m.breite - 6f, y, l) }
        l.strokeWidth = 3f
        l.color = Color.rgb(0x9A, 0xA3, 0xAE)
        c.drawRoundRect(r, 14f, 14f, l)
    }

    fun bus(c: Canvas, bus: Bus) {
        val b = Geometrie.BUS_BREITE
        val h = Geometrie.BUS_HOEHE
        val x = bus.x
        val y = bus.y
        // Karosserie (Hamburger Stadtbus-Rot), vorne rechts abgerundet
        r.set(x, y + 10f, x + b, y + h - 40f)
        schatten(c, r, 30f)
        f.color = Color.BLACK
        f.shader = LinearGradient(0f, y, 0f, y + h, Color.rgb(0xE8, 0x2A, 0x2A), Color.rgb(0xB8, 0x0F, 0x1A), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 30f, 30f, f)
        f.shader = null
        // weißer Zierstreifen
        f.color = Color.WHITE
        c.drawRect(x + 14f, y + Geometrie.BUS_UNTEN_Y - 22f, x + b - 14f, y + Geometrie.BUS_UNTEN_Y - 10f, f)
        // Fensterbänder oben und unten mit Sitzplätzen
        for (deck in floatArrayOf(Geometrie.BUS_OBEN_Y, Geometrie.BUS_UNTEN_Y)) {
            r.set(x + 180f, y + deck - 8f, x + 190f + 10 * ZELLE + LUECKE + 10f, y + deck + ZELLE + 8f)
            f.color = Color.rgb(0xD6, 0xEC, 0xF8)
            c.drawRoundRect(r, 14f, 14f, f)
            // Pfosten zwischen den Fünfergruppen
            f.color = Color.rgb(0xB8, 0x0F, 0x1A)
            c.drawRect(x + 190f + 5 * ZELLE, y + deck - 8f, x + 190f + 5 * ZELLE + LUECKE, y + deck + ZELLE + 8f, f)
        }
        // Sitze (Lehnen) als Hinweis, wo Kinder Platz nehmen
        f.color = Color.rgb(0x2D, 0x5F, 0x9E)
        for (p in Geometrie.plaetze(bus)) {
            r.set(p.x - 20f, p.y - 2f, p.x + 20f, p.y + 26f)
            c.drawRoundRect(r, 6f, 6f, f)
        }
        // Fahrerkabine vorne links mit großer Scheibe und Zielanzeige
        f.color = Color.rgb(0xD6, 0xEC, 0xF8)
        r.set(x + 24f, y + Geometrie.BUS_OBEN_Y - 8f, x + 160f, y + Geometrie.BUS_OBEN_Y + ZELLE + 8f)
        c.drawRoundRect(r, 18f, 18f, f)
        r.set(x + 24f, y + Geometrie.BUS_UNTEN_Y - 8f, x + 160f, y + Geometrie.BUS_UNTEN_Y + ZELLE + 30f)
        c.drawRoundRect(r, 18f, 18f, f)
        f.color = Color.rgb(0x22, 0x22, 0x22)
        r.set(x + 30f, y + 18f, x + 160f, y + 50f)
        c.drawRoundRect(r, 6f, 6f, f)
        t.typeface = schriftFett
        t.textSize = 26f
        t.color = Color.rgb(0xFF, 0xB3, 0x00)
        c.drawText("Schule", x + 95f, y + 43f, t)
        // Tür (zwischen Kabine und Sitzen)
        f.color = Color.rgb(0x9E, 0xC9, 0xE6)
        r.set(x + 166f, y + Geometrie.BUS_UNTEN_Y - 8f, x + 182f, y + h - 48f)
        c.drawRect(r, f)
        // Räder
        for (rx in floatArrayOf(x + 120f, x + b - 140f)) {
            f.color = Color.rgb(0x22, 0x22, 0x22)
            c.drawCircle(rx, y + h - 40f, 38f, f)
            f.color = Color.rgb(0xB0, 0xB6, 0xBD)
            c.drawCircle(rx, y + h - 40f, 16f, f)
        }
        // Scheinwerfer
        f.color = Color.rgb(0xFF, 0xE0, 0x82)
        c.drawCircle(x + 14f, y + h - 80f, 9f, f)
    }

    fun rahmen(c: Canvas, rahmen: Rahmen, zahl: Boolean = true) {
        val m = Geometrie.masse(rahmen)
        r.set(rahmen.x, rahmen.y, rahmen.x + m.breite, rahmen.y + m.hoehe)
        schatten(c, r, 12f)
        f.color = Color.BLACK
        f.shader = LinearGradient(rahmen.x, 0f, rahmen.x + m.breite, 0f, Color.rgb(0xB0, 0x7A, 0x4C), Color.rgb(0x8A, 0x58, 0x30), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 12f, 12f, f)
        f.shader = null
        f.color = Color.rgb(0xF7, 0xF3, 0xEA)
        r.set(rahmen.x + 14f, rahmen.y + 14f, rahmen.x + m.breite - 14f, rahmen.y + m.hoehe - 14f)
        c.drawRoundRect(r, 8f, 8f, f)
        for (reihe in 0 until rahmen.reihen) {
            val y = Geometrie.kugel(rahmen, reihe, 0).y
            l.color = Color.rgb(0x7A, 0x7F, 0x86)
            l.strokeWidth = 4f
            l.pathEffect = null
            c.drawLine(rahmen.x + 14f, y, rahmen.x + m.breite - 14f, y, l)
            for (k in 0 until 10) {
                val p = Geometrie.kugel(rahmen, reihe, k)
                val farbe = if (k < 5) ROT else BLAU
                f.color = Color.BLACK
                f.shader = RadialGradient(p.x - 6f, p.y - 8f, Geometrie.KUGEL / 2, mische(farbe, Color.WHITE, 0.35f), farbe, Shader.TileMode.CLAMP)
                c.drawCircle(p.x, p.y, Geometrie.KUGEL / 2 - 1f, f)
                f.shader = null
            }
        }
        // Zahl der links liegenden Kugeln (nicht beim Blitzblick – sonst wäre die Lösung zu sehen)
        if (!zahl) return
        val zahl = Geometrie.rahmenZahl(rahmen)
        t.typeface = schriftFett
        t.textSize = 40f
        t.color = Color.WHITE
        c.drawText("$zahl", rahmen.x + m.breite / 2, rahmen.y - 14f, t)
    }

    fun strahl(c: Canvas, s: Strahl) {
        val y = s.y + Geometrie.STRAHL_Y
        l.color = Color.WHITE
        l.strokeWidth = 5f
        l.pathEffect = null
        c.drawLine(s.x + 20f, y, s.x + Geometrie.STRAHL_BREITE + 70f, y, l)
        // Pfeilspitze
        c.drawLine(s.x + Geometrie.STRAHL_BREITE + 70f, y, s.x + Geometrie.STRAHL_BREITE + 52f, y - 12f, l)
        c.drawLine(s.x + Geometrie.STRAHL_BREITE + 70f, y, s.x + Geometrie.STRAHL_BREITE + 52f, y + 12f, l)
        t.typeface = schrift
        t.color = Color.WHITE
        for (n in 0..s.bis) {
            val x = Geometrie.strahlX(s, n)
            val zehner = n % 10 == 0
            val fuenfer = n % 5 == 0
            val lang = if (zehner) 26f else if (fuenfer) 18f else 10f
            l.strokeWidth = if (zehner) 4f else 2.5f
            c.drawLine(x, y - lang, x, y + lang, l)
            val beschriften = if (s.bis <= 20) true else zehner || (s.bis <= 50 && fuenfer)
            if (beschriften) {
                t.textSize = if (zehner) 36f else 28f
                c.drawText("$n", x, y + lang + 34f, t)
            }
        }
        s.marke?.let { n ->
            val x = Geometrie.strahlX(s, n)
            pfad.reset()
            pfad.moveTo(x, y - 8f); pfad.lineTo(x - 18f, y - 40f); pfad.lineTo(x + 18f, y - 40f); pfad.close()
            f.color = Color.rgb(0xFF, 0xC1, 0x07)
            c.drawPath(pfad, f)
            t.typeface = schriftFett
            t.textSize = 40f
            t.color = Color.rgb(0xFF, 0xC1, 0x07)
            c.drawText("$n", x, y - 52f, t)
        }
    }

    fun strich(c: Canvas, s: Strich) {
        val y = s.y + Geometrie.STRICH_Y
        l.color = Color.WHITE
        l.strokeWidth = 5f
        l.pathEffect = null
        c.drawLine(s.x + 20f, y, s.x + s.breite + 60f, y, l)
        t.typeface = schrift
        t.color = Color.WHITE
        t.textSize = 34f
        val marken = (listOf(s.start, s.ende) + s.spruenge.flatMap { listOf(it.von, it.bis) }).toSet()
        for (n in marken) {
            val x = Geometrie.strichX(s, n)
            l.strokeWidth = 4f
            c.drawLine(x, y - 14f, x, y + 14f, l)
            c.drawText("$n", x, y + 52f, t)
        }
        // Sprünge als Bögen mit Beschriftung
        for ((i, sp) in s.spruenge.withIndex()) {
            val x1 = Geometrie.strichX(s, sp.von)
            val x2 = Geometrie.strichX(s, sp.bis)
            val hoehe = minOf(130f, 40f + kotlin.math.abs(x2 - x1) * 0.35f)
            val farbe = if (i % 2 == 0) Color.rgb(0xFF, 0xC1, 0x07) else Color.rgb(0x8E, 0xE0, 0xFF)
            l.color = farbe
            l.strokeWidth = 5f
            pfad.reset()
            pfad.moveTo(x1, y - 6f)
            pfad.cubicTo(x1, y - hoehe, x2, y - hoehe, x2, y - 6f)
            c.drawPath(pfad, l)
            // Pfeilspitze am Ziel
            val richtung = if (x2 >= x1) -1f else 1f
            c.drawLine(x2, y - 6f, x2 + richtung * 14f, y - 22f, l)
            c.drawLine(x2, y - 6f, x2 - richtung * 4f, y - 26f, l)
            t.typeface = schriftFett
            t.textSize = 36f
            t.color = farbe
            val diff = sp.bis - sp.von
            c.drawText(if (diff >= 0) "+$diff" else "−${-diff}", (x1 + x2) / 2, y - hoehe * 0.75f - 10f, t)
        }
    }

    fun karte(c: Canvas, k: Karte) {
        val m = Geometrie.masse(k)
        r.set(k.x, k.y, k.x + m.breite, k.y + m.hoehe)
        schatten(c, r, 16f)
        f.shader = null
        f.color = Color.WHITE
        c.drawRoundRect(r, 16f, 16f, f)
        t.typeface = schrift
        t.textSize = 50f
        t.color = Color.rgb(0x22, 0x22, 0x22)
        c.drawText(k.text, k.x + m.breite / 2, k.y + m.hoehe / 2 + 18f, t)
    }

    // ------------------------------------------------------------ bewegliche Teile

    fun plaettchen(c: Canvas, x: Float, y: Float, rot: Boolean, weg: Boolean, groesse: Float = 1f) {
        val rad = PLAETTCHEN_R * groesse
        if (rad <= 0.5f) return
        if (weg) {
            // weggenommen: nur gestrichelter Umriss
            l.color = Color.rgb(0x9A, 0xA3, 0xAE)
            l.strokeWidth = 3f
            l.pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
            c.drawCircle(x, y, rad - 2f, l)
            l.pathEffect = null
            c.drawLine(x - rad * 0.6f, y + rad * 0.6f, x + rad * 0.6f, y - rad * 0.6f, l)
            return
        }
        val farbe = if (rot) ROT else BLAU
        f.color = Color.argb(60, 0, 0, 0)
        c.drawCircle(x + 2f, y + 3f, rad, f)
        f.color = Color.BLACK
        f.shader = RadialGradient(x - rad * 0.3f, y - rad * 0.35f, rad * 1.2f, mische(farbe, Color.WHITE, 0.3f), farbe, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, f)
        f.shader = null
        // Blau zusätzlich mit hellem Ring – unterscheidbar auch bei Farbschwäche
        if (!rot) {
            l.color = Color.argb(200, 255, 255, 255)
            l.strokeWidth = 4f
            c.drawCircle(x, y, rad * 0.55f, l)
        }
    }

    fun figur(c: Canvas, x: Float, y: Float, art: Int, groesse: Float = 1f) {
        if (groesse <= 0.02f) return
        c.save()
        c.scale(groesse, groesse, x, y)
        val haut = HAUT[Math.floorMod(art, HAUT.size)]
        val kleid = KLEIDUNG[Math.floorMod(art / HAUT.size + art, KLEIDUNG.size)]
        // Körper
        f.color = kleid
        r.set(x - 22f, y - 2f, x + 22f, y + 30f)
        c.drawRoundRect(r, 16f, 16f, f)
        // Kopf
        f.color = haut
        c.drawCircle(x, y - 14f, 15f, f)
        // Haare (einfacher Bogen)
        f.color = mische(haut, Color.BLACK, 0.65f)
        c.drawArc(x - 15f, y - 30f, x + 15f, y - 2f, 180f, 180f, true, f)
        // Gesicht
        f.color = Color.rgb(0x22, 0x22, 0x22)
        c.drawCircle(x - 5f, y - 13f, 2.2f, f)
        c.drawCircle(x + 5f, y - 13f, 2.2f, f)
        l.color = Color.rgb(0x22, 0x22, 0x22)
        l.strokeWidth = 2f
        c.drawArc(x - 5f, y - 11f, x + 5f, y - 4f, 20f, 140f, false, l)
        c.restore()
    }

    // ------------------------------------------------------------ Hilfen

    fun linie(c: Canvas, linie: Linie) {
        if (linie.punkte.isEmpty()) return
        l.color = linie.farbe
        l.strokeWidth = linie.breite
        l.pathEffect = null
        pfad.reset()
        pfad.moveTo(linie.punkte[0].x, linie.punkte[0].y)
        if (linie.punkte.size == 1) pfad.lineTo(linie.punkte[0].x + 0.1f, linie.punkte[0].y)
        for (i in 1 until linie.punkte.size) pfad.lineTo(linie.punkte[i].x, linie.punkte[i].y)
        c.drawPath(pfad, l)
    }

    private fun term(c: Canvas, b: Teil, text: String) {
        val m = Geometrie.masse(b)
        t.typeface = schriftFett
        t.textSize = 44f
        val breite = t.measureText(text) + 40f
        val mx = b.x + m.breite / 2
        val y = b.y + m.hoehe + 16f
        r.set(mx - breite / 2, y, mx + breite / 2, y + 62f)
        f.shader = null
        f.color = Color.argb(150, 0, 0, 0)
        c.drawRoundRect(r, 31f, 31f, f)
        t.color = Color.WHITE
        c.drawText(text, mx, y + 46f, t)
    }

    private fun vorhang(c: Canvas, b: Teil) {
        val m = Geometrie.masse(b)
        r.set(b.x - 6f, b.y - 6f, b.x + m.breite + 6f, b.y + m.hoehe + 6f)
        f.color = Color.BLACK
        f.shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.rgb(0x4A, 0x2F, 0x6E), Color.rgb(0x2E, 0x1B, 0x48), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18f, 18f, f)
        f.shader = null
        t.typeface = schriftFett
        t.textSize = minOf(m.hoehe * 0.6f, 160f)
        t.color = Color.WHITE
        c.drawText("?", r.centerX(), r.centerY() + t.textSize * 0.35f, t)
    }

    private fun vorrat(c: Canvas, mitStellen: Boolean) {
        f.shader = null
        f.color = Color.argb(70, 0, 0, 0)
        c.drawRoundRect(if (mitStellen) VORRAT_GROSS else VORRAT_KLEIN, 22f, 22f, f)
        // Stapel Plättchen
        for (i in 2 downTo 0) plaettchen(c, VORRAT_PLAETTCHEN.centerX() + i * 4f, VORRAT_PLAETTCHEN.centerY() - 10f - i * 6f, i % 2 == 0, false)
        t.typeface = schrift
        t.textSize = 22f
        t.color = Color.WHITE
        c.drawText("Plättchen", VORRAT_PLAETTCHEN.centerX(), VORRAT_PLAETTCHEN.bottom - 10f, t)
        figur(c, VORRAT_FIGUR.centerX() - 10f, VORRAT_FIGUR.centerY() - 22f, 5)
        figur(c, VORRAT_FIGUR.centerX() + 10f, VORRAT_FIGUR.centerY() - 14f, 2)
        c.drawText("Kinder", VORRAT_FIGUR.centerX(), VORRAT_FIGUR.bottom - 10f, t)
        if (mitStellen) {
            stange(c, VORRAT_STANGE.centerX() - 8f, VORRAT_STANGE.centerY() - 22f, 0.55f)
            stange(c, VORRAT_STANGE.centerX() + 8f, VORRAT_STANGE.centerY() - 18f, 0.55f)
            c.drawText("Zehner", VORRAT_STANGE.centerX(), VORRAT_STANGE.bottom - 10f, t)
            wuerfel(c, VORRAT_WUERFEL.centerX() - 6f, VORRAT_WUERFEL.centerY() - 20f)
            wuerfel(c, VORRAT_WUERFEL.centerX() + 8f, VORRAT_WUERFEL.centerY() - 10f)
            c.drawText("Einer", VORRAT_WUERFEL.centerX(), VORRAT_WUERFEL.bottom - 10f, t)
        }
    }

    // ------------------------------------------------------------ Stellenwert

    private val HOLZ = Color.rgb(0xF2, 0xC1, 0x4E)
    private val HOLZ_DUNKEL = Color.rgb(0xC8, 0x8E, 0x1E)

    fun wuerfel(c: Canvas, x: Float, y: Float, groesse: Float = 1f) {
        val w = LernGeometrie.WUERFEL * groesse
        if (w < 1f) return
        f.shader = null
        f.color = Color.argb(60, 0, 0, 0)
        r.set(x - w / 2 + 2f, y - w / 2 + 3f, x + w / 2 + 2f, y + w / 2 + 3f)
        c.drawRoundRect(r, 5f, 5f, f)
        f.color = Color.BLACK
        f.shader = LinearGradient(x - w / 2, y - w / 2, x + w / 2, y + w / 2, mische(HOLZ, Color.WHITE, 0.25f), HOLZ_DUNKEL, Shader.TileMode.CLAMP)
        r.set(x - w / 2, y - w / 2, x + w / 2, y + w / 2)
        c.drawRoundRect(r, 5f, 5f, f)
        f.shader = null
        l.color = HOLZ_DUNKEL
        l.strokeWidth = 2f
        l.pathEffect = null
        c.drawRoundRect(r, 5f, 5f, l)
    }

    /** Zehnerstange: zehn Würfel übereinander (mit Fünfer-Markierung). */
    fun stange(c: Canvas, x: Float, y: Float, groesse: Float = 1f) {
        val b = LernGeometrie.STANGE_B * groesse
        val h = LernGeometrie.STANGE_H * groesse
        if (b < 1f) return
        f.shader = null
        f.color = Color.argb(60, 0, 0, 0)
        r.set(x - b / 2 + 3f, y - h / 2 + 4f, x + b / 2 + 3f, y + h / 2 + 4f)
        c.drawRoundRect(r, 5f, 5f, f)
        f.color = Color.BLACK
        f.shader = LinearGradient(x - b / 2, 0f, x + b / 2, 0f, mische(HOLZ, Color.WHITE, 0.25f), HOLZ_DUNKEL, Shader.TileMode.CLAMP)
        r.set(x - b / 2, y - h / 2, x + b / 2, y + h / 2)
        c.drawRoundRect(r, 5f, 5f, f)
        f.shader = null
        l.color = HOLZ_DUNKEL
        l.pathEffect = null
        for (i in 1 until 10) {
            l.strokeWidth = if (i == 5) 3.5f else 1.5f
            val yy = y - h / 2 + i * h / 10
            c.drawLine(x - b / 2, yy, x + b / 2, yy, l)
        }
        l.strokeWidth = 2f
        c.drawRoundRect(r, 5f, 5f, l)
    }

    fun stellentafel(c: Canvas, s: Stellentafel, alle: List<Teil>, mitZahl: Boolean) {
        val m = Geometrie.masse(s)
        r.set(s.x, s.y, s.x + m.breite, s.y + m.hoehe)
        schatten(c, r, 16f)
        f.shader = null
        f.color = Color.WHITE
        c.drawRoundRect(r, 16f, 16f, f)
        // Kopfzeile Z | E in den üblichen Stellenwert-Farben (Zehner blau, Einer rot)
        f.color = Color.rgb(0xD8, 0xE7, 0xFB)
        r.set(s.x + 6f, s.y + 6f, s.x + LernGeometrie.TAFEL_Z_BREITE - 3f, s.y + LernGeometrie.KOPF)
        c.drawRoundRect(r, 12f, 12f, f)
        f.color = Color.rgb(0xFB, 0xDD, 0xDB)
        r.set(s.x + LernGeometrie.TAFEL_Z_BREITE + 3f, s.y + 6f, s.x + m.breite - 6f, s.y + LernGeometrie.KOPF)
        c.drawRoundRect(r, 12f, 12f, f)
        t.typeface = schriftFett
        t.textSize = 44f
        t.color = BLAU
        c.drawText("Z", s.x + LernGeometrie.TAFEL_Z_BREITE / 2, s.y + 54f, t)
        t.color = ROT
        c.drawText("E", s.x + LernGeometrie.TAFEL_Z_BREITE + LernGeometrie.TAFEL_E_BREITE / 2, s.y + 54f, t)
        l.color = Color.rgb(0x9A, 0xA3, 0xAE)
        l.strokeWidth = 3f
        l.pathEffect = null
        c.drawLine(s.x + LernGeometrie.TAFEL_Z_BREITE, s.y + 10f, s.x + LernGeometrie.TAFEL_Z_BREITE, s.y + m.hoehe - 10f, l)
        // Plätze andeuten
        f.color = Color.rgb(0xEE, 0xF1, 0xF4)
        for (p in LernGeometrie.stangenPlaetze(s)) {
            r.set(p.x - LernGeometrie.STANGE_B / 2, p.y - LernGeometrie.STANGE_H / 2, p.x + LernGeometrie.STANGE_B / 2, p.y + LernGeometrie.STANGE_H / 2)
            c.drawRoundRect(r, 5f, 5f, f)
        }
        for (p in LernGeometrie.wuerfelPlaetze(s)) {
            r.set(p.x - LernGeometrie.WUERFEL / 2, p.y - LernGeometrie.WUERFEL / 2, p.x + LernGeometrie.WUERFEL / 2, p.y + LernGeometrie.WUERFEL / 2)
            c.drawRoundRect(r, 5f, 5f, f)
        }
        l.strokeWidth = 3f
        r.set(s.x, s.y, s.x + m.breite, s.y + m.hoehe)
        c.drawRoundRect(r, 16f, 16f, l)
        val st = LernGeometrie.stellen(s, alle)
        // „Bündeln“, sobald zehn oder mehr Einer liegen
        if (st.einer >= 10) {
            val k = LernGeometrie.buendelnKnopf(s)
            f.color = Color.rgb(0x1E, 0x6F, 0xD9)
            r.set(k[0], k[1], k[2], k[3])
            c.drawRoundRect(r, 30f, 30f, f)
            t.typeface = schriftFett
            t.textSize = 30f
            t.color = Color.WHITE
            c.drawText("10 E → 1 Z", r.centerX(), r.centerY() + 11f, t)
        }
        if (mitZahl && st.zahl > 0 || mitZahl && st.zehner + st.einer > 0) {
            val text = "${st.zehner} Z  ${st.einer} E  =  ${st.zahl}"
            t.typeface = schriftFett
            t.textSize = 44f
            val breite = t.measureText(text) + 40f
            val mx = s.x + m.breite / 2
            val y = s.y + m.hoehe + 16f
            r.set(mx - breite / 2, y, mx + breite / 2, y + 62f)
            f.color = Color.argb(150, 0, 0, 0)
            c.drawRoundRect(r, 31f, 31f, f)
            t.color = Color.WHITE
            c.drawText(text, mx, y + 46f, t)
        }
    }

    // ------------------------------------------------------------ Zahlenhaus, Zahlenmauer, Aufgaben

    private fun zahlZelle(c: Canvas, rr: FloatArray, zelle: Zelle, ok: Boolean?, fuellung: Int = Color.WHITE) {
        r.set(rr[0] + 3f, rr[1] + 3f, rr[0] + rr[2] - 3f, rr[1] + rr[3] - 3f)
        f.shader = null
        f.color = when (ok) { true -> Color.rgb(0xDC, 0xF5, 0xE1); false -> Color.rgb(0xFD, 0xE2, 0xDE); null -> fuellung }
        c.drawRoundRect(r, 10f, 10f, f)
        l.pathEffect = null
        l.color = when (ok) { true -> GRUEN_OK; false -> ROT_FALSCH; null -> Color.rgb(0x9A, 0xA3, 0xAE) }
        l.strokeWidth = if (ok != null) 5f else 2.5f
        c.drawRoundRect(r, 10f, 10f, l)
        zelle.wert?.let { w ->
            t.typeface = if (zelle.fest) schriftFett else schrift
            t.textSize = 50f
            t.color = if (zelle.fest) Color.rgb(0x22, 0x22, 0x22) else Color.rgb(0x1E, 0x5F, 0xC0)
            c.drawText("$w", r.centerX(), r.centerY() + 18f, t)
        }
    }

    fun zahlenhaus(c: Canvas, h: Zahlenhaus, marken: List<Boolean?>?) {
        val m = Geometrie.masse(h)
        // Dach
        pfad.reset()
        pfad.moveTo(h.x - 14f, h.y + LernGeometrie.DACH)
        pfad.lineTo(h.x + m.breite / 2, h.y)
        pfad.lineTo(h.x + m.breite + 14f, h.y + LernGeometrie.DACH)
        pfad.close()
        f.shader = null
        f.color = Color.argb(55, 0, 0, 0)
        c.save(); c.translate(4f, 6f); c.drawPath(pfad, f); c.restore()
        f.color = Color.rgb(0xE5, 0x5A, 0x3C)
        c.drawPath(pfad, f)
        t.typeface = schriftFett
        t.textSize = 56f
        t.color = Color.WHITE
        c.drawText("${h.dach}", h.x + m.breite / 2, h.y + LernGeometrie.DACH - 24f, t)
        // Mauerwerk
        r.set(h.x, h.y + LernGeometrie.DACH, h.x + m.breite, h.y + m.hoehe)
        f.color = Color.rgb(0xF4, 0xEF, 0xE6)
        c.drawRect(r, f)
        for (i in h.zellen.indices) zahlZelle(c, LernGeometrie.hausZelle(h, i), h.zellen[i], marken?.getOrNull(i / 2), Color.rgb(0xF4, 0xEF, 0xE6))
    }

    fun zahlenmauer(c: Canvas, m: Zahlenmauer, marken: List<Boolean?>?) {
        for (i in m.zellen.indices) zahlZelle(c, LernGeometrie.mauerStein(m, i), m.zellen[i], marken?.getOrNull(i), Color.rgb(0xF8, 0xE3, 0xC8))
    }

    fun aufgabe(c: Canvas, a: Aufgabe, ok: Boolean?) {
        val m = Geometrie.masse(a)
        r.set(a.x, a.y, a.x + m.breite, a.y + m.hoehe)
        schatten(c, r, 18f)
        f.shader = null
        f.color = Color.WHITE
        c.drawRoundRect(r, 18f, 18f, f)
        var x = a.x + 20f
        t.typeface = schrift
        for (teil in a.teile()) {
            if (teil == "□") {
                val luecke = floatArrayOf(x, a.y + 10f, 100f, LernGeometrie.AUFGABE_H - 20f)
                zahlZelle(c, luecke, Zelle(a.eingabe), ok, Color.rgb(0xF2, 0xF5, 0xF9))
                x += 110f
            } else {
                t.typeface = schrift
                t.textSize = 50f
                t.color = Color.rgb(0x22, 0x22, 0x22)
                val w = teil.length * 30f + 18f
                c.drawText(teil, x + w / 2, a.y + LernGeometrie.AUFGABE_H / 2 + 18f, t)
                x += w
            }
        }
    }

    private fun papierkorb(c: Canvas) {
        f.shader = null
        f.color = Color.argb(200, 0xC6, 0x28, 0x28)
        c.drawRoundRect(PAPIERKORB, 50f, 50f, f)
        t.typeface = schriftFett
        t.textSize = 34f
        t.color = Color.WHITE
        c.drawText("Hier weg", PAPIERKORB.centerX(), PAPIERKORB.centerY() + 12f, t)
    }

    private fun schatten(c: Canvas, rr: RectF, radius: Float) {
        f.shader = null
        f.color = Color.argb(55, 0, 0, 0)
        val s = RectF(rr.left + 4f, rr.top + 6f, rr.right + 4f, rr.bottom + 6f)
        c.drawRoundRect(s, radius, radius, f)
    }

    private fun mische(a: Int, b: Int, x: Float): Int {
        fun k(p: Int, q: Int) = (p + (q - p) * x).toInt().coerceIn(0, 255)
        return Color.rgb(k(Color.red(a), Color.red(b)), k(Color.green(a), Color.green(b)), k(Color.blue(a), Color.blue(b)))
    }
}
