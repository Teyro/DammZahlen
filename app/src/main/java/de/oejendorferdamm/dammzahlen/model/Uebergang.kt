/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.model

/**
 * Zehner- bzw. Hunderterübergang Schritt für Schritt, wie im Unterricht: erst bis zum vollen
 * Zehner, dann der Rest. Bei 8 + 5: 8 rote Plättchen → 2 blaue füllen den Zehner (8 + 2 = 10) →
 * 3 blaue ins nächste Feld (10 + 3 = 13). Dazu Sprünge am Rechenstrich und die Rechnung auf
 * einer Karte. Minus genauso rückwärts: 13 − 5 = 13 − 3 − 2.
 *
 * Bis 20 im Zwanzigerfeld, darüber im Hunderterfeld.
 */
object Uebergang {
    data class Schritt(val titel: String, val teile: List<Teil>)

    /** Ist die Aufgabe gültig (Zahlen 0–100, Ergebnis 0–100)? */
    fun gueltig(a: Int, b: Int, plus: Boolean): Boolean {
        val e = if (plus) a + b else a - b
        return a in 0..100 && b in 1..100 && e in 0..100
    }

    /** Geht die Rechnung über einen Zehner hinweg? */
    fun mitUebergang(a: Int, b: Int, plus: Boolean): Boolean =
        if (plus) a % 10 != 0 && a % 10 + b > 10 else a % 10 != 0 && b > a % 10

    fun schritte(a: Int, b: Int, plus: Boolean, neueId: () -> Long): List<Schritt> {
        require(gueltig(a, b, plus))
        val s = if (plus) a + b else a - b
        val groesste = maxOf(a, s)
        val art = if (groesste <= 20) FeldArt.ZWANZIGER else FeldArt.HUNDERTER
        val feld = if (art == FeldArt.ZWANZIGER) Feld(neueId(), 620f, 150f, art) else Feld(neueId(), 200f, 30f, art)
        val plaetze = Geometrie.plaetze(feld)
        val start = (minOf(a, s) / 10) * 10
        val ende = maxOf(start + 10, ((groesste + 9) / 10) * 10)
        val strichX = if (art == FeldArt.ZWANZIGER) 270f else 270f
        val strichY = if (art == FeldArt.ZWANZIGER) 380f else 725f
        val karteX = if (art == FeldArt.ZWANZIGER) 620f else 1000f
        val karteY = if (art == FeldArt.ZWANZIGER) 30f else 80f
        val zeichen = if (plus) "+" else "−"
        val strichId = neueId()
        val karteId = neueId()
        val ids = List(maxOf(a, s)) { neueId() }

        /** Plättchen: die ersten [rot] rot, bis [bis] blau; Bereich [weg] als weggenommen. */
        fun plaettchen(rot: Int, bis: Int, weg: IntRange = IntRange.EMPTY): List<Teil> = (0 until bis).map { i ->
            val p = plaetze[i]
            Plaettchen(ids[i], p.x, p.y, rot = i < rot, weg = i in weg)
        }

        fun stand(spruenge: List<Sprung>, text: String, steine: List<Teil>) =
            listOf<Teil>(feld) + steine + Strich(strichId, strichX, strichY, start, ende, spruenge) + Karte(karteId, karteX, karteY, text)

        val ergebnis = ArrayList<Schritt>()
        ergebnis.add(Schritt("$a $zeichen $b – wir legen $a", stand(emptyList(), "$a $zeichen $b = ?", plaettchen(a, a))))

        if (plus) {
            if (mitUebergang(a, b, true)) {
                val zehner = (a / 10 + 1) * 10
                val e = zehner - a
                val r = b - e
                ergebnis.add(Schritt("Erst bis zur $zehner: $a + $e = $zehner", stand(listOf(Sprung(a, zehner)), "$a + $b = $a + $e + …", plaettchen(a, zehner))))
                ergebnis.add(Schritt("Dann den Rest: $zehner + $r = $s", stand(listOf(Sprung(a, zehner), Sprung(zehner, s)), "$a + $b = $a + $e + $r = $s", plaettchen(a, s))))
            } else {
                ergebnis.add(Schritt("$b dazu: $a + $b = $s", stand(listOf(Sprung(a, s)), "$a + $b = $s", plaettchen(a, s))))
            }
        } else {
            if (mitUebergang(a, b, false)) {
                val e = a % 10
                val zehner = a - e
                val r = b - e
                ergebnis.add(Schritt("Erst bis zur $zehner: $a − $e = $zehner", stand(listOf(Sprung(a, zehner)), "$a − $b = $a − $e − …", plaettchen(a, a, zehner until a))))
                ergebnis.add(Schritt("Dann den Rest: $zehner − $r = $s", stand(listOf(Sprung(a, zehner), Sprung(zehner, s)), "$a − $b = $a − $e − $r = $s", plaettchen(a, a, s until a))))
            } else {
                ergebnis.add(Schritt("$b weg: $a − $b = $s", stand(listOf(Sprung(a, s)), "$a − $b = $s", plaettchen(a, a, s until a))))
            }
        }
        return ergebnis
    }
}
