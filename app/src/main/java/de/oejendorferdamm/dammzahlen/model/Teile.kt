/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.model

import kotlin.math.abs
import kotlin.math.hypot

/*
 * Alles auf der Tafel ist ein „Teil“. Koordinaten sind Vorbild-Pixel einer 1920 × 1080 großen
 * Tafel (wie bei DammBoard) – auf jedem Bildschirm gleich groß, auf 4K einfach doppelt so fein.
 * Behälter (Felder, Bus, …) liegen mit ihrer linken oberen Ecke bei (x, y), bewegliche Teile
 * (Plättchen, Figuren) mit ihrer Mitte.
 */

/** Größe der Tafel in Vorbild-Pixeln (wie DammBoard: eine 1920 × 1080-Tafel). */
const val VORBILD_BREITE_W = 1920f
const val VORBILD_HOEHE_W = 1080f

/** Kantenlänge einer Zelle in Zehner-, Zwanziger-, Hunderterfeld und Bus. */
const val ZELLE = 64f
/** Lücke nach je fünf Zellen – die „Kraft der Fünf“. */
const val LUECKE = 16f
/** Rand um ein Feld. */
const val RAND = 12f
/** Radius eines Plättchens. */
const val PLAETTCHEN_R = 26f

data class Punkt(val x: Float, val y: Float) {
    fun abstand(o: Punkt) = hypot(x - o.x, y - o.y)
}

data class Masse(val breite: Float, val hoehe: Float)

enum class FeldArt(val bezeichnung: String, val spalten: Int, val zeilen: Int) {
    ZEHNER("Zehnerfeld", 5, 2),
    ZWANZIGER("Zwanzigerfeld", 10, 2),
    HUNDERTER("Hunderterfeld", 10, 10)
}

/** Ein Sprung am Rechenstrich, z. B. von 8 nach 10. */
data class Sprung(val von: Int, val bis: Int)

sealed class Teil {
    abstract val id: Long
    abstract val x: Float
    abstract val y: Float
    abstract fun verschoben(x: Float, y: Float): Teil
    /** Plättchen und Figuren dürfen auch Kinder bewegen; Behälter nur die Lehrkraft. */
    open val beweglichFuerKinder: Boolean get() = false
}

/** Wendeplättchen: rot oder blau. [weg] = bei Minus-Aufgaben weggenommen (nur noch Umriss). */
data class Plaettchen(override val id: Long, override val x: Float, override val y: Float, val rot: Boolean = true, val weg: Boolean = false) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
    override val beweglichFuerKinder get() = true
}

/** Schlichte Kinderfigur (für Bus und Rechengeschichten); [art] wählt Haut- und Kleidungsfarbe. */
data class Figur(override val id: Long, override val x: Float, override val y: Float, val art: Int) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
    override val beweglichFuerKinder get() = true
}

data class Feld(override val id: Long, override val x: Float, override val y: Float, val art: FeldArt) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
}

/** Stilisierter Doppeldecker-Linienbus: unten 10, oben 10 Plätze (je 5 + 5). */
data class Bus(override val id: Long, override val x: Float, override val y: Float) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
}

/** Rechenrahmen mit [reihen] Reihen à 10 Kugeln; [links] = wie viele Kugeln je Reihe links liegen. */
data class Rahmen(override val id: Long, override val x: Float, override val y: Float, val reihen: Int, val links: List<Int> = List(reihen) { 0 }) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
}

/** Zahlenstrahl 0 … [bis] mit verschiebbarer Marke (null = keine). */
data class Strahl(override val id: Long, override val x: Float, override val y: Float, val bis: Int, val marke: Int? = null) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
}

/** Rechenstrich von [start] bis [ende] mit Sprüngen (Bögen). */
data class Strich(override val id: Long, override val x: Float, override val y: Float, val start: Int, val ende: Int, val spruenge: List<Sprung> = emptyList()) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
}

/** Karte mit Aufgabe oder Text, z. B. „8 + 5 = ?“. */
data class Karte(override val id: Long, override val x: Float, override val y: Float, val text: String) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
}

// ---------------------------------------------------------------- Geometrie

object Geometrie {
    const val STRAHL_BREITE = 1500f
    const val STRICH_BREITE = 1300f
    const val KUGEL = 44f
    const val RAHMEN_REIHE = 52f

