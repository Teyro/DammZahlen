/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.model

import kotlin.math.abs

/*
 * Lernbausteine ab 0.2: Stellenwert (Zehnerstangen, Einerwürfel, Stellentafel), Zahlenhaus,
 * Zahlenmauer und Rechenaufgaben mit Lücke. Kinder tragen Zahlen über den Ziffernblock ein,
 * „Prüfen“ zeigt, was stimmt.
 */

/** Ein Zahlenfeld zum Eintragen; [fest] = von der Lehrkraft vorgegeben (Kinder können es nicht ändern). */
data class Zelle(val wert: Int? = null, val fest: Boolean = false)

/** Zehnerstange (Dienes-Material): zehn Würfel am Stück. */
data class Stange(override val id: Long, override val x: Float, override val y: Float) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
    override val beweglichFuerKinder get() = true
}

/** Einerwürfel. */
data class Wuerfel(override val id: Long, override val x: Float, override val y: Float) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
    override val beweglichFuerKinder get() = true
}

/** Stellentafel Zehner | Einer – Stangen und Würfel rasten ein, darunter steht die Zahl. */
data class Stellentafel(override val id: Long, override val x: Float, override val y: Float) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
}

/** Zahlenhaus: im Dach die Zahl, in jedem Stockwerk zwei Zahlen, die zusammen die Dachzahl ergeben. */
data class Zahlenhaus(override val id: Long, override val x: Float, override val y: Float, val dach: Int, val zellen: List<Zelle>) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
    val stockwerke get() = zellen.size / 2
}

/** Zahlenmauer: jeder Stein ist die Summe der zwei Steine darunter. Zellen von unten links nach oben. */
data class Zahlenmauer(override val id: Long, override val x: Float, override val y: Float, val stufen: Int, val zellen: List<Zelle>) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
}

enum class Rechenart(val zeichen: String) { PLUS("+"), MINUS("−"), DOPPELT("Doppelte"), HAELFTE("Hälfte") }

/**
 * Rechenaufgabe mit Lücke: [luecke] 0 = erste Zahl, 1 = zweite Zahl, 2 = Ergebnis.
 * Bei DOPPELT/HAELFTE ist [a] die Ausgangszahl, die Lücke immer das Ergebnis.
 */
data class Aufgabe(
    override val id: Long, override val x: Float, override val y: Float,
    val a: Int, val b: Int, val art: Rechenart, val luecke: Int = 2, val eingabe: Int? = null
) : Teil() {
    override fun verschoben(x: Float, y: Float) = copy(x = x, y = y)
    val ergebnis get() = when (art) {
        Rechenart.PLUS -> a + b
        Rechenart.MINUS -> a - b
        Rechenart.DOPPELT -> 2 * a
        Rechenart.HAELFTE -> a / 2
    }
    val loesung get() = when (luecke) { 0 -> a; 1 -> b; else -> ergebnis }

    /** Text mit Platzhalter □ für die Lücke, z. B. „8 + □ = 13“. */
    fun teile(): List<String> = when (art) {
        Rechenart.DOPPELT -> listOf("Das Doppelte von $a =", "□")
        Rechenart.HAELFTE -> listOf("Die Hälfte von $a =", "□")
        else -> listOf(if (luecke == 0) "□" else "$a", art.zeichen, if (luecke == 1) "□" else "$b", "=", if (luecke == 2) "□" else "$ergebnis")
    }
}

object LernGeometrie {
    // Stellentafel
    const val TAFEL_Z_BREITE = 330f
    const val TAFEL_E_BREITE = 260f
    const val TAFEL_HOEHE = 470f
    const val KOPF = 70f
    const val WUERFEL = 36f
    const val STANGE_B = 30f
    const val STANGE_H = 10 * 30f

    // Zahlenhaus und Zahlenmauer
    const val HAUS_B = 120f
    const val HAUS_H = 70f
    const val DACH = 110f
    const val STEIN_B = 120f
    const val STEIN_H = 70f

    const val AUFGABE_H = 96f