    /** Außenmaße eines Behälters (Plättchen und Figuren haben keine). */
    fun masse(t: Teil): Masse = when (t) {
        is Feld -> Masse(spaltenBreite(t.art.spalten) + 2 * RAND, zeilenHoehe(t.art.zeilen) + 2 * RAND)
        is Bus -> Masse(BUS_BREITE, BUS_HOEHE)
        is Rahmen -> Masse(10 * KUGEL + LUECKE + 2 * 110f + 40f, t.reihen * RAHMEN_REIHE + 40f)
        is Strahl -> Masse(STRAHL_BREITE + 80f, 150f)
        is Strich -> Masse(STRICH_BREITE + 80f, 230f)
        is Karte -> Masse(maxOf(220f, t.text.length * 34f + 60f), 100f)
        is Plaettchen -> Masse(2 * PLAETTCHEN_R, 2 * PLAETTCHEN_R)
        is Figur -> Masse(FIGUR_B, FIGUR_H)
    }

    const val FIGUR_B = 50f
    const val FIGUR_H = 60f

    private fun spaltenBreite(n: Int) = n * ZELLE + (if (n > 5) LUECKE else 0f)
    private fun zeilenHoehe(n: Int) = n * ZELLE + (if (n > 5) LUECKE else 0f)

    // Bus: Fensterband oben und unten, je 10 Plätze, davor Fahrerkabine
    const val BUS_BREITE = 10 * ZELLE + LUECKE + 260f
    const val BUS_HOEHE = 2 * ZELLE + 230f
    private const val BUS_SITZE_X = 190f
    const val BUS_OBEN_Y = 60f
    const val BUS_UNTEN_Y = 60f + ZELLE + 70f

    /**
     * Einrastplätze eines Behälters in Lese-Reihenfolge (erst die obere Reihe von links nach
     * rechts – so wird im Zwanzigerfeld erst der erste Zehner gefüllt). Beim Bus kommen die
     * unteren Plätze zuerst („unten 1 … 10, oben 11 … 20“).
     */
    fun plaetze(t: Teil): List<Punkt> = when (t) {
        is Feld -> buildList {
            for (z in 0 until t.art.zeilen) for (s in 0 until t.art.spalten) {
                val px = t.x + RAND + s * ZELLE + (if (s >= 5) LUECKE else 0f) + ZELLE / 2
                val py = t.y + RAND + z * ZELLE + (if (z >= 5) LUECKE else 0f) + ZELLE / 2
                add(Punkt(px, py))
            }
        }
        is Bus -> buildList {
            for (deck in listOf(BUS_UNTEN_Y, BUS_OBEN_Y)) for (s in 0 until 10) {
                add(Punkt(t.x + BUS_SITZE_X + s * ZELLE + (if (s >= 5) LUECKE else 0f) + ZELLE / 2, t.y + deck + ZELLE / 2))
            }
        }
        else -> emptyList()
    }

    fun istBehaelter(t: Teil) = t is Feld || t is Bus

    /** Liegt (px, py) auf dem Teil? Bewegliche Teile über ihren Radius, Behälter über ihr Rechteck. */
    fun trifft(t: Teil, px: Float, py: Float): Boolean = when (t) {
        is Plaettchen -> hypot(px - t.x, py - t.y) <= PLAETTCHEN_R + 8f
        is Figur -> abs(px - t.x) <= FIGUR_B / 2 + 6f && abs(py - t.y) <= FIGUR_H / 2 + 6f
        else -> masse(t).let { m -> px >= t.x && py >= t.y && px <= t.x + m.breite && py <= t.y + m.hoehe }
    }

    /** Nächster freier Platz in einem Behälter (innerhalb von 0,6 Zellen), sonst null. */
    fun einrasten(stueck: Teil, alle: List<Teil>): Punkt? {
        val belegt = alle.filter { (it is Plaettchen || it is Figur) && it.id != stueck.id }.map { Punkt(it.x, it.y) }
        var bester: Punkt? = null
        var abstand = ZELLE * 0.6f
        val p = Punkt(stueck.x, stueck.y)
        for (b in alle) if (istBehaelter(b)) for (platz in plaetze(b)) {
            val d = platz.abstand(p)
            if (d < abstand && belegt.none { it.abstand(platz) < 4f }) {
                abstand = d
                bester = platz
            }
        }
        return bester
    }