    fun masse(t: Teil): Masse = when (t) {
        is Stange -> Masse(STANGE_B, STANGE_H)
        is Wuerfel -> Masse(WUERFEL, WUERFEL)
        is Stellentafel -> Masse(TAFEL_Z_BREITE + TAFEL_E_BREITE, TAFEL_HOEHE)
        is Zahlenhaus -> Masse(2 * HAUS_B, DACH + t.stockwerke * HAUS_H)
        is Zahlenmauer -> Masse(t.stufen * STEIN_B, t.stufen * STEIN_H)
        is Aufgabe -> Masse(aufgabeBreite(t), AUFGABE_H)
        else -> Masse(0f, 0f)
    }

    fun aufgabeBreite(t: Aufgabe) = 40f + t.teile().sumOf { if (it == "□") 110.0 else it.length * 30.0 + 18.0 }.toFloat()

    // ------------------------------------------------ Stellentafel

    /** Plätze für Stangen in der Zehner-Spalte (9 nebeneinander). */
    fun stangenPlaetze(s: Stellentafel): List<Punkt> = List(9) { i ->
        Punkt(s.x + 22f + i * 34f + STANGE_B / 2, s.y + KOPF + 16f + STANGE_H / 2)
    }

    /** Plätze für Würfel in der Einer-Spalte: 4 × 5, mit Lücke nach je fünf (Platz für bis zu 20 vor dem Bündeln). */
    fun wuerfelPlaetze(s: Stellentafel): List<Punkt> = List(20) { i ->
        val spalte = i / 5
        val zeile = i % 5
        Punkt(s.x + TAFEL_Z_BREITE + 26f + spalte * 52f + (if (spalte >= 2) 12f else 0f) + WUERFEL / 2,
            s.y + KOPF + 16f + zeile * 56f + WUERFEL / 2)
    }

    data class Stellen(val zehner: Int, val einer: Int) { val zahl get() = 10 * zehner + einer }

    fun stellen(s: Stellentafel, alle: List<Teil>): Stellen {
        val sp = stangenPlaetze(s)
        val wp = wuerfelPlaetze(s)
        val z = alle.count { it is Stange && sp.any { p -> p.abstand(Punkt(it.x, it.y)) < 4f } }
        val e = alle.count { it is Wuerfel && wp.any { p -> p.abstand(Punkt(it.x, it.y)) < 4f } }
        return Stellen(z, e)
    }

    /** Bereich des Knopfs „Bündeln“ (erscheint ab 10 Einern). */
    fun buendelnKnopf(s: Stellentafel) = floatArrayOf(s.x + TAFEL_Z_BREITE + 20f, s.y + TAFEL_HOEHE - 92f, s.x + TAFEL_Z_BREITE + TAFEL_E_BREITE - 20f, s.y + TAFEL_HOEHE - 28f)

    /** Zehn Einer zu einem Zehner bündeln (wenn in der Zehner-Spalte Platz ist). */
    fun buendeln(s: Stellentafel, alle: List<Teil>, neueId: () -> Long): List<Teil>? {
        val wp = wuerfelPlaetze(s)
        val einer = alle.filter { it is Wuerfel && wp.any { p -> p.abstand(Punkt(it.x, it.y)) < 4f } }
        if (einer.size < 10) return null
        val frei = freierPlatz(stangenPlaetze(s), alle.filterIsInstance<Stange>()) ?: return null
        val weg = einer.takeLast(10).map { it.id }.toSet()
        return alle.filter { it.id !in weg } + Stange(neueId(), frei.x, frei.y)
    }

    /** Einen Zehner in zehn Einer tauschen (wenn genug Plätze frei sind). */
    fun entbuendeln(s: Stellentafel, stange: Stange, alle: List<Teil>, neueId: () -> Long): List<Teil>? {
        val belegt = alle.filterIsInstance<Wuerfel>().map { Punkt(it.x, it.y) }
        val frei = wuerfelPlaetze(s).filter { p -> belegt.none { it.abstand(p) < 4f } }
        if (frei.size < 10) return null
        return alle.filter { it.id != stange.id } + frei.take(10).map { Wuerfel(neueId(), it.x, it.y) }
    }

    fun freierPlatz(plaetze: List<Punkt>, belegt: List<Teil>): Punkt? =
        plaetze.firstOrNull { p -> belegt.none { Punkt(it.x, it.y).abstand(p) < 4f } }

    // ------------------------------------------------ Zahlenhaus

    /** Rechteck der Zelle i (oben links x, y, Breite, Höhe). */
    fun hausZelle(h: Zahlenhaus, i: Int): FloatArray {
        val zeile = i / 2
        val spalte = i % 2
        return floatArrayOf(h.x + spalte * HAUS_B, h.y + DACH + zeile * HAUS_H, HAUS_B, HAUS_H)
    }

    fun hausZelleBei(h: Zahlenhaus, px: Float, py: Float): Int? =
        h.zellen.indices.firstOrNull { i -> innen(hausZelle(h, i), px, py) }

    /** Stimmt jedes Stockwerk? null = noch nicht ausgefüllt. */
    fun hausPruefen(h: Zahlenhaus): List<Boolean?> = (0 until h.stockwerke).map { s ->
        val l = h.zellen[2 * s].wert
        val r = h.zellen[2 * s + 1].wert
        if (l == null || r == null) null else l + r == h.dach
    }

    // ------------------------------------------------ Zahlenmauer

    /** Index des Steins (Reihe r von unten, Position k). */
    fun mauerIndex(stufen: Int, r: Int, k: Int): Int = (0 until r).sumOf { stufen - it } + k

    fun mauerStein(m: Zahlenmauer, i: Int): FloatArray {
        var r = 0
        var rest = i
        while (rest >= m.stufen - r) { rest -= m.stufen - r; r++ }
        val x = m.x + r * STEIN_B / 2 + rest * STEIN_B
        val y = m.y + (m.stufen - 1 - r) * STEIN_H
        return floatArrayOf(x, y, STEIN_B, STEIN_H)
    }

    fun mauerSteinBei(m: Zahlenmauer, px: Float, py: Float): Int? =
        m.zellen.indices.firstOrNull { i -> innen(mauerStein(m, i), px, py) }

    /** Jeder Stein über der untersten Reihe: stimmt er mit der Summe der beiden darunter? */
    fun mauerPruefen(m: Zahlenmauer): List<Boolean?> = m.zellen.indices.map { i ->
        val (r, k) = mauerPosition(m.stufen, i)
        val wert = m.zellen[i].wert ?: return@map null
        if (r == 0) return@map if (m.zellen[i].fest) null else true
        val links = m.zellen[mauerIndex(m.stufen, r - 1, k)].wert
        val rechts = m.zellen[mauerIndex(m.stufen, r - 1, k + 1)].wert
        if (links == null || rechts == null) null else wert == links + rechts
    }

    fun mauerPosition(stufen: Int, i: Int): Pair<Int, Int> {
        var r = 0
        var rest = i
        while (rest >= stufen - r) { rest -= stufen - r; r++ }
        return r to rest
    }

    // ------------------------------------------------ Aufgabe

    /** Bereich des Kästchens □ einer Aufgabe. */
    fun aufgabeLuecke(t: Aufgabe): FloatArray {
        var x = t.x + 20f
        for (teil in t.teile()) {
            if (teil == "□") return floatArrayOf(x, t.y + 10f, 100f, AUFGABE_H - 20f)
            x += teil.length * 30f + 18f
        }
        return floatArrayOf(x, t.y + 10f, 100f, AUFGABE_H - 20f)
    }

    fun innen(r: FloatArray, px: Float, py: Float) = px >= r[0] && py >= r[1] && px <= r[0] + r[2] && py <= r[1] + r[3]

    /** Liegt (px, py) in der Nähe von (x, y)? */
    fun nahe(x: Float, y: Float, px: Float, py: Float, r: Float) = abs(px - x) <= r && abs(py - y) <= r
}