    /** Inhalt eines Behälters: rote, blaue Plättchen und Figuren (weggenommene zählen nicht). */
    data class Inhalt(val rot: Int, val blau: Int, val figuren: Int, val unten: Int = 0, val oben: Int = 0) {
        val gesamt get() = rot + blau + figuren
    }

    fun inhalt(b: Teil, alle: List<Teil>): Inhalt {
        val plaetze = plaetze(b)
        var rot = 0
        var blau = 0
        var figuren = 0
        var unten = 0
        var oben = 0
        for (t in alle) {
            val i = when (t) {
                is Plaettchen -> if (t.weg) -1 else plaetze.indexOfFirst { it.abstand(Punkt(t.x, t.y)) < 4f }
                is Figur -> plaetze.indexOfFirst { it.abstand(Punkt(t.x, t.y)) < 4f }
                else -> -1
            }
            if (i < 0) continue
            when (t) {
                is Plaettchen -> if (t.rot) rot++ else blau++
                else -> figuren++
            }
            if (b is Bus) { if (i < 10) unten++ else oben++ }
        }
        return Inhalt(rot, blau, figuren, unten, oben)
    }

    /** Beschriftung unter einem Behälter, z. B. „8 + 5 = 13“ oder „unten 7 · oben 4 · zusammen 11“. */
    fun term(b: Teil, alle: List<Teil>): String? {
        val inh = inhalt(b, alle)
        if (inh.gesamt == 0) return null
        if (b is Bus) return "unten ${inh.unten} · oben ${inh.oben} · zusammen ${inh.unten + inh.oben}"
        return when {
            inh.rot > 0 && inh.blau > 0 -> "${inh.rot} + ${inh.blau} = ${inh.rot + inh.blau}"
            else -> "${inh.gesamt}"
        }
    }

    // ------------------------------------------------ Zahlenstrahl und Rechenstrich

    /** x-Position der Zahl [n] auf einem Zahlenstrahl. */
    fun strahlX(s: Strahl, n: Int) = s.x + 40f + STRAHL_BREITE * n / s.bis
    const val STRAHL_Y = 90f

    /** Nächste ganze Zahl zu einer x-Position am Zahlenstrahl. */
    fun strahlZahl(s: Strahl, px: Float): Int = Math.round((px - s.x - 40f) / STRAHL_BREITE * s.bis).coerceIn(0, s.bis)

    fun strichX(s: Strich, n: Int) = s.x + 40f + STRICH_BREITE * (n - s.start) / (s.ende - s.start).coerceAtLeast(1)
    const val STRICH_Y = 150f

    // ------------------------------------------------ Rechenrahmen

    /** Mitte der Kugel [k] (0–9) in Reihe [r]: links geschobene liegen links, die übrigen rechts. */
    fun kugel(rahmen: Rahmen, r: Int, k: Int): Punkt {
        val links = rahmen.links.getOrElse(r) { 0 }
        val stange = rahmen.x + 20f
        val breite = masse(rahmen).breite - 40f
        val y = rahmen.y + 20f + r * RAHMEN_REIHE + RAHMEN_REIHE / 2
        val luecke = if (k >= 5) LUECKE else 0f
        val x = if (k < links) stange + 10f + k * KUGEL + luecke + KUGEL / 2
        else stange + breite - 10f - (10 - k) * KUGEL + (if (k < 5) -LUECKE else 0f) + KUGEL / 2
        return Punkt(x, y)
    }

    /** Antippen einer Kugel: sie und alle links von ihr kommen nach links – oder umgekehrt. */
    fun kugelSchieben(rahmen: Rahmen, r: Int, k: Int): Rahmen {
        val links = rahmen.links.getOrElse(r) { 0 }
        val neu = if (k < links) k else k + 1
        return rahmen.copy(links = rahmen.links.mapIndexed { i, v -> if (i == r) neu else v })
    }

    /** Welche Kugel liegt bei (px, py)? (Reihe, Kugel) oder null. */
    fun kugelBei(rahmen: Rahmen, px: Float, py: Float): Pair<Int, Int>? {
        for (r in 0 until rahmen.reihen) for (k in 0 until 10) {
            val p = kugel(rahmen, r, k)
            if (hypot(px - p.x, py - p.y) <= KUGEL / 2 + 4f) return r to k
        }
        return null
    }

    /** Wie viele Kugeln liegen insgesamt links (die gezählten)? */
    fun rahmenZahl(r: Rahmen) = r.links.sum()
}
